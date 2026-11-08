import { create, fromBinary, toBinary } from "@bufbuild/protobuf";
import { Code, ConnectError, type Transport } from "@connectrpc/connect";
import { AsyncQueue, FrameDecoder, frame, metadata } from "./frames.js";

export function withWebSocketStreams(
    base: Transport,
    baseUrl: string,
    authorization: string,
): Transport {
    return {
        unary: base.unary,
        async stream(method, signal, timeoutMs, header, input, contextValues) {
            if (method.methodKind === "server_streaming") {
                return base.stream(
                    method,
                    signal,
                    timeoutMs,
                    header,
                    input,
                    contextValues,
                );
            }
            const url = new URL(
                `${baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl}/${method.parent.typeName}/${method.name}`,
            );
            url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
            const socket = new WebSocket(url, "grpc-websockets");
            socket.binaryType = "arraybuffer";
            const queue = new AsyncQueue<Uint8Array>();
            const decoder = new FrameDecoder();
            const responseHeader = new Headers();
            const trailer = new Headers();
            let hasStatus = false;
            let disposed = false;
            let resolveOpened!: () => void;
            let rejectOpened!: (reason: unknown) => void;
            const opened = new Promise<void>((resolve, reject) => {
                resolveOpened = resolve;
                rejectOpened = reject;
            });
            const cleanup = (error?: unknown) => {
                if (disposed) return;
                disposed = true;
                clearTimeout(timer);
                signal?.removeEventListener("abort", abort);
                rejectOpened(
                    error ?? new ConnectError("RPC closed", Code.Canceled),
                );
                queue.end(error);
                socket.close();
            };
            const abort = () =>
                cleanup(new ConnectError("RPC canceled", Code.Canceled));
            const timer = timeoutMs
                ? setTimeout(
                      () =>
                          cleanup(
                              new ConnectError(
                                  "RPC deadline exceeded",
                                  Code.DeadlineExceeded,
                              ),
                          ),
                      timeoutMs,
                  )
                : undefined;
            signal?.addEventListener("abort", abort, { once: true });
            socket.onopen = () => {
                if (signal?.aborted) {
                    abort();
                    return;
                }
                const headers = new Headers(header);
                headers.set("content-type", "application/grpc-web+proto");
                if (authorization) headers.set("authorization", authorization);
                if (timeoutMs) headers.set("grpc-timeout", `${timeoutMs}m`);
                let text = "";
                headers.forEach((value, key) => {
                    text += `${key}: ${value}\r\n`;
                });
                socket.send(new TextEncoder().encode(text));
                resolveOpened();
            };
            socket.onmessage = (event) => {
                try {
                    if (!(event.data instanceof ArrayBuffer)) {
                        throw new ConnectError(
                            "Non-binary RPC frame",
                            Code.DataLoss,
                        );
                    }
                    for (const item of decoder.push(
                        new Uint8Array(event.data),
                    )) {
                        if (item.flags === 128) {
                            const headers = metadata(item.data);
                            if (headers.has("grpc-status")) {
                                hasStatus = true;
                                headers.forEach((value, key) =>
                                    trailer.append(key, value),
                                );
                                const status = Number(
                                    headers.get("grpc-status"),
                                );
                                let message =
                                    headers.get("grpc-message") ?? "RPC failed";
                                try {
                                    message = decodeURIComponent(message);
                                } catch {
                                    /* Preserve malformed server message. */
                                }
                                cleanup(
                                    status === 0
                                        ? undefined
                                        : new ConnectError(message, status),
                                );
                            } else {
                                headers.forEach((value, key) =>
                                    responseHeader.append(key, value),
                                );
                            }
                        } else if (item.flags === 0) {
                            queue.push(item.data);
                        } else {
                            throw new ConnectError(
                                "Unsupported RPC frame flags",
                                Code.Unimplemented,
                            );
                        }
                    }
                } catch (error) {
                    cleanup(error);
                }
            };
            socket.onerror = () =>
                cleanup(
                    new ConnectError(
                        "WebSocket connection failed",
                        Code.Unavailable,
                    ),
                );
            socket.onclose = () => {
                if (disposed) return;
                try {
                    decoder.finish();
                } catch (error) {
                    cleanup(error);
                    return;
                }
                cleanup(
                    hasStatus
                        ? undefined
                        : new ConnectError(
                              "RPC closed without status",
                              Code.Unavailable,
                          ),
                );
            };
            if (signal?.aborted) abort();
            try {
                await opened;
            } catch (error) {
                cleanup(error);
                throw error;
            }
            void (async () => {
                try {
                    for await (const message of input) {
                        while (
                            socket.bufferedAmount > 1024 * 1024 &&
                            !disposed
                        ) {
                            await new Promise((resolve) =>
                                setTimeout(resolve, 10),
                            );
                        }
                        if (disposed) return;
                        const encoded = frame(
                            toBinary(
                                method.input,
                                create(method.input, message),
                            ),
                        );
                        const packet = new Uint8Array(encoded.length + 1);
                        packet.set(encoded, 1);
                        socket.send(packet);
                    }
                    if (!disposed) {
                        socket.send(new Uint8Array([1]));
                    }
                } catch (error) {
                    cleanup(error);
                }
            })();
            return {
                stream: true,
                service: method.parent,
                method,
                header: responseHeader,
                trailer,
                message: (async function* () {
                    try {
                        for await (const bytes of queue) {
                            yield fromBinary(method.output, bytes);
                        }
                    } finally {
                        cleanup();
                    }
                })(),
            };
        },
    };
}
