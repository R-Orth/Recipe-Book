import { describe, it, expect } from "vitest";
import { homeQuips, addQuips, randomHomeQuip, randomAddQuip } from "./quips.js";

describe("quips", () => {
    it("randomHomeQuip returns a member of homeQuips", () => {
        expect(homeQuips).toContain(randomHomeQuip());
    });

    it("randomAddQuip returns a member of addQuips", () => {
        expect(addQuips).toContain(randomAddQuip());
    });
});
