import {build} from "esbuild";
import {copyFile, rm} from "node:fs/promises";

await rm("dist", {recursive: true, force: true});

// Keep the startup indicator independent of the Compose runtime.
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
    globalName: "husiBrowser",
    outfile: "dist/browser.js",
});
