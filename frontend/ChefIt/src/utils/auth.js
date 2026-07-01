export const GOOGLE_CLIENT_ID =
    import.meta.env.VITE_GOOGLE_CLIENT_ID ??
    "642465390637-kcp0hs8k1a4pm4uu75rtkp9l8r5h3cn0.apps.googleusercontent.com";

const AUTH_URL = import.meta.env.VITE_AUTH_URL ?? "http://localhost:8080/auth";
const USERS_URL = import.meta.env.VITE_USERS_URL ?? "http://localhost:8080/users";
const TOKEN_KEY = "chefit_token";

export async function loginWithGoogle(credential) {
    const response = await fetch(`${AUTH_URL}/google`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ credential }),
    });
    if (!response.ok) throw new Error(`Google login failed: ${response.status}`);

    const data = await response.json();
    storeTokenAndNotify(data);
    return data;
}

export async function loginWithCredentials(identifier, password) {
    const response = await fetch(`${AUTH_URL}/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identifier, password }),
    });
    if (!response.ok) throw await authError(response);

    const data = await response.json();
    storeTokenAndNotify(data);
    return data;
}

export async function register(identifier, password, realname) {
    const response = await fetch(`${AUTH_URL}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identifier, password, realname }),
    });
    if (!response.ok) throw await authError(response);

    const data = await response.json();
    storeTokenAndNotify(data);
    return data;
}

export async function fetchSelf() {
    const user = getCurrentUser();
    if (!user) throw new Error("Not signed in");

    const response = await fetch(`${AUTH_URL}/${user.identifier}`, {
        headers: { Authorization: `Bearer ${getToken()}` },
    });
    if (!response.ok) throw await authError(response);
    return response.json();
}

// Partial profile update via the users service (PUT /users/{uuid}). `updates` may carry
// any of: realname, identifier, and a currentPassword + newPassword pair. Self-only is
// enforced server-side against the JWT subject. Returns the updated PublicUser. Note: the
// session JWT is NOT reissued here (the users service only validates tokens), so an
// identifier/name change isn't reflected in the token until the next sign-in.
export async function updateAccount(updates) {
    const user = getCurrentUser();
    if (!user) throw new Error("Not signed in");

    const response = await fetch(`${USERS_URL}/${user.sub}`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${getToken()}`,
        },
        body: JSON.stringify(updates),
    });
    if (!response.ok) throw await authError(response);
    return response.json();
}

// Permanently deletes the signed-in user's account (DELETE /users/{uuid}). Requires the
// current password for confirmation; self-only server-side. On success the local session
// is cleared. Returns nothing (the endpoint responds 204 No Content).
export async function deleteAccount(password) {
    const user = getCurrentUser();
    if (!user) throw new Error("Not signed in");

    const response = await fetch(`${USERS_URL}/${user.sub}`, {
        method: "DELETE",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${getToken()}`,
        },
        body: JSON.stringify({ password }),
    });
    if (!response.ok) throw await authError(response);

    logout();
}

export function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

export function logout() {
    localStorage.removeItem(TOKEN_KEY);
    notifyAuthChanged();
}

// Decodes (does not verify) the JWT payload for display purposes. Signature
// verification stays on the server — this is just to read claims the server
// already vouched for when it issued the token.
export function getCurrentUser() {
    const token = getToken();
    if (!token) return null;
    const claims = decodeJwt(token);
    if (!claims) return null;
    if (claims.exp && claims.exp * 1000 < Date.now()) return null;
    return { identifier: claims.identifier, name: claims.name, sub: claims.sub };
}

function storeTokenAndNotify(data) {
    if (data?.token) {
        localStorage.setItem(TOKEN_KEY, data.token);
        notifyAuthChanged();
    }
}

async function authError(response) {
    let body = {};
    try { body = await response.json(); } catch { /* body might be empty */ }
    const err = new Error(`Auth request failed: ${response.status}`);
    err.status = response.status;
    err.body = body;
    return err;
}

function decodeJwt(token) {
    try {
        const payload = token.split(".")[1];
        if (!payload) return null;
        const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
        return JSON.parse(atob(base64));
    } catch {
        return null;
    }
}

function notifyAuthChanged() {
    if (typeof window !== "undefined") {
        window.dispatchEvent(new Event("chefit-auth"));
    }
}
