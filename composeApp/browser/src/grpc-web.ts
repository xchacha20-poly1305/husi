import {FrameDecoder, GrpcWebError, frame, metadata} from "./grpc-web-frames";

const CONTENT_TYPE = "application/grpc-web+proto";
const WS_BUFFER_LIMIT = 1024 * 1024;
const QUEUE_BYTES_LIMIT = 16 * 1024 * 1024;
const QUEUE_MESSAGES_LIMIT = 256;
const TRAILER_FRAME_FLAG = 0x80;
const MAX_MILLIS_TIMEOUT = 99_999_999;

const GrpcStatus = {
    CANCELLED: 1,
    UNKNOWN: 2,
    DEADLINE_EXCEEDED: 4,
    PERMISSION_DENIED: 7,
    RESOURCE_EXHAUSTED: 8,
    UNIMPLEMENTED: 12,
    INTERNAL: 13,
    UNAVAILABLE: 14,
    UNAUTHENTICATED: 16,
} as const;

export function openGrpcWeb(
    url: string,
    path: string,
    requestMetadata: string,
    body: Uint8Array | null,
    timeoutMs: number,
): GrpcWebExchange {
    return new GrpcWebExchange(
        url + path,
        new Headers(JSON.parse(requestMetadata)),
        body,
        timeoutMs,
    );
}

class GrpcWebExchange {
    private readonly decoder = new FrameDecoder();
    private readonly controller = new AbortController();
    private readonly queue: Uint8Array[] = [];
    private queuedBytes = 0;
    private reader?: ReadableStreamDefaultReader<Uint8Array>;
    private fetchReady?: Promise<void>;
    private socket?: WebSocket;
    private socketReady?: Promise<void>;
    private wake?: () => void;
    private timer?: number;
    private error?: GrpcWebError;
    private ended = false;
    private receivedHeaders?: Headers;
    private receivedStatus?: GrpcWebError;

    get status(): number {
        return this.error?.code ?? 0;
    }

    get statusMessage(): string {
        return this.error?.message ?? "";
    }

    get responseMetadata(): string | null {
        return this.receivedHeaders
            ? JSON.stringify(Object.fromEntries(this.receivedHeaders))
            : null;
    }

    constructor(url: string, headers: Headers, body: Uint8Array | null, timeoutMs: number) {
        headers.set("Content-Type", CONTENT_TYPE);
        headers.set("X-Grpc-Web", "1");
        if (timeoutMs > 0) {
            const timeoutHeader = timeoutMs <= MAX_MILLIS_TIMEOUT
                ? `${timeoutMs}m`
                : `${Math.ceil(timeoutMs / 1000)}S`;
            headers.set("Grpc-Timeout", timeoutHeader);
            this.timer = window.setTimeout(
                () => this.fail(new GrpcWebError(GrpcStatus.DEADLINE_EXCEEDED, "RPC deadline exceeded")),
                timeoutMs,
            );
        }
        if (body !== null) {
            this.fetchReady = this.openFetch(url, headers, body).catch(error => this.fail(error));
        } else {
            try {
                this.openSocket(url, headers);
            } catch (error) {
                this.fail(error);
            }
        }
    }

    private async openFetch(url: string, headers: Headers, body: Uint8Array): Promise<void> {
        const response = await fetch(url, {
            method: "POST",
            headers,
            body: frame(body),
            signal: this.controller.signal,
            redirect: "error",
        });
        this.receivedHeaders = response.headers;
        this.readStatus(response.headers);
        if (!response.ok && !this.receivedStatus) {
            throw new GrpcWebError(httpCode(response.status), `RPC HTTP status ${response.status}`);
        }
        if (this.receivedStatus) {
            this.end();
            return;
        }
        if (!response.headers.get("content-type")?.startsWith("application/grpc-web")) {
            throw new GrpcWebError(GrpcStatus.INTERNAL, "Unexpected gRPC-Web response content type");
        }
        this.reader = response.body?.getReader();
        if (!this.reader) {
            this.end();
        }
    }

    private openSocket(url: string, headers: Headers) {
        const address = new URL(url);
        address.protocol = address.protocol === "https:" ? "wss:" : "ws:";
        const socket = new WebSocket(address, "grpc-websockets");
        this.socket = socket;
        socket.binaryType = "arraybuffer";
        this.socketReady = new Promise(resolve => {
            socket.onopen = () => {
                try {
                    let text = "";
                    for (const [name, value] of headers) {
                        text += `${name}: ${value}\r\n`;
                    }
                    socket.send(new TextEncoder().encode(text));
                } catch (error) {
                    this.fail(error);
                } finally {
                    resolve();
                }
            };
            this.controller.signal.addEventListener("abort", () => resolve(), {once: true});
        });
        socket.onmessage = event => {
            try {
                this.onChunk(new Uint8Array(event.data));
            } catch (error) {
                this.fail(error);
            }
        };
        socket.onerror = () => {
            this.fail(new GrpcWebError(GrpcStatus.UNAVAILABLE, "RPC WebSocket connection failed"));
        };
        socket.onclose = () => this.end();
    }

    async send(bytes: Uint8Array): Promise<void> {
        const encoded = frame(bytes);
        const packet = new Uint8Array(encoded.length + 1);
        packet.set(encoded, 1);
        await this.sendPacket(packet);
    }

    async finish(): Promise<void> {
        await this.sendPacket(new Uint8Array([1]));
    }

    private async sendPacket(packet: Uint8Array<ArrayBuffer>) {
        await this.socketReady;
        const socket = this.socket;
        while (!this.ended && socket && socket.bufferedAmount > WS_BUFFER_LIMIT) {
            await new Promise(resolve => window.setTimeout(resolve, 10));
        }
        if (this.ended) {
            if (this.error) {
                throw this.error;
            }
            return;
        }
        if (!socket) {
            throw new GrpcWebError(GrpcStatus.INTERNAL, "RPC WebSocket is missing");
        }
        socket.send(packet);
    }

    async receive(): Promise<Uint8Array | null> {
        await this.fetchReady;
        while (!this.queue.length && !this.ended) {
            if (this.reader) {
                try {
                    const result = await this.reader.read();
                    if (result.done) {
                        this.end();
                    } else {
                        this.onChunk(result.value);
                    }
                } catch (error) {
                    this.fail(error);
                }
            } else {
                await new Promise<void>(resolve => {
                    this.wake = resolve;
                });
                this.wake = undefined;
            }
        }
        const bytes = this.queue.shift();
        if (bytes) {
            this.queuedBytes -= bytes.byteLength;
            return bytes;
        }
        if (this.error) {
            throw this.error;
        }
        return null;
    }

    private onChunk(chunk: Uint8Array) {
        if (this.ended) return;
        for (const {flags, data} of this.decoder.push(chunk)) {
            if (flags === TRAILER_FRAME_FLAG) {
                const headers = metadata(data);
                this.receivedHeaders ??= headers;
                this.readStatus(headers);
            } else if (flags === 0 && !this.receivedStatus) {
                this.queuedBytes += data.byteLength;
                const exceedsQueueLimit =
                    this.queue.length >= QUEUE_MESSAGES_LIMIT || this.queuedBytes > QUEUE_BYTES_LIMIT;
                if (exceedsQueueLimit) {
                    throw new GrpcWebError(GrpcStatus.RESOURCE_EXHAUSTED, "RPC receive queue exceeds limit");
                }
                this.queue.push(data);
            } else {
                throw new GrpcWebError(GrpcStatus.INTERNAL, "Unexpected gRPC-Web frame");
            }
        }
        this.wake?.();
    }

    private readStatus(headers: Headers) {
        const status = headers.get("grpc-status");
        if (status === null) return;
        const code = Number(status);
        const isDigits = status.length > 0 && [...status].every(digit => digit >= "0" && digit <= "9");
        if (!isDigits || code > GrpcStatus.UNAUTHENTICATED) {
            throw new GrpcWebError(GrpcStatus.UNKNOWN, "Invalid grpc-status");
        }
        const message = headers.get("grpc-message") ?? "";
        this.receivedStatus = new GrpcWebError(code, decodeStatusMessage(message));
    }

    private end() {
        if (this.ended) return;
        try {
            this.decoder.finish();
            const status = this.receivedStatus;
            if (!status) {
                throw new GrpcWebError(GrpcStatus.UNKNOWN, "RPC closed without grpc-status");
            }
            if (status.code !== 0) {
                this.error = status;
            }
            this.dispose();
        } catch (error) {
            this.fail(error);
        }
    }

    private fail(error: unknown) {
        if (this.ended) return;
        this.error = error instanceof GrpcWebError
            ? error
            : new GrpcWebError(GrpcStatus.UNAVAILABLE, String(error));
        this.dispose();
    }

    close() {
        this.fail(new GrpcWebError(GrpcStatus.CANCELLED, "RPC canceled"));
        this.queue.length = 0;
        this.queuedBytes = 0;
    }

    private dispose() {
        this.ended = true;
        window.clearTimeout(this.timer);
        this.controller.abort();
        this.socket?.close();
        this.wake?.();
    }
}

function decodeStatusMessage(message: string): string {
    try {
        return decodeURIComponent(message);
    } catch {
        return message;
    }
}

function httpCode(status: number): number {
    switch (status) {
        case 401:
            return GrpcStatus.UNAUTHENTICATED;
        case 403:
            return GrpcStatus.PERMISSION_DENIED;
        case 404:
            return GrpcStatus.UNIMPLEMENTED;
        case 429:
        case 502:
        case 503:
        case 504:
            return GrpcStatus.UNAVAILABLE;
        default:
            return GrpcStatus.UNKNOWN;
    }
}
