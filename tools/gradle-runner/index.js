import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { spawn } from "child_process";
import path from "path";

const ROOT = process.cwd();
const IS_WIN = process.platform === "win32";

const SERVICE_DIRS = {
    gateway: path.join(ROOT, "gateway", "gateway"),
    recipes: path.join(ROOT, "services", "recipes"),
    auth:    path.join(ROOT, "services", "auth"),
    users:   path.join(ROOT, "services", "users"),
};

const SERVICES = Object.keys(SERVICE_DIRS);

const STARTUP_SUCCESS = /Started \w+Application in/;
const STARTUP_FAILURE = /APPLICATION FAILED TO START|BUILD FAILED/;

function runGradle(serviceDir, task, timeoutMs) {
    return new Promise((resolve) => {
        const gradlew = IS_WIN ? "gradlew.bat" : "./gradlew";
        const proc = spawn(gradlew, [task, "--console=plain"], {
            cwd: serviceDir,
            shell: IS_WIN,
        });

        const lines = [];
        let timedOut = false;
        let finished = false;

        const finish = (exitCode) => {
            if (finished) return;
            finished = true;
            clearTimeout(timer);

            const output = lines.join("\n");
            const status = timedOut
                ? `Timed out after ${timeoutMs / 1000}s`
                : exitCode === 0
                ? "BUILD SUCCESSFUL"
                : `FAILED (exit ${exitCode})`;

            resolve({ output, status, timedOut, exitCode });
        };

        const timer = setTimeout(() => {
            timedOut = true;
            proc.kill("SIGTERM");
            finish(null);
        }, timeoutMs);

        const collect = (chunk) => {
            const text = chunk.toString();
            lines.push(...text.split("\n").filter((l) => l.trim()));

            // For bootRun: detect startup success or failure early
            if (task === "bootRun") {
                if (STARTUP_SUCCESS.test(text)) {
                    clearTimeout(timer);
                    setTimeout(() => { proc.kill("SIGTERM"); finish(0); }, 500);
                } else if (STARTUP_FAILURE.test(text)) {
                    clearTimeout(timer);
                    setTimeout(() => { proc.kill("SIGTERM"); finish(1); }, 500);
                }
            }
        };

        proc.stdout.on("data", collect);
        proc.stderr.on("data", collect);
        proc.on("close", finish);
        proc.on("error", (err) => {
            lines.push(`[ERROR] ${err.message}`);
            finish(1);
        });
    });
}

const server = new McpServer({ name: "gradle-runner", version: "1.0.0" });

server.tool(
    "run_gradle",
    "Run a Gradle task in a service directory. For build/test/compileJava: waits for completion. For bootRun: waits until Spring reports 'Started' or 'FAILED' (up to timeout).",
    {
        service: z.enum(SERVICES).describe("Which service to build: gateway, recipes, auth, or users"),
        task:    z.string().describe("Gradle task to run — e.g. build, test, compileJava, bootRun, bootJar, clean"),
        timeout: z.number().int().min(5).max(300).default(90).describe("Max seconds to wait (default 90). bootRun auto-exits on startup detection."),
    },
    async ({ service, task, timeout }) => {
        const serviceDir = SERVICE_DIRS[service];
        const timeoutMs  = (timeout ?? 90) * 1000;

        const label = `[gradle-runner] ${service} :${task}`;
        const { output, status, timedOut } = await runGradle(serviceDir, task, timeoutMs);

        const note = task === "bootRun" && !timedOut
            ? "\nNote: process was terminated after startup detection. Start it in a separate terminal for ongoing use."
            : timedOut
            ? `\nNote: reached ${timeout}s timeout — partial output shown above.`
            : "";

        const text = `${label}\nStatus: ${status}\n\n${output}${note}`;
        return { content: [{ type: "text", text }] };
    },
);

server.tool(
    "list_services",
    "List all known services and their Gradle project directories.",
    {},
    async () => {
        const rows = SERVICES.map((s) => `  ${s.padEnd(10)} ${SERVICE_DIRS[s]}`).join("\n");
        return { content: [{ type: "text", text: `Known services:\n${rows}` }] };
    },
);

const transport = new StdioServerTransport();
await server.connect(transport);
