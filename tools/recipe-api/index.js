import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const AWS_URL     = "https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items";
const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8080/items";

const TARGET_ENUM = ["aws", "gateway", "both"];

async function apiCall(baseUrl, method, path = "", body = undefined) {
    const url  = `${baseUrl}${path}`;
    const opts = { method, headers: {} };
    if (body !== undefined) {
        opts.headers["Content-Type"] = "application/json";
        opts.body = JSON.stringify(body);
    }
    const start = Date.now();
    try {
        const res  = await fetch(url, opts);
        const ms   = Date.now() - start;
        let payload;
        const text = await res.text();
        try { payload = JSON.parse(text); } catch { payload = text; }
        return { ok: true, status: res.status, ms, payload };
    } catch (err) {
        return { ok: false, error: err.message, ms: Date.now() - start };
    }
}

function targetUrls(target) {
    if (target === "aws")     return { aws: AWS_URL };
    if (target === "gateway") return { gateway: GATEWAY_URL };
    return { aws: AWS_URL, gateway: GATEWAY_URL };
}

async function callAll(target, method, path, body) {
    const urls    = targetUrls(target);
    const entries = await Promise.all(
        Object.entries(urls).map(async ([name, base]) => [name, await apiCall(base, method, path, body)])
    );
    return Object.fromEntries(entries);
}

function formatResults(results) {
    return Object.entries(results)
        .map(([name, r]) => {
            const label  = `[${name.toUpperCase()}]`;
            if (!r.ok) return `${label} ERROR: ${r.error} (${r.ms}ms)`;
            const body   = typeof r.payload === "object"
                ? JSON.stringify(r.payload, null, 2)
                : r.payload;
            return `${label} ${r.status} (${r.ms}ms)\n${body}`;
        })
        .join("\n\n---\n\n");
}

const server = new McpServer({ name: "recipe-api", version: "1.0.0" });

server.tool(
    "list_recipes",
    "Fetch all recipes. Use target='both' to compare AWS vs gateway responses side-by-side.",
    {
        target: z.enum(TARGET_ENUM).default("aws").describe("Which backend to call: aws, gateway, or both"),
    },
    async ({ target }) => {
        const results = await callAll(target ?? "aws", "GET", "");
        return { content: [{ type: "text", text: formatResults(results) }] };
    },
);

server.tool(
    "get_recipe",
    "Fetch a single recipe by id.",
    {
        id:     z.string().describe("Recipe id"),
        target: z.enum(TARGET_ENUM).default("aws").describe("Which backend to call: aws, gateway, or both"),
    },
    async ({ id, target }) => {
        const results = await callAll(target ?? "aws", "GET", `/${id}`);
        return { content: [{ type: "text", text: formatResults(results) }] };
    },
);

server.tool(
    "add_recipe",
    "Add a new recipe via PUT. Sends to AWS, gateway, or both.",
    {
        name:        z.string().describe("Recipe name"),
        time:        z.string().describe("Estimated time in minutes, e.g. '30'"),
        servings:    z.string().describe("Number of servings, e.g. '4'"),
        ingredients: z.array(z.object({
            name:        z.string(),
            amount:      z.string(),
            measurement: z.string().describe("e.g. Cup, Tbsp, g — or empty string for no measurement"),
        })).describe("List of ingredients"),
        steps:  z.array(z.string()).describe("Ordered list of step instructions"),
        target: z.enum(TARGET_ENUM).default("aws").describe("Which backend to call: aws, gateway, or both"),
    },
    async ({ name, time, servings, ingredients, steps, target }) => {
        const recipe  = { id: `${Date.now()}`, name, time, servings, ingredients, steps };
        const results = await callAll(target ?? "aws", "PUT", "", recipe);
        return { content: [{ type: "text", text: formatResults(results) }] };
    },
);

server.tool(
    "delete_recipe",
    "Delete a recipe by id.",
    {
        id:     z.string().describe("Recipe id to delete"),
        target: z.enum(TARGET_ENUM).default("aws").describe("Which backend to call: aws, gateway, or both"),
    },
    async ({ id, target }) => {
        const results = await callAll(target ?? "aws", "DELETE", `/${id}`);
        return { content: [{ type: "text", text: formatResults(results) }] };
    },
);

server.tool(
    "api_config",
    "Show the current AWS and gateway URLs this tool is pointing at.",
    {},
    async () => {
        const text = `AWS URL:     ${AWS_URL}\nGateway URL: ${GATEWAY_URL}\n\nTo change the gateway URL, set the GATEWAY_URL env var in .claude/settings.json.`;
        return { content: [{ type: "text", text }] };
    },
);

const transport = new StdioServerTransport();
await server.connect(transport);
