type RemoteServer = {
    id: number;
    userOrder: number;
    name: string;
    url: string;
    secret: string;
};

const SERVER_STORE = "servers";
const ORDER_INDEX = "userOrder";

export function createRemoteServerStorage(databaseName: string) {
    return new RemoteServerStorage(databaseName);
}

class RemoteServerStorage {
    private database?: Promise<IDBDatabase>;
    private readonly listeners = new Set<() => void>();
    private readonly channel?: BroadcastChannel;
    private closed = false;

    constructor(private readonly databaseName: string) {
        // Notifications improve freshness; transactions provide write isolation without them.
        try {
            this.channel = new BroadcastChannel(`${databaseName}:servers`);
            this.channel.onmessage = () => this.notify();
        } catch {
            // Focus and visibility changes still refresh browsers without BroadcastChannel.
        }
        window.addEventListener("focus", this.notify);
        window.addEventListener("pageshow", this.notify);
        document.addEventListener("visibilitychange", this.onVisibilityChange);
    }

    async list(): Promise<string> {
        const servers = await this.transaction<RemoteServer[]>("readonly", (store, result) => {
            const request = store.getAll();
            request.onsuccess = () => result(request.result);
        });
        servers.sort((left, right) => left.userOrder - right.userOrder || left.id - right.id);
        return JSON.stringify(servers);
    }

    async save(value: string): Promise<string> {
        const server: RemoteServer = {
            id: 0, userOrder: 0, name: "", url: "", secret: "", ...JSON.parse(value),
        };
        if (!Number.isSafeInteger(server.id) || server.id < 0 ||
            !Number.isSafeInteger(server.userOrder) || server.userOrder < 0) {
            throw new Error("Invalid server ID or order");
        }
        const saved = await this.transaction<RemoteServer>("readwrite", (store, result) => {
            const insert = () => {
                const request = store.index(ORDER_INDEX).openCursor(null, "prev");
                request.onsuccess = () => {
                    const userOrder = server.userOrder || (request.result?.value.userOrder ?? 0) + 1;
                    const record: Omit<RemoteServer, "id"> & {id?: number} = {...server, userOrder};
                    if (server.id === 0) {
                        delete record.id;
                    }
                    const added = store.add(record);
                    added.onsuccess = () => result({...server, userOrder, id: added.result as number});
                };
            };
            if (server.id === 0) {
                insert();
            } else {
                const request = store.get(server.id);
                request.onsuccess = () => {
                    if (request.result) {
                        const updated = store.put(server);
                        updated.onsuccess = () => result(server);
                    } else {
                        insert();
                    }
                };
            }
        });
        this.changed();
        return JSON.stringify(saved);
    }

    async delete(id: string): Promise<void> {
        const key = Number(id);
        if (!Number.isSafeInteger(key) || key <= 0) throw new Error("Invalid server ID");
        await this.transaction<void>("readwrite", (store, result) => {
            const request = store.delete(key);
            request.onsuccess = () => result();
        });
        this.changed();
    }

    subscribe(listener: () => void): {close: () => void} {
        this.listeners.add(listener);
        return {close: () => { this.listeners.delete(listener); }};
    }

    close(): void {
        this.closed = true;
        void this.database?.then(database => database.close(), () => {});
        this.channel?.close();
        this.listeners.clear();
        window.removeEventListener("focus", this.notify);
        window.removeEventListener("pageshow", this.notify);
        document.removeEventListener("visibilitychange", this.onVisibilityChange);
    }

    private readonly onVisibilityChange = () => {
        if (document.visibilityState === "visible") this.notify();
    };

    private readonly notify = () => {
        for (const listener of this.listeners) {
            try {
                listener();
            } catch (error) {
                console.error("Server storage observer failed", error);
            }
        }
    };

    private changed(): void {
        this.notify();
        try {
            this.channel?.postMessage(null);
        } catch {
            // A notification failure must not turn a committed write into a failed save.
        }
    }

    private open(): Promise<IDBDatabase> {
        if (this.closed) return Promise.reject(new Error("Server storage is closed"));
        return this.database ??= new Promise<IDBDatabase>((resolve, reject) => {
            const request = indexedDB.open(this.databaseName, 1);
            request.onupgradeneeded = () => {
                const store = request.result.createObjectStore(SERVER_STORE, {
                    keyPath: "id", autoIncrement: true,
                });
                store.createIndex(ORDER_INDEX, ORDER_INDEX);
            };
            request.onsuccess = () => {
                const database = request.result;
                database.onversionchange = () => {
                    database.close();
                    this.database = undefined;
                };
                database.onclose = () => { this.database = undefined; };
                if (this.closed) {
                    database.close();
                    reject(new Error("Server storage is closed"));
                } else {
                    resolve(database);
                }
            };
            request.onerror = () => reject(request.error);
        }).catch(error => {
            this.database = undefined;
            throw error;
        });
    }

    private async transaction<T>(
        mode: IDBTransactionMode,
        execute: (store: IDBObjectStore, result: (value: T) => void) => void,
    ): Promise<T> {
        const database = await this.open();
        return new Promise<T>((resolve, reject) => {
            const transaction = database.transaction(SERVER_STORE, mode);
            let result: T;
            transaction.oncomplete = () => resolve(result);
            transaction.onabort = () => reject(transaction.error ?? new Error("Server storage transaction aborted"));
            try {
                execute(transaction.objectStore(SERVER_STORE), value => { result = value; });
            } catch (error) {
                transaction.abort();
                reject(error);
            }
        });
    }
}
