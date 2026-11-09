import {build} from "esbuild";
import {copyFile} from "node:fs/promises";

// Keep the startup indicator independent of the RPC and Compose runtimes.
await build({
    stdin: {
        contents: `
            import "@kyralabs/m3e-elements/loading-indicator.js";
            import "@kyralabs/m3e-css/loading-indicator.css";
        `,
        resolveDir: process.cwd(),
    },
    bundle: true,
    minify: true,
    format: "iife",
    outfile: "dist/loading-indicator.js",
});
await copyFile("node_modules/@kyralabs/m3e-elements/NOTICE", "dist/loading-indicator.NOTICE.txt");
await copyFile("node_modules/@kyralabs/m3e-elements/LICENSE", "dist/loading-indicator.LICENSE.txt");

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
