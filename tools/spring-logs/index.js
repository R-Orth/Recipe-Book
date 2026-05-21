import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import fs from "fs";
import path from "path";

const ROOT = process.cwd();

const SERVICE_LOG_FILES = {
    gateway: path.join(ROOT, "gateway", "gateway", "logs", "gateway.log"),
    recipes: path.join(ROOT, "services", "recipes", "logs", "recipes.log"),
    auth:    path.join(ROOT, "services", "auth",    "logs", "auth.log"),
    users:   path.join(ROOT, "services", "users",   "logs", "users.log"),
};

const SERVICES = Object.keys(SERVICE_LOG_FILES);

function tailLines(filePath, count, filter) {
    if (!fs.existsSync(filePath)) {
        return `Log file not found: ${filePath}\nMake sure the service has been started at least once.`;
    }

    const raw = fs.readFileSync(filePath, "utf-8");
    let lines = raw.split("\n").filter((l) => l.trim() !== "");

    if (filter) {
        lines = lines.filter((l) => l.toLowerCase().includes(filter.toLowerCase()));
    }

    const tail = lines.slice(-count);

    if (tail.length === 0) {
        return filter
            ? `No log lines matching "${filter}" found in ${path.basename(filePath)}.`
            : `Log file exists but is empty: ${filePath}`;
    }

    const header = filter
        ? `Last ${tail.length} lines matching "${filter}" from ${path.basename(filePath)}:`
        : `Last ${tail.length} lines from ${path.basename(filePath)}:`;

    return `${header}\n\n${tail.join("\n")}`;
}

const server = new McpServer({ name: "spring-logs", version: "1.0.0" });

server.tool(
    "tail_logs",
    "Read the tail of a Spring Boot service log file. Optionally filter by text (e.g. '[STUB]', 'ERROR', 'WARN').",
    {
        service: z.enum(SERVICES).describe("Which service log to read: gateway, recipes, auth, or users"),
        lines:   z.number().int().min(1).max(500).default(50).describe("How many lines to return (default 50)"),
        filter:  z.string().optional().describe("Only return lines containing this string (case-insensitive)"),
    },
    async ({ service, lines, filter }) => {
        const logFile = SERVICE_LOG_FILES[service];
        const result = tailLines(logFile, lines ?? 50, filter);
        return { content: [{ type: "text", text: result }] };
    },
);

server.tool(
    "log_file_path",
    "Return the absolute path of a service's log file — useful if you want to open it in an editor or watch it with another tool.",
    {
        service: z.enum(SERVICES).describe("Which service: gateway, recipes, auth, or users"),
    },
    async ({ service }) => {
        const logFile = SERVICE_LOG_FILES[service];
        const exists  = fs.existsSync(logFile);
        const text    = `${logFile}\nExists: ${exists}`;
        return { content: [{ type: "text", text }] };
    },
);

const transport = new StdioServerTransport();
await server.connect(transport);
