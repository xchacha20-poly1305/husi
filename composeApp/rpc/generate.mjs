import { readdir, mkdir, rm } from "node:fs/promises";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const directory = dirname(fileURLToPath(import.meta.url));
const schemas = resolve(directory, "../../proto");
const output = join(directory, "generated");

async function protoFiles(directory) {
    const files = [];
    for (const entry of await readdir(directory, { withFileTypes: true })) {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            files.push(...(await protoFiles(path)));
        } else if (entry.isFile() && entry.name.endsWith(".proto")) {
            files.push(path);
        }
    }
    return files.sort();
}

const files = (await protoFiles(schemas)).map((path) =>
    relative(schemas, path).split(sep).join("/"),
);

if (files.length === 0) {
    throw new Error(`No protobuf schemas found in ${schemas}`);
}
await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });

// npm scripts put the pinned protoc-gen-es executable on PATH.
const result = spawnSync(
    "protoc",
    [
        `--proto_path=${schemas}`,
        `--es_out=${output}`,
        "--es_opt=target=ts",
        ...files,
    ],
    { cwd: schemas, stdio: "inherit" },
);
if (result.error) {
    console.error(
        result.error.code === "ENOENT"
            ? "protoc not found in PATH. Install the protobuf compiler before building the dashboard."
            : result.error.message,
    );
}
process.exit(result.status ?? 1);
