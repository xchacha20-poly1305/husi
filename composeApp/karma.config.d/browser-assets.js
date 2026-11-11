// Exercise the same browser storage bridge that the dashboard loads in production.
config.files.unshift({
    pattern: require("node:path").join(config.basePath, "kotlin/browser.js"),
    included: true,
    served: true,
    watched: false,
});
