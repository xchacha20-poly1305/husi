// A simulated controller for manual browser acceptance; never a live-kernel compatibility claim.
import {createServer} from "node:http";
import {readFile, stat} from "node:fs/promises";
import {extname, resolve, sep} from "node:path";
import {fileURLToPath} from "node:url";
import {WebSocketServer} from "ws";

const root = resolve(process.argv[2] ?? fileURLToPath(new URL("../../build/dist/wasmJs/productionExecutable", import.meta.url)));
const port = Number(process.argv[3] ?? 18088);
const events = [];
let disabled = false;
const config = {
    mode: "rule",
    "log-level": "info",
    ipv6: true,
    "allow-lan": false,
    "bind-address": "*",
    "mixed-port": 7890,
    "socks-port": 7891,
    port: 0,
    "redir-port": 0,
    "tproxy-port": 0,
    tun: {
        enable: false,
    }
};
const proxies = {
    Group: {
        name: "Group",
        type: "Selector",
        all: ["Node", "DIRECT"],
        now: "Node",
    },
    Node: {
        name: "Node",
        type: "Shadowsocks",
        history: [{time: new Date().toISOString(), delay: 42}],
    },
    DIRECT: {
        name: "DIRECT",
        type: "Direct",
        history: [],
    },
};

function json(res, data, status = 200) {
    res.writeHead(status, {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"});
    res.end(data === undefined ? undefined : JSON.stringify(data));
}

const server = createServer(async (req, res) => {
    const url = new URL(req.url, "http://localhost");
    if (req.method === "OPTIONS") {
        res.writeHead(204, {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "*",
            "Access-Control-Allow-Methods": "GET,PUT,PATCH,DELETE,POST,OPTIONS"
        }).end();
        return;
    }
    if (url.pathname === "/__events") {
        return json(res, events);
    }
    if (url.pathname.startsWith("/controller/")) {
        events.push({method: req.method, path: req.url});
        const chunks = [];
        for await (const chunk of req) {
            chunks.push(chunk);
        }
        const body = Buffer.concat(chunks).toString();
        if (url.pathname === "/controller/version") {
            return json(res, {version: "fixture-0.1"});
        }
        if (url.pathname === "/controller/configs") {
            if (req.method === "PATCH") {
                const update = JSON.parse(body);
                if (Object.keys(update).length !== 1 || typeof update.mode !== "string") {
                    return json(res, {message: "Only mode switching is supported"}, 400);
                }
                config.mode = update.mode;
                return json(res, undefined, 204);
            }
            if (req.method !== "GET") {
                return json(res, {message: "Unsupported operation"}, 405);
            }
            return json(res, config);
        }
        if (url.pathname === "/controller/proxies") {
            return json(res, {proxies});
        }
        if (url.pathname === "/controller/proxies/Group" && req.method === "PUT") {
            proxies.Group.now = JSON.parse(body).name;
            return json(res, undefined, 204);
        }
        if (url.pathname.endsWith("/delay") || url.pathname.endsWith("/healthcheck")) {
            return json(res, {delay: 36});
        }
        if (url.pathname === "/controller/rules") {
            return json(res, {
                rules: [
                    {type: "DomainSuffix", payload: "example.test", proxy: "Group", uuid: "refind-test-rule", disabled},
                    {type: "Match", payload: "all", proxy: "DIRECT"},
                ]
            });
        }
        if (url.pathname === "/controller/rules/refind-test-rule" && req.method === "PUT") {
            disabled = !disabled;
            return json(res, undefined, 204);
        }
        if (url.pathname === "/controller/providers/proxies") {
            return json(res, {
                providers: {
                    Subscription: {
                        name: "Subscription",
                        type: "Proxy",
                        vehicleType: "HTTP",
                        updatedAt: new Date().toISOString(),
                        subscriptionInfo: {Upload: 1000, Download: 2000, Total: 100000, Expire: 2000000000},
                        proxies: [proxies.Node]
                    },
                }
            });
        }
        if (url.pathname === "/controller/providers/rules") {
            return json(res, {
                providers: {
                    Rules: {
                        name: "Rules",
                        type: "Rule",
                        behavior: "domain",
                        format: "yaml",
                        ruleCount: 100,
                        updatedAt: new Date().toISOString()
                    },
                }
            });
        }
        if (url.pathname.startsWith("/controller/providers/") && req.method === "PUT") {
            return json(res, undefined, 204);
        }
        if (url.pathname === "/controller/dns/query") {
            return json(res, {
                Status: 0,
                Question: [{Name: url.searchParams.get("name"), Qtype: 1, Qclass: 1}],
                Answer: [{name: "example.test.", type: 1, TTL: 60, data: "192.0.2.1"}]
            });
        }
        if (url.pathname.startsWith("/controller/connections") && req.method === "DELETE") {
            return json(res, undefined, 204);
        }
        return json(res, {message: "Unsupported fixture endpoint"}, 404);
    }
    if (req.method !== "GET") {
        return json(res, {message: "Not RPC"}, 404);
    }
    let path;
    try {
        path = resolve(root, "." + decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname));
    } catch {
        return json(res, {}, 400);
    }
    if (!path.startsWith(root + sep)) {
        return json(res, {}, 403);
    }
    try {
        const file = await stat(path);
        if (!file.isFile()) {
            return json(res, {}, 404);
        }
        const mime = {
            ".html": "text/html",
            ".js": "text/javascript",
            ".mjs": "text/javascript",
            ".wasm": "application/wasm",
            ".json": "application/json",
            ".css": "text/css"
        }[extname(path)] ?? "application/octet-stream";
        res.writeHead(200, {"Content-Type": mime});
        res.end(await readFile(path));
    } catch {
        json(res, {}, 404);
    }
});
const sockets = new WebSocketServer({server});
sockets.on("connection", (socket, req) => {
    const path = new URL(req.url, "http://localhost").pathname;
    events.push({method: "WS", path: req.url});
    const sample = () => {
        if (socket.readyState !== 1) return;
        if (path.endsWith("/traffic")) {
            socket.send(JSON.stringify({up: 1024, down: 2048}));
        } else if (path.endsWith("/memory")) {
            socket.send(JSON.stringify({inuse: 20971520}));
        } else if (path.endsWith("/connections")) {
            socket.send(JSON.stringify({
                uploadTotal: 4096,
                downloadTotal: 8192,
                connections: [{
                    id: "fixture-connection",
                    metadata: {
                        network: "tcp", type: "Mixed", sourceIP: "127.0.0.1", sourcePort: 1234,
                        destinationIP: "192.0.2.1", destinationPort: "443", host: "example.test"
                    },
                    upload: 4096,
                    download: 8192,
                    start: "2026-11-13T00:00:00Z",
                    chains: ["Node", "Group"],
                    rule: "DomainSuffix",
                    rulePayload: "example.test",
                }],
            }));
        } else if (path.endsWith("/logs")) {
            socket.send(JSON.stringify({
                type: "info",
                payload: "Simulated Clash controller log"
            }));
        }
    };
    sample();
    const timer = setInterval(sample, 1000);
    socket.on("close", () => clearInterval(timer));
});
server.listen(port, "127.0.0.1", () => console.log(`Fixture dashboard: http://127.0.0.1:${port}; controller: http://127.0.0.1:${port}/controller`));
