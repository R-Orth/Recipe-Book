import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
    GATEWAY_URL,
    fetchAllRecipes,
    fetchRecipeById,
    createRecipe,
    deleteRecipeById,
} from "./api.js";

function okResponse(body) {
    return { ok: true, status: 200, json: async () => body };
}

function errorResponse(status) {
    return { ok: false, status, json: async () => ({}) };
}

beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe("fetchAllRecipes", () => {
    it("GETs GATEWAY_URL and returns parsed JSON", async () => {
        const recipes = [{ id: "1", name: "Toast" }];
        fetch.mockResolvedValue(okResponse(recipes));

        const result = await fetchAllRecipes();

        expect(fetch).toHaveBeenCalledWith(GATEWAY_URL);
        expect(result).toEqual(recipes);
    });

    it("throws when the response is not ok", async () => {
        fetch.mockResolvedValue(errorResponse(500));
        await expect(fetchAllRecipes()).rejects.toThrow("Failed to load recipes: 500");
    });
});

describe("fetchRecipeById", () => {
    it("GETs GATEWAY_URL/{id} and returns parsed JSON", async () => {
        const recipe = { id: "42", name: "Soup" };
        fetch.mockResolvedValue(okResponse(recipe));

        const result = await fetchRecipeById("42");

        expect(fetch).toHaveBeenCalledWith(`${GATEWAY_URL}/42`);
        expect(result).toEqual(recipe);
    });

    it("throws when the response is not ok", async () => {
        fetch.mockResolvedValue(errorResponse(404));
        await expect(fetchRecipeById("42")).rejects.toThrow("Failed to load recipe 42: 404");
    });
});

describe("createRecipe", () => {
    it("PUTs JSON to GATEWAY_URL", async () => {
        const recipe = { id: "1", name: "Toast" };
        fetch.mockResolvedValue(okResponse(recipe));

        await createRecipe(recipe);

        expect(fetch).toHaveBeenCalledWith(GATEWAY_URL, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(recipe),
        });
    });

    it("throws when the response is not ok", async () => {
        fetch.mockResolvedValue(errorResponse(400));
        await expect(createRecipe({ id: "1" })).rejects.toThrow("Failed to create recipe: 400");
    });
});

describe("deleteRecipeById", () => {
    it("DELETEs GATEWAY_URL/{id}", async () => {
        fetch.mockResolvedValue(okResponse(""));

        await deleteRecipeById("7");

        expect(fetch).toHaveBeenCalledWith(`${GATEWAY_URL}/7`, {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
        });
    });

    it("throws when the response is not ok", async () => {
        fetch.mockResolvedValue(errorResponse(500));
        await expect(deleteRecipeById("7")).rejects.toThrow("Failed to delete recipe 7: 500");
    });
});
