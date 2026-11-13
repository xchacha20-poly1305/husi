import {
    createClashClient, ClashError, HttpError,
    type ClashClient, type ClashStream, type RequestOptions,
} from "tshomo";
import {isRefindRule, refind} from "tshomo/extensions/refind";

/** One owner for requests and streams. Only tshomo performs controller I/O. */
export function createClashSession(url: string, secret: string, logDebug: (message: string) => void) {
    const client = createClashClient({
        baseUrl: url,
        secret,
        requestTimeoutMs: 10000,
        fetch: loggedFetch(logDebug),
    });
    return new ClashSession(client, logDebug);
}

export class ClashSession {
    private readonly extension;
    private closed = false;
    private readonly requests = new Map<number, AbortController>();

    constructor(private readonly client: ClashClient, private readonly logDebug: (message: string) => void) {
        this.extension = client.extend(refind);
    }

    close() {
        this.logDebug("Clash session closed");
        this.closed = true;
        this.client.dispose();
    }

    cancelRequest(id: number) {
        this.requests.get(id)?.abort();
    }

    async request(operation: string, input: string, id: number): Promise<string> {
        const started = performance.now();
        this.logDebug(`Clash request ${id}: ${operation}`);
        const controller = new AbortController();
        this.requests.set(id, controller);
        try {
            const args = JSON.parse(input);
            const value = await this.execute(operation, args, {signal: controller.signal});
            this.logDebug(`Clash request ${id}: ${operation} completed in ${Math.round(performance.now() - started)}ms`);
            return JSON.stringify({value: value ?? null});
        } catch (error) {
            this.logDebug(`Clash request ${id}: ${operation} failed`);
            return JSON.stringify({error: describeError(error)});
        } finally {
            this.requests.delete(id);
        }
    }

    watch(kind: string, interval: number, receive: (value: string) => void) {
        const options = {
            reconnect: false as const,
            connectTimeoutMs: 10000,
            onStateChange: (state: string) => this.logDebug(`Clash stream ${kind}: ${state}`),
        };
        this.logDebug(`Starting Clash stream ${kind}, interval=${interval}ms`);
        let stream: ClashStream<unknown>;
        switch (kind) {
            case "traffic": stream = this.client.metrics.watchTraffic(options); break;
            case "connections": stream = this.client.connections.watch({...options, intervalMs: interval}); break;
            case "logs": stream = this.client.logs.watch({...options, level: "debug"}); break;
            case "memory": stream = this.client.metrics.watchMemory(options); break;
            default: throw new Error("Unknown Clash subscription");
        }
        let stopped = false;
        void (async () => {
            try {
                for await (const value of stream) {
                    if (!stopped && !this.closed) {
                        receive(JSON.stringify({value}));
                    }
                }
                if (!stopped && !this.closed) {
                    receive(JSON.stringify({error: {kind: "stream", message: "Stream ended"}}));
                }
            } catch (error) {
                if (!stopped && !this.closed) {
                    receive(JSON.stringify({error: describeError(error)}));
                }
            }
        })();
        return {close() { stopped = true; stream.close(); }};
    }

    private async execute(operation: string, args: Record<string, string>, options: RequestOptions): Promise<unknown> {
        switch (operation) {
            case "version": return this.client.system.getVersion(options);
            case "proxies": return this.client.proxies.list(options);
            case "select": return this.client.proxies.select(args.group, args.name, options);
            case "delay": return this.client.proxies.delay(args.name, undefined, options);
            case "closeConnection": return this.client.connections.close(args.id, options);
            case "closeConnections": return this.client.connections.closeAll(options);
            case "config": return this.client.configs.get(options);
            case "setMode": return this.client.configs.patch({mode: args.mode}, options);
            case "rules": return (await this.client.rules.list(options)).map(rule => ({...rule, toggleable: isRefindRule(rule)}));
            case "toggleRule": return this.extension.rules.toggleByUuid(args.uuid, options);
            case "proxyProviders": return Object.entries(await this.client.proxyProviders.list(options)).map(([name, provider]) => ({
                ...provider, name,
            }));
            case "ruleProviders": return Object.entries(await this.client.ruleProviders.list(options)).map(([name, provider]) => ({...provider, name}));
            case "updateProxyProvider": return this.client.proxyProviders.update(args.name, options);
            case "healthCheck": return this.client.proxyProviders.healthCheck(args.name, options);
            case "providerDelay": return this.client.proxyProviders.delayProxy(args.provider, args.name, undefined, options);
            case "updateRuleProvider": return this.client.ruleProviders.update(args.name, options);
            case "dns": return this.client.dns.query({name: args.name, type: args.type}, options);
            default: throw new Error("Unknown Clash operation");
        }
    }
}

function describeError(error: unknown) {
    return {
        kind: error instanceof ClashError ? error.kind : "unknown",
        status: error instanceof HttpError ? error.status : null,
        message: error instanceof Error ? error.message : "Clash request failed",
    };
}

/** Log endpoint paths and timing only; headers, query tokens and response bodies stay private. */
function loggedFetch(logDebug: (message: string) => void): typeof globalThis.fetch {
    return async (input, init) => {
        const url = new URL(input instanceof Request ? input.url : String(input));
        const method = init?.method ?? (input instanceof Request ? input.method : "GET");
        const started = performance.now();
        const response = await globalThis.fetch(input, init);
        logDebug(`Clash HTTP ${method} ${url.pathname}: ${response.status}, ${Math.round(performance.now() - started)}ms`);
        return response;
    };
}
