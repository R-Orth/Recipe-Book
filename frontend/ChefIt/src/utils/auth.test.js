import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
    loginWithGoogle,
    loginWithCredentials,
    register,
    fetchSelf,
    getToken,
    logout,
    getCurrentUser,
} from "./auth.js";

function makeJwt(payload) {
    const header = btoa(JSON.stringify({ alg: "HS256" }));
    const body = btoa(JSON.stringify(payload));
    return `${header}.${body}.signature-not-verified-on-frontend`;
}

const AUTH_GOOGLE_URL = "http://localhost:8080/auth/google";
const AUTH_LOGIN_URL = "http://localhost:8080/auth/login";
const AUTH_REGISTER_URL = "http://localhost:8080/auth/register";

function okResponse(body) {
    return { ok: true, status: 200, json: async () => body };
}

let store;

beforeEach(() => {
    store = {};
    vi.stubGlobal("fetch", vi.fn());
    vi.stubGlobal("localStorage", {
        getItem: vi.fn((k) => store[k] ?? null),
        setItem: vi.fn((k, v) => { store[k] = v; }),
        removeItem: vi.fn((k) => { delete store[k]; }),
    });
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe("loginWithGoogle", () => {
    it("POSTs the credential to the gateway auth route and returns the body", async () => {
        const body = { token: "jwt-token", uuid: "u1", email: "a@b.com" };
        fetch.mockResolvedValue(okResponse(body));

        const result = await loginWithGoogle("google-id-token");

        expect(fetch).toHaveBeenCalledWith(AUTH_GOOGLE_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ credential: "google-id-token" }),
        });
        expect(result).toEqual(body);
    });

    it("stores the returned JWT in localStorage", async () => {
        fetch.mockResolvedValue(okResponse({ token: "jwt-token" }));

        await loginWithGoogle("google-id-token");

        expect(localStorage.setItem).toHaveBeenCalledWith("chefit_token", "jwt-token");
        expect(getToken()).toBe("jwt-token");
    });

    it("throws when the response is not ok", async () => {
        fetch.mockResolvedValue({ ok: false, status: 401, json: async () => ({}) });

        await expect(loginWithGoogle("bad")).rejects.toThrow("Google login failed: 401");
    });
});

describe("getCurrentUser", () => {
    it("returns identifier/name/sub from the JWT identifier claim", () => {
        const futureExp = Math.floor(Date.now() / 1000) + 3600;
        store["chefit_token"] = makeJwt({
            sub: "u1",
            identifier: "alice@x.com",
            name: "Ada",
            exp: futureExp,
        });

        expect(getCurrentUser()).toEqual({
            identifier: "alice@x.com",
            name: "Ada",
            sub: "u1",
        });
    });

    it("returns null when token is expired", () => {
        const pastExp = Math.floor(Date.now() / 1000) - 60;
        store["chefit_token"] = makeJwt({
            sub: "u1",
            identifier: "alice@x.com",
            name: "Ada",
            exp: pastExp,
        });

        expect(getCurrentUser()).toBeNull();
    });

    it("returns null when no token", () => {
        expect(getCurrentUser()).toBeNull();
    });
});

describe("loginWithCredentials", () => {
    it("POSTs identifier+password to the gateway login route and stores the JWT", async () => {
        const body = { token: "jwt-local", uuid: "u1", identifier: "alice", realname: "Ada", providers: ["local"] };
        fetch.mockResolvedValue(okResponse(body));

        const result = await loginWithCredentials("alice", "Abcdefg12!");

        expect(fetch).toHaveBeenCalledWith(AUTH_LOGIN_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ identifier: "alice", password: "Abcdefg12!" }),
        });
        expect(result).toEqual(body);
        expect(localStorage.setItem).toHaveBeenCalledWith("chefit_token", "jwt-local");
    });

    it("throws on 401 with the response body as cause shape", async () => {
        fetch.mockResolvedValue({ ok: false, status: 401, json: async () => ({ error: "invalid_credentials" }) });

        await expect(loginWithCredentials("alice", "wrong")).rejects.toMatchObject({
            status: 401,
            body: { error: "invalid_credentials" },
        });
    });

    it("throws on 400 carrying the validation details", async () => {
        fetch.mockResolvedValue({
            ok: false,
            status: 400,
            json: async () => ({ error: "invalid_request", details: ["missing_password"] }),
        });

        await expect(loginWithCredentials("alice", "")).rejects.toMatchObject({
            status: 400,
            body: { details: ["missing_password"] },
        });
    });
});

describe("register", () => {
    it("POSTs identifier+password+realname and stores the JWT", async () => {
        const body = { token: "jwt-new", uuid: "u2", identifier: "bob", realname: "Bob", providers: ["local"] };
        fetch.mockResolvedValue(okResponse(body));

        const result = await register("bob", "Abcdefg12!", "Bob");

        expect(fetch).toHaveBeenCalledWith(AUTH_REGISTER_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ identifier: "bob", password: "Abcdefg12!", realname: "Bob" }),
        });
        expect(result).toEqual(body);
        expect(localStorage.setItem).toHaveBeenCalledWith("chefit_token", "jwt-new");
    });

    it("throws on 401 (identifier taken — generic body)", async () => {
        fetch.mockResolvedValue({ ok: false, status: 401, json: async () => ({ error: "registration_unavailable" }) });

        await expect(register("alice", "Abcdefg12!", "Ada")).rejects.toMatchObject({
            status: 401,
            body: { error: "registration_unavailable" },
        });
    });

    it("throws on 400 with validation details", async () => {
        fetch.mockResolvedValue({
            ok: false,
            status: 400,
            json: async () => ({ error: "invalid_request", details: ["too_short", "missing_special"] }),
        });

        await expect(register("alice", "weak", "Ada")).rejects.toMatchObject({
            status: 400,
            body: { details: ["too_short", "missing_special"] },
        });
    });
});

describe("fetchSelf", () => {
    it("GETs /auth/{identifier} with Bearer token from localStorage", async () => {
        const futureExp = Math.floor(Date.now() / 1000) + 3600;
        store["chefit_token"] = makeJwt({ sub: "u1", identifier: "alice", name: "Ada", exp: futureExp });
        const body = { uuid: "u1", identifier: "alice", identifierIsEmail: false, providers: ["local"], realname: "Ada" };
        fetch.mockResolvedValue(okResponse(body));

        const result = await fetchSelf();

        expect(fetch).toHaveBeenCalledWith("http://localhost:8080/auth/alice", {
            headers: { Authorization: `Bearer ${store["chefit_token"]}` },
        });
        expect(result).toEqual(body);
    });

    it("throws on 401 (expired/invalid token)", async () => {
        const futureExp = Math.floor(Date.now() / 1000) + 3600;
        store["chefit_token"] = makeJwt({ sub: "u1", identifier: "alice", exp: futureExp });
        fetch.mockResolvedValue({ ok: false, status: 401, json: async () => ({}) });

        await expect(fetchSelf()).rejects.toMatchObject({ status: 401 });
    });

    it("throws synchronously when there is no token", async () => {
        await expect(fetchSelf()).rejects.toThrow(/not signed in/i);
    });
});

describe("logout", () => {
    it("removes the stored token", () => {
        store["chefit_token"] = "jwt-token";

        logout();

        expect(localStorage.removeItem).toHaveBeenCalledWith("chefit_token");
        expect(getToken()).toBeNull();
    });
});
