import {Code, ConnectError} from "@connectrpc/connect";

const MAX_FRAME = 16 * 1024 * 1024;

export function frame(data: Uint8Array, flags = 0): Uint8Array {
    const bytes = new Uint8Array(5 + data.length);
    bytes[0] = flags;
    new DataView(bytes.buffer).setUint32(1, data.length);
    bytes.set(data, 5);
    return bytes;
}

/** A WebSocket message may contain partial or multiple gRPC frames. */
export class FrameDecoder {
    private pending = new Uint8Array(0);

    push(bytes: Uint8Array): { flags: number; data: Uint8Array }[] {
        const combined = new Uint8Array(this.pending.length + bytes.length);
        combined.set(this.pending);
        combined.set(bytes, this.pending.length);
        const frames = [];
        let offset = 0;
        while (offset + 5 <= combined.length) {
            const length = new DataView(
                combined.buffer,
                combined.byteOffset + offset + 1,
                4,
            ).getUint32(0);
            if (length > MAX_FRAME) {
                throw new ConnectError("RPC frame exceeds limit", Code.ResourceExhausted);
            }
            if (offset + 5 + length > combined.length) {
                break;
            }
            frames.push({
                flags: combined[offset],
                data: combined.slice(offset + 5, offset + 5 + length),
            });
            offset += 5 + length;
        }
        this.pending = combined.slice(offset);
        return frames;
    }

    finish() {
        if (this.pending.length) {
            throw new ConnectError("Truncated RPC frame", Code.DataLoss);
        }
    }
}

export function metadata(bytes: Uint8Array): Headers {
    const headers = new Headers();
    for (const line of new TextDecoder().decode(bytes).split("\r\n")) {
        const colon = line.indexOf(":");
        if (colon > 0) {
            headers.append(
                line.slice(0, colon).trim(),
                line.slice(colon + 1).trim(),
            );
        }
    }
    return headers;
}

export class AsyncQueue<T> implements AsyncIterable<T> {
    private values: T[] = [];
    private wake?: () => void;
    private ended = false;
    private error?: unknown;

    constructor(private readonly limit = 256) {
    }

    get size() {
        return this.values.length;
    }

    push(value: T) {
        if (this.ended) return;
        if (this.values.length >= this.limit)
            throw new ConnectError(
                "RPC receive queue exceeds limit",
                Code.ResourceExhausted,
            );
        this.values.push(value);
        this.wake?.();
    }

    end(error?: unknown) {
        this.ended = true;
        this.error = error;
        this.wake?.();
    }

    async* [Symbol.asyncIterator]() {
        for (; ;) {
            if (this.values.length) {
                yield this.values.shift()!;
                continue;
            }
            if (this.ended) {
                if (this.error) {
                    throw this.error;
                }
                return;
            }
            await new Promise<void>((resolve) => {
                this.wake = resolve;
            });
            this.wake = undefined;
        }
    }
}
