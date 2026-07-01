// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import LocalLoginForm from "./LocalLoginForm.jsx";

vi.mock("../utils/auth.js", () => ({
    loginWithCredentials: vi.fn(),
}));
import { loginWithCredentials } from "../utils/auth.js";

beforeEach(() => {
    loginWithCredentials.mockReset();
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("LocalLoginForm", () => {
    it("submits identifier and password and calls onSuccess with the response", async () => {
        const data = { token: "jwt", uuid: "u1", identifier: "alice", realname: "Ada", providers: ["local"] };
        loginWithCredentials.mockResolvedValue(data);
        const onSuccess = vi.fn();

        render(<LocalLoginForm onSuccess={onSuccess} />);
        fireEvent.change(screen.getByLabelText(/identifier/i), { target: { value: "alice" } });
        fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "Abcdefg12!" } });
        fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

        await waitFor(() => expect(loginWithCredentials).toHaveBeenCalledWith("alice", "Abcdefg12!"));
        await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(data));
    });

    it("renders the universal Google suggestion hint on any 401", async () => {
        loginWithCredentials.mockRejectedValue(Object.assign(new Error("401"), {
            status: 401,
            body: { error: "invalid_credentials" },
        }));

        render(<LocalLoginForm onSuccess={vi.fn()} />);
        fireEvent.change(screen.getByLabelText(/identifier/i), { target: { value: "alice" } });
        fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrong" } });
        fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

        await waitFor(() =>
            expect(screen.getByText(/signed up with Google/i)).toBeInTheDocument()
        );
    });

    it("renders validation details on 400", async () => {
        loginWithCredentials.mockRejectedValue(Object.assign(new Error("400"), {
            status: 400,
            body: { error: "invalid_request", details: ["missing_password"] },
        }));

        render(<LocalLoginForm onSuccess={vi.fn()} />);
        fireEvent.change(screen.getByLabelText(/identifier/i), { target: { value: "alice" } });
        fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

        await waitFor(() =>
            expect(screen.getByText(/missing_password/i)).toBeInTheDocument()
        );
    });
});
