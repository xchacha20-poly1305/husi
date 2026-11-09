const fs = require("node:fs");
const path = require("node:path");

config.output.crossOriginLoading = "anonymous";
config.plugins.push({
    apply(compiler) {
        const pluginName = "HusiWebIndexPlugin";
        const templatePath = path.resolve(__dirname, "kotlin/index.template.html");
        const indicatorStylesPath = path.resolve(__dirname, "kotlin/loading-indicator.css");
        const indicatorScriptPath = path.resolve(__dirname, "kotlin/loading-indicator.js");
        compiler.hooks.thisCompilation.tap(pluginName, (compilation) => {
            for (const file of [templatePath, indicatorStylesPath, indicatorScriptPath]) {
                compilation.fileDependencies.add(file);
            }
            compilation.hooks.processAssets.tap(
                {
                    name: pluginName,
                    stage: compiler.webpack.Compilation.PROCESS_ASSETS_STAGE_REPORT,
                },
                () => {
                    const preloads = compilation.getAssets()
                        .filter(({name}) => name.endsWith(".wasm"))
                        .map(({name}) => `  <link rel="preload" href="${name}" as="fetch" type="application/wasm" crossorigin="anonymous">`)
                        .join("\n");
                    const template = fs.readFileSync(templatePath, "utf8");
                    const html = template
                        .replace("  <!-- webpack-wasm-preloads -->", preloads)
                        .replace("  <!-- loading-indicator-styles -->", () =>
                            `<style>${fs.readFileSync(indicatorStylesPath, "utf8")}</style>`)
                        .replace("  <!-- loading-indicator-script -->", () =>
                            `<script>${fs.readFileSync(indicatorScriptPath, "utf8")}</script>`);
                    compilation.emitAsset("index.html", new compiler.webpack.sources.RawSource(html));
                },
            );
        });
    },
});
