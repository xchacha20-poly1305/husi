const MAX_FRAME = 16 * 1024 * 1024;
const FRAME_HEADER_SIZE = 5;
const GRPC_RESOURCE_EXHAUSTED = 8;
const GRPC_DATA_LOSS = 15;

export type GrpcWebFrame = {
    flags: number;
    data: Uint8Array;
};

export class GrpcWebError extends Error {
    constructor(readonly code: number, message: string) {
        super(message);
    }
}

export function frame(data: Uint8Array, flags = 0): Uint8Array<ArrayBuffer> {
    if (data.length > MAX_FRAME) {
        throw new GrpcWebError(GRPC_RESOURCE_EXHAUSTED, "RPC frame exceeds limit");
    }
    const bytes = new Uint8Array(FRAME_HEADER_SIZE + data.length);
    bytes[0] = flags;
    new DataView(bytes.buffer).setUint32(1, data.length);
    bytes.set(data, FRAME_HEADER_SIZE);
    return bytes;
}

/** A WebSocket message may contain partial or multiple gRPC frames. */
export class FrameDecoder {
    private pending = new Uint8Array(0);

    push(bytes: Uint8Array): GrpcWebFrame[] {
        const combined = new Uint8Array(this.pending.length + bytes.length);
        combined.set(this.pending);
        combined.set(bytes, this.pending.length);
        const frames: GrpcWebFrame[] = [];
        let offset = 0;
        while (offset + FRAME_HEADER_SIZE <= combined.length) {
            const length = new DataView(
                combined.buffer,
                combined.byteOffset + offset + 1,
                4,
            ).getUint32(0);
            if (length > MAX_FRAME) {
                throw new GrpcWebError(GRPC_RESOURCE_EXHAUSTED, "RPC frame exceeds limit");
            }
            const frameEnd = offset + FRAME_HEADER_SIZE + length;
            if (frameEnd > combined.length) {
                break;
            }
            frames.push({
                flags: combined[offset],
                data: combined.slice(offset + FRAME_HEADER_SIZE, frameEnd),
            });
            offset = frameEnd;
        }
        this.pending = combined.slice(offset);
        return frames;
    }

    finish() {
        if (this.pending.length) {
            throw new GrpcWebError(GRPC_DATA_LOSS, "Truncated RPC frame");
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
