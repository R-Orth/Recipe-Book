export const API_URL = "https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items";
export const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL ?? "http://localhost:8080/items";

function pingGateway(method, path, body) {
    const url = `${GATEWAY_URL}${path}`;
    const options = { method };
    if (body !== undefined) {
        options.headers = { "Content-Type": "application/json" };
        options.body = JSON.stringify(body);
    }

    const startedAt = performance.now();
    console.debug(`[GATEWAY] → ${method} ${url}`, body ?? "");

    fetch(url, options)
        .then(async (response) => {
            const elapsed = (performance.now() - startedAt).toFixed(1);
            const text = await response.text();
            let payload = text;
            try { payload = JSON.parse(text); } catch { /* leave as text */ }
            console.debug(
                `[GATEWAY] ← ${method} ${url} ${response.status} (${elapsed}ms)`,
                payload,
            );
        })
        .catch((error) => {
            const elapsed = (performance.now() - startedAt).toFixed(1);
            console.warn(
                `[GATEWAY] ✕ ${method} ${url} failed after ${elapsed}ms:`,
                error.message,
            );
        });
}

export async function fetchAllRecipes() {
    pingGateway("GET", "");
    const response = await fetch(API_URL);
    if (!response.ok) throw new Error(`Failed to load recipes: ${response.status}`);
    return response.json();
}

export async function fetchRecipeById(id) {
    pingGateway("GET", `/${id}`);
    const response = await fetch(`${API_URL}/${id}`);
    if (!response.ok) throw new Error(`Failed to load recipe ${id}: ${response.status}`);
    return response.json();
}

export async function createRecipe(recipe) {
    pingGateway("PUT", "", recipe);
    const response = await fetch(API_URL, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(recipe),
    });
    if (!response.ok) throw new Error(`Failed to create recipe: ${response.status}`);
    return response;
}

export async function deleteRecipeById(id) {
    pingGateway("DELETE", `/${id}`);
    const response = await fetch(`${API_URL}/${id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
    });
    if (!response.ok) throw new Error(`Failed to delete recipe ${id}: ${response.status}`);
    return response;
}
