import {build} from "esbuild";
import {copyFile} from "node:fs/promises";

await build({
    entryPoints: ["src/index.ts"],
    bundle: true,
    format: "iife",
    globalName: "husiRpc",
    outfile: "dist/rpc.js",
});

await build({
    entryPoints: ["src/sqlite-worker.ts"],
    bundle: true,
    format: "esm",
    outfile: "dist/sqlite-worker.js",
});

await copyFile("node_modules/@sqlite.org/sqlite-wasm/dist/sqlite3.wasm", "dist/sqlite3.wasm");
