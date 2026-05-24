// export const API_URL = "https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items";  // AWS fallback (legacy)
export const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL ?? "http://localhost:8080/items";

export async function fetchAllRecipes() {
    const response = await fetch(GATEWAY_URL);
    if (!response.ok) throw new Error(`Failed to load recipes: ${response.status}`);
    return response.json();
}

export async function fetchRecipeById(id) {
    const response = await fetch(`${GATEWAY_URL}/${id}`);
    if (!response.ok) throw new Error(`Failed to load recipe ${id}: ${response.status}`);
    return response.json();
}

export async function createRecipe(recipe) {
    const response = await fetch(GATEWAY_URL, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(recipe),
    });
    if (!response.ok) throw new Error(`Failed to create recipe: ${response.status}`);
    return response;
}

export async function deleteRecipeById(id) {
    const response = await fetch(`${GATEWAY_URL}/${id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
    });
    if (!response.ok) throw new Error(`Failed to delete recipe ${id}: ${response.status}`);
    return response;
}
