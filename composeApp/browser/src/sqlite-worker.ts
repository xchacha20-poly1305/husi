import sqlite3InitModule, {type Database, type PreparedStatement, type SAHPoolUtil} from "@sqlite.org/sqlite-wasm";

// Implements the message protocol of androidx.sqlite.driver.web.WebWorkerSQLiteDriver.
const sqlite = sqlite3InitModule();
let storage: Promise<SAHPoolUtil> | undefined;
const databases = new Map<number, Database>();
const statements = new Map<number, PreparedStatement>();
let nextId = 0;

type Request = {
    cmd: "open" | "prepare" | "step" | "close";
    fileName: string;
    databaseId: number;
    statementId: number;
    sql: string;
    bindings: (number | bigint | string | Uint8Array | null)[];
};

async function execute(request: Request) {
    const instance = await sqlite;
    switch (request.cmd) {
        case "open": {
            const database = request.fileName === ":memory:"
                ? new instance.oo1.DB(":memory:")
                : new (await (storage ??= instance.installOpfsSAHPoolVfs({name: "husi-remote"})))
                    .OpfsSAHPoolDb(request.fileName);
            const databaseId = ++nextId;
            databases.set(databaseId, database);
            return {databaseId};
        }
        case "prepare": {
            const database = databases.get(request.databaseId);
            if (!database) {
                throw new Error("Database is closed");
            }
            const statement = database.prepare(request.sql);
            const statementId = ++nextId;
            statements.set(statementId, statement);
            return {
                statementId,
                parameterCount: statement.parameterCount,
                columnNames: statement.columnCount > 0 ? statement.getColumnNames([]) : [],
            };
        }
        case "step": {
            const statement = statements.get(request.statementId);
            if (!statement) {
                throw new Error("Statement is closed");
            }
            statement.reset(true);
            request.bindings.forEach((value, index) => statement.bind(index + 1, value));
            const rows = [];
            const columnTypes = [];
            while (statement.step()) {
                if (columnTypes.length === 0) {
                    for (let column = 0; column < statement.columnCount; column++) {
                        columnTypes.push(instance.capi.sqlite3_column_type(statement, column));
                    }
                }
                rows.push(statement.get([]));
            }
            return {rows, columnTypes};
        }
        case "close":
            statements.get(request.statementId)?.finalize();
            statements.delete(request.statementId);
            databases.get(request.databaseId)?.close();
            databases.delete(request.databaseId);
            return;
        default:
            throw new Error("Unknown database command");
    }
}

let pending = Promise.resolve();
self.onmessage = (event: MessageEvent<{id: number; data: Request}>) => {
    const {id, data} = event.data;
    pending = pending.then(async () => {
        try {
            const result = await execute(data);
            if (result !== undefined) {
                self.postMessage({id, data: result});
            }
        } catch (error) {
            self.postMessage({id, error: error instanceof Error ? error.message : String(error)});
        }
    });
};
