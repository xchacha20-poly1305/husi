import {mkdir, readFile, rm, writeFile} from "node:fs/promises";
import {join, resolve} from "node:path";
import parseSpdx from "spdx-expression-parse";

const output = resolve(process.argv[2]);
const packages = JSON.parse(await readFile("generated/npm-licenses.json", "utf8"));

function licenseIds(expression) {
    if (expression.license) return [expression.license];
    return [...licenseIds(expression.left), ...licenseIds(expression.right)];
}

await rm(output, {recursive: true, force: true});
await mkdir(join(output, "libraries"), {recursive: true});

for (const [packageId, packageInfo] of Object.entries(packages).sort()) {
    const separator = packageId.lastIndexOf("@");
    const name = packageId.slice(0, separator);
    const version = packageId.slice(separator + 1);
    const id = `npm_${encodeURIComponent(name)}_${version}`;
    const licenses = [...new Set(licenseIds(parseSpdx(packageInfo.licenses)))];
    await writeFile(join(output, "libraries", `${id}.json`), JSON.stringify({
        uniqueId: `npm:${name}`,
        artifactVersion: version,
        name,
        website: packageInfo.repository,
        licenses,
    }, null, 2) + "\n");
}
console.log(`Converted licenses for ${Object.keys(packages).length} npm packages.`);
