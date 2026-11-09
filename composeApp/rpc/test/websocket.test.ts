import { test } from "node:test";
import assert from "node:assert/strict";
import { fromBinary, fromJson, toBinary } from "@bufbuild/protobuf";
import {
    StartedService,
    TailscaleSSHServerMessageSchema,
} from "../generated/daemon/started_service_pb.js";
import { withWebSocketStreams } from "../src/websocket.js";
import { AsyncQueue, frame } from "../src/frames.js";
import type { Transport } from "@connectrpc/connect";

class Socket {
    static latest: Socket;
    binaryType = "";
    bufferedAmount = 0;
    sent: Uint8Array[] = [];
    closed = false;
    onopen?: () => void;
    onmessage?: (event: { data: ArrayBuffer }) => void;
    onerror?: () => void;
    onclose?: () => void;
    constructor(
        readonly url: URL,
        readonly protocol: string,
    ) {
        Socket.latest = this;
        queueMicrotask(() => this.onopen?.());
    }
    send(bytes: Uint8Array) {
        this.sent.push(bytes);
    }
    close() {
        this.closed = true;
    }
    receive(bytes: Uint8Array) {
        this.onmessage?.({ data: new Uint8Array(bytes).buffer });
    }
}

const base = {
    unary() {
        throw new Error("unexpected unary");
    },
    stream() {
        throw new Error("unexpected HTTP stream");
    },
} as Transport;
const method = StartedService.method.startTailscaleSSHSession;

test("dashboard subscriptions use WebSockets beyond the HTTP/1.1 connection limit and keep receiving after half-close", async () => {
    const original = globalThis.WebSocket;
    globalThis.WebSocket = Socket as unknown as typeof WebSocket;
    const controller = new AbortController();
    try {
        const transport = withWebSocketStreams(
            base,
            "http://example.org/api",
            "Bearer secret",
        );
        const methods = [
            StartedService.method.subscribeGroups,
            StartedService.method.subscribeLog,
            StartedService.method.subscribeTailscaleStatus,
            StartedService.method.subscribeOpenConnectStatus,
            StartedService.method.subscribeOpenVPNStatus,
            StartedService.method.subscribeClashMode,
            StartedService.method.subscribeServiceStatus,
            StartedService.method.subscribeStatus,
            StartedService.method.subscribeConnections,
        ];
        const subscriptions = [];
        for (const method of methods) {
            const input = fromJson(
                method.input,
                method.input.fields.some(field => field.name === "interval")
                    ? { interval: "1000000000" }
                    : {},
            );
            const response = await transport.stream(
                method,
                controller.signal,
                undefined,
                undefined,
                (async function* () {
                    yield input;
                })(),
            );
            subscriptions.push({ method, input, response, socket: Socket.latest });
        }
        await new Promise((resolve) => setTimeout(resolve, 0));
        for (const { method, input, response, socket } of subscriptions) {
            assert.equal(
                socket.url.href,
                `ws://example.org/api/daemon.StartedService/${method.name}`,
            );
            assert.match(
                new TextDecoder().decode(socket.sent[0]),
                /authorization: Bearer secret/,
            );
            assert.equal(socket.sent.length, 3);
            assert.equal(socket.sent[1][0], 0);
            assert.deepEqual(fromBinary(method.input, socket.sent[1].slice(6)), input);
            assert.deepEqual(socket.sent[2], new Uint8Array([1]));
            assert.equal(socket.closed, false);

            const messages = response.message[Symbol.asyncIterator]();
            for (const memory of [1048576, 2097152]) {
                const output = fromJson(
                    method.output,
                    method.name === "SubscribeStatus"
                        ? { memory: String(memory), goroutines: 42, uplinkTotal: "9007199254740993" }
                        : {},
                );
                socket.receive(frame(toBinary(method.output, output)));
                assert.deepEqual((await messages.next()).value, output);
            }
            socket.receive(
                frame(new TextEncoder().encode("grpc-status: 0\r\n"), 128),
            );
            assert.equal((await messages.next()).done, true);
            assert.equal(socket.closed, true);
        }
    } finally {
        controller.abort();
        globalThis.WebSocket = original;
    }
});

test("WebSocket transport sends metadata, framed input and half-close, and decodes output and status", async () => {
    const original = globalThis.WebSocket;
    globalThis.WebSocket = Socket as unknown as typeof WebSocket;
    try {
        const transport = withWebSocketStreams(
            base,
            "https://example.org/api/",
            "Bearer secret",
        );
        const response = await transport.stream(
            method,
            undefined,
            undefined,
            undefined,
            (async function* () {
                yield { start: { endpointTag: "ts", username: "root" } };
            })(),
        );
        const socket = Socket.latest;
        await new Promise((resolve) => setTimeout(resolve, 0));
        assert.equal(
            socket.url.href,
            "wss://example.org/api/daemon.StartedService/StartTailscaleSSHSession",
        );
        assert.equal(socket.protocol, "grpc-websockets");
        assert.match(
            new TextDecoder().decode(socket.sent[0]),
            /authorization: Bearer secret/,
        );
        assert.equal(socket.sent[1][0], 0);
        assert.equal(socket.sent[1][1], 0);
        assert.deepEqual(socket.sent[2], new Uint8Array([1]));
        const output = frame(
            toBinary(
                TailscaleSSHServerMessageSchema,
                fromJson(TailscaleSSHServerMessageSchema, {
                    output: { data: "AAH/" },
                }),
            ),
        );
        socket.receive(
            frame(
                new TextEncoder().encode("content-type: application/grpc\r\n"),
                128,
            ),
        );
        socket.receive(output.slice(0, 2));
        socket.receive(output.slice(2));
        socket.receive(
            frame(new TextEncoder().encode("grpc-status: 0\r\n"), 128),
        );
        const messages = [];
        for await (const message of response.message) messages.push(message);
        assert.equal(messages.length, 1);
        assert.deepEqual(messages[0].message, {
            case: "output",
            value: {
                $typeName: "daemon.TailscaleSSHOutput",
                data: new Uint8Array([0, 1, 255]),
            },
        });
        assert.equal(response.trailer.get("grpc-status"), "0");
        assert.equal(socket.closed, true);
    } finally {
        globalThis.WebSocket = original;
    }
});

test("WebSocket transport propagates authentication status and cancellation and rejects missing trailers", async () => {
    const original = globalThis.WebSocket;
    globalThis.WebSocket = Socket as unknown as typeof WebSocket;
    try {
        const transport = withWebSocketStreams(base, "https://example.org", "");
        const input = new AsyncQueue<any>();
        const response = await transport.stream(
            method,
            undefined,
            undefined,
            undefined,
            input,
        );
        Socket.latest.receive(
            frame(
                new TextEncoder().encode(
                    "grpc-status: 16\r\ngrpc-message: invalid%20authorization\r\n",
                ),
                128,
            ),
        );
        await assert.rejects(async () => {
            for await (const _ of response.message) {
            }
        }, /invalid authorization/);
        input.end();
        const controller = new AbortController();
        const response2 = await transport.stream(
            method,
            controller.signal,
            undefined,
            undefined,
            (async function* () {})(),
        );
        controller.abort();
        await assert.rejects(async () => {
            for await (const _ of response2.message) {
            }
        }, /canceled/);
        assert.equal(Socket.latest.closed, true);
        const response3 = await transport.stream(
            method,
            undefined,
            undefined,
            undefined,
            (async function* () {})(),
        );
        Socket.latest.onclose?.();
        await assert.rejects(async () => {
            for await (const _ of response3.message) {
            }
        }, /without status/);
    } finally {
        globalThis.WebSocket = original;
    }
});
