import {
    type DescMethod,
    type DescMethodStreaming,
    type DescMethodUnary,
    fromJson,
    type Message,
    toJson,
} from "@bufbuild/protobuf";
import {Code, ConnectError, type Transport} from "@connectrpc/connect";
import {createGrpcWebTransport} from "@connectrpc/connect-web";
import {StartedService} from "../generated/daemon/started_service_pb.js";
import {AsyncQueue} from "./frames.js";
import {withWebSocketStreams} from "./websocket.js";

let transport: Transport | undefined;
let generation = 0;
let nextId = 0;
const calls = new Map<number, AbortController>();
const inputs = new Map<number, AsyncQueue<Message>>();
const uploads = new Set<number>();
const jsonOptions = {alwaysEmitImplicit: true};

export function normalizeUrl(value: string): string {
    const url = new URL(value);
    if (url.protocol !== "http:" && url.protocol !== "https:")
        throw new Error("Use an HTTP or HTTPS API URL");
    if (url.username || url.password || url.search || url.hash) {
        throw new Error(
            "API URL must not contain credentials, a query or a fragment",
        );
    }
    return url.href.endsWith("/") ? url.href.slice(0, -1) : url.href;
}

export function disconnect() {
    generation++;
    for (const controller of calls.values()) {
        controller.abort();
    }
    calls.clear();
    for (const queue of inputs.values()) {
        queue.end();
    }
    inputs.clear();
    uploads.clear();
    transport = undefined;
}

export async function connect(url: string, secret: string): Promise<string> {
    disconnect();
    const currentGeneration = generation;
    const baseUrl = normalizeUrl(url);
    const authorization = secret ? `Bearer ${secret}` : "";
    transport = withWebSocketStreams(
        createGrpcWebTransport({
            baseUrl,
            useBinaryFormat: true,
            interceptors: [
                (next) => async (request) => {
                    if (authorization) {
                        request.header.set("authorization", authorization);
                    }
                    return next(request);
                },
            ],
        }),
        baseUrl,
        authorization,
    );
    try {
        return await unary("GetVersion", "{}");
    } catch (error) {
        if (generation === currentGeneration) {
            disconnect();
        }
        throw error;
    }
}

export async function testConnection(
    url: string,
    secret: string,
): Promise<string> {
    const client = createGrpcWebTransport({
        baseUrl: normalizeUrl(url),
        useBinaryFormat: true,
    });
    const method = methodOf("GetVersion");
    const response = await client.unary(
        method as DescMethodUnary,
        undefined,
        10000,
        secret ? {authorization: `Bearer ${secret}`} : undefined,
        fromJson(method.input, {}),
    );
    return JSON.stringify(toJson(method.output, response.message));
}

function methodOf(name: string): DescMethod {
    const method = StartedService.methods.find(
        (method) => method.name === name,
    );
    if (!method) {
        throw new Error(`Unknown remote method: ${name}`);
    }
    return method;
}

function requireTransport(): Transport {
    if (!transport) {
        throw new ConnectError("Connect to a server first", Code.Unavailable);
    }
    return transport;
}

export async function unary(name: string, json: string): Promise<string> {
    const client = requireTransport();
    const method = methodOf(name);
    if (method.methodKind !== "unary") {
        throw new Error("Expected unary RPC");
    }
    const controller = new AbortController();
    const id = ++nextId;
    calls.set(id, controller);
    try {
        const response = await client.unary(
            method as DescMethodUnary,
            controller.signal,
            30000,
            undefined,
            fromJson(method.input, JSON.parse(json)),
        );
        return JSON.stringify(
            toJson(method.output, response.message, jsonOptions),
        );
    } finally {
        calls.delete(id);
    }
}

export function subscribe(
    name: string,
    json: string,
    onData: (json: string) => void,
    onEnd: (error: string) => void,
): number {
    const client = requireTransport();
    const method = methodOf(name);
    if (method.methodKind === "unary") {
        throw new Error("Expected streaming RPC");
    }
    const controller = new AbortController();
    const id = ++nextId;
    const currentGeneration = generation;
    calls.set(id, controller);
    const queue = new AsyncQueue<Message>(1024);
    queue.push(fromJson(method.input, JSON.parse(json)));
    if (method.methodKind === "server_streaming") {
        queue.end();
    } else {
        inputs.set(id, queue);
    }
    void (async () => {
        try {
            const response = await client.stream(
                method as DescMethodStreaming,
                controller.signal,
                undefined,
                undefined,
                queue,
            );
            for await (const message of response.message) {
                if (
                    generation !== currentGeneration ||
                    controller.signal.aborted
                ) {
                    return;
                }
                onData(
                    JSON.stringify(toJson(method.output, message, jsonOptions)),
                );
            }
            if (generation === currentGeneration && !controller.signal.aborted) {
                onEnd("");
            }
        } catch (error) {
            if (generation === currentGeneration && !controller.signal.aborted) {
                onEnd(ConnectError.from(error).message);
            }
        } finally {
            calls.delete(id);
            inputs.delete(id);
            queue.end();
        }
    })();
    return id;
}

export function send(id: number, name: string, json: string) {
    const input = inputs.get(id);
    if (!input) {
        throw new Error("Remote stream has closed");
    }
    input.push(fromJson(methodOf(name).input, JSON.parse(json)));
}

export function halfClose(id: number) {
    inputs.get(id)?.end();
}

export function cancel(id: number) {
    calls.get(id)?.abort();
    calls.delete(id);
    inputs.get(id)?.end();
    inputs.delete(id);
}

export function saveFile(name: string, base64: string) {
    const bytes = Uint8Array.from(atob(base64), (value) => value.charCodeAt(0));
    const url = URL.createObjectURL(new Blob([bytes]));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = name;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function upload(
    endpointTag: string,
    peerStableID: string,
    onData: (json: string) => void,
    onEnd: (error: string) => void,
): void {
    const currentGeneration = generation;
    const picker = document.createElement("input");
    picker.type = "file";
    picker.multiple = true;
    picker.onchange = () => {
        if (currentGeneration !== generation) return;
        const files = Array.from(picker.files ?? []);
        if (!files.length) return;
        const id = subscribe(
            "SendTaildropFiles",
            JSON.stringify({
                start: {
                    endpointTag,
                    peerStableID,
                    files: files.map((file) => ({
                        name: file.name,
                        size: String(file.size),
                    })),
                },
            }),
            onData,
            (error) => {
                uploads.delete(id);
                onEnd(error);
            },
        );
        uploads.add(id);
        onData(JSON.stringify({
            uploadId: String(id),
            files: files.map(file => ({name: file.name, size: String(file.size)})),
        }));
        void (async () => {
            try {
                for (const file of files) {
                    for (let offset = 0; offset < file.size; offset += 65536) {
                        while (
                            (inputs.get(id) as
                                | AsyncQueue<Message>
                                | undefined) &&
                            pendingUpload(id)
                            ) {
                            await new Promise((resolve) =>
                                setTimeout(resolve, 10),
                            );
                        }
                        if (!inputs.has(id)) {
                            throw new Error("Upload canceled");
                        }
                        const bytes = new Uint8Array(
                            await file
                                .slice(offset, offset + 65536)
                                .arrayBuffer(),
                        );
                        let binary = "";
                        for (const byte of bytes) {
                            binary += String.fromCharCode(byte);
                        }
                        send(
                            id,
                            "SendTaildropFiles",
                            JSON.stringify({chunk: {data: btoa(binary)}}),
                        );
                    }
                    send(id, "SendTaildropFiles", '{"fileDone":{}}');
                }
                halfClose(id);
            } catch (error) {
                uploads.delete(id);
                cancel(id);
                if (generation === currentGeneration) {
                    onEnd(String(error));
                }
            }
        })();
    };
    picker.oncancel = () => onEnd("");
    picker.click();
}

export function cancelUpload(id: number) {
    uploads.delete(id);
    cancel(id);
}

export function cancelUploads() {
    for (const id of uploads) {
        cancel(id);
    }
    uploads.clear();
}

function pendingUpload(id: number): boolean {
    return (inputs.get(id)?.size ?? 0) > 8;
}

export async function download(
    endpointTag: string,
    name: string,
): Promise<void> {
    const client = requireTransport();
    const method = methodOf("DownloadTaildropFile");
    if (method.methodKind === "unary") {
        throw new Error("Expected streaming RPC");
    }
    const controller = new AbortController();
    const id = ++nextId;
    calls.set(id, controller);
    try {
        const response = await client.stream(
            method as DescMethodStreaming,
            controller.signal,
            undefined,
            undefined,
            (async function* () {
                yield fromJson(method.input, {endpointTag, name});
            })(),
        );
        const parts: Uint8Array<ArrayBuffer>[] = [];
        let size = 0;
        let expected = 0;
        for await (const message of response.message) {
            const chunk = message as Message & {
                size: bigint;
                data: Uint8Array;
            };
            if (chunk.size) {
                expected = Number(chunk.size);
            }
            size += chunk.data.length;
            if (size > 512 * 1024 * 1024) {
                throw new Error("Browser download exceeds 512 MiB");
            }
            parts.push(new Uint8Array(chunk.data));
        }
        if (size !== expected) {
            throw new Error("Incomplete file download");
        }
        const url = URL.createObjectURL(new Blob(parts));
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = name;
        anchor.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    } finally {
        calls.delete(id);
    }
}

export function openLink(url: string) {
    const parsed = new URL(url);
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
        throw new Error("Unsupported link");
    }
    window.open(parsed.href, "_blank", "noopener,noreferrer");
}

if (typeof window !== "undefined") {
    window.addEventListener("pagehide", disconnect);
}

export async function qrPng(value: string): Promise<string> {
    const {toDataURL} = await import("qrcode");
    return toDataURL(value, {width: 512, margin: 2});
}
