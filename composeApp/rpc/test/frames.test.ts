import { test } from "node:test";
import assert from "node:assert/strict";
import { AsyncQueue, FrameDecoder, frame, metadata } from "../src/frames.js";
import { normalizeUrl } from "../src/index.js";
import { fromJson, toJson } from "@bufbuild/protobuf";
import {
    TailscaleSSHClientMessageSchema,
    StatusSchema,
} from "../generated/daemon/started_service_pb.js";

test("gRPC decoder handles fragmented and concatenated data and metadata", () => {
    const data = frame(new Uint8Array([1, 2, 3]));
    const trailer = frame(new TextEncoder().encode("grpc-status: 0\r\n"), 128);
    const all = new Uint8Array(data.length + trailer.length);
    all.set(data);
    all.set(trailer, data.length);
    const decoder = new FrameDecoder();
    assert.deepEqual(decoder.push(all.slice(0, 3)), []);
    assert.deepEqual(decoder.push(all.slice(3, 6)), []);
    const decoded = decoder.push(all.slice(6));
    assert.deepEqual(decoded[0].data, new Uint8Array([1, 2, 3]));
    assert.equal(decoded[1].flags, 128);
    assert.equal(metadata(decoded[1].data).get("grpc-status"), "0");
    decoder.finish();
});

test("reject truncated and oversized frames", () => {
    const truncated = new FrameDecoder();
    truncated.push(frame(new Uint8Array([1])).slice(0, 5));
    assert.throws(() => truncated.finish(), /Truncated/);
    assert.throws(
        () => new FrameDecoder().push(new Uint8Array([0, 127, 255, 255, 255])),
        /exceeds/,
    );
});

test("queue wakes pending readers, drains then reports failure, and limits buffering", async () => {
    const queue = new AsyncQueue<number>(1);
    const iterator = queue[Symbol.asyncIterator]();
    const first = iterator.next();
    queue.push(7);
    assert.equal((await first).value, 7);
    queue.push(8);
    assert.throws(() => queue.push(9), /exceeds/);
    queue.end(new Error("disconnected"));
    assert.equal((await iterator.next()).value, 8);
    await assert.rejects(iterator.next(), /disconnected/);
});

test("normalize URLs while preserving gateway paths", () => {
    assert.equal(
        normalizeUrl("https://example.org/api/"),
        "https://example.org/api",
    );
    assert.throws(() => normalizeUrl("ws://example.org"), /HTTP/);
    assert.throws(() => normalizeUrl("https://u:p@example.org"), /credentials/);
    assert.throws(
        () => normalizeUrl("https://example.org/#secret"),
        /fragment/,
    );
});

test("protobuf JSON preserves 64-bit counters and SSH bytes and oneof", () => {
    const input = { uplinkTotal: "9007199254740993" };
    assert.equal(
        (toJson(StatusSchema, fromJson(StatusSchema, input)) as typeof input)
            .uplinkTotal,
        input.uplinkTotal,
    );
    const ssh = { input: { data: "AAH/" } };
    assert.deepEqual(
        toJson(
            TailscaleSSHClientMessageSchema,
            fromJson(TailscaleSSHClientMessageSchema, ssh),
        ),
        ssh,
    );
});

test("switching servers cancels old RPCs and a late failure cannot disconnect the new target", async () => {
    const { connect, disconnect, unary } = await import("../src/index.js");
    const { VersionSchema } = await import(
        "../generated/daemon/started_service_pb.js"
    );
    const { toBinary } = await import("@bufbuild/protobuf");
    const original = globalThis.fetch;
    let rejectFirst!: (reason: unknown) => void;
    let firstSignal: AbortSignal | null | undefined;
    globalThis.fetch = (async (
        request: RequestInfo | URL,
        init?: RequestInit,
    ) => {
        const url = request instanceof Request ? request.url : String(request);
        if (url.includes("first.example")) {
            firstSignal =
                init?.signal ??
                (request instanceof Request ? request.signal : undefined);
            return await new Promise<Response>((_, reject) => {
                rejectFirst = reject;
            });
        }
        const payload = frame(
            toBinary(
                VersionSchema,
                fromJson(VersionSchema, { version: "test", apiVersion: 1 }),
            ),
        );
        const trailer = frame(
            new TextEncoder().encode("grpc-status: 0\r\n"),
            128,
        );
        const body = new Uint8Array(payload.length + trailer.length);
        body.set(payload);
        body.set(trailer, payload.length);
        return new Response(body, {
            headers: { "content-type": "application/grpc-web+proto" },
        });
    }) as typeof fetch;
    try {
        const first = connect("https://first.example", "old");
        const rejected = assert.rejects(first);
        await new Promise((resolve) => setTimeout(resolve, 0));
        const second = await connect("https://second.example", "new");
        assert.equal(JSON.parse(second).version, "test");
        assert.equal(firstSignal?.aborted, true);
        rejectFirst(new Error("late failure"));
        await rejected;
        assert.equal(
            JSON.parse(await unary("GetVersion", "{}")).version,
            "test",
        );
    } finally {
        disconnect();
        globalThis.fetch = original;
    }
});
