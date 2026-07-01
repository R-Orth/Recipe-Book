// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import RegisterForm from "./RegisterForm.jsx";

vi.mock("../utils/auth.js", () => ({
    register: vi.fn(),
}));
import { register } from "../utils/auth.js";

beforeEach(() => {
    register.mockReset();
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("RegisterForm", () => {
    it("submits identifier, password, realname and calls onSuccess", async () => {
        const data = { token: "jwt", uuid: "u2", identifier: "bob", realname: "Bob", providers: ["local"] };
        register.mockResolvedValue(data);
        const onSuccess = vi.fn();

        render(<RegisterForm onSuccess={onSuccess} />);
        fireEvent.change(screen.getByLabelText(/identifier/i), { target: { value: "bob" } });
        fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "Abcdefg12!" } });
        fireEvent.change(screen.getByLabelText(/name/i), { target: { value: "Bob" } });
        fireEvent.click(screen.getByRole("button", { name: /register/i }));

        await waitFor(() => expect(register).toHaveBeenCalledWith("bob", "Abcdefg12!", "Bob"));
        await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(data));
    });

    it("renders per-violation messages on 400", async () => {
        register.mockRejectedValue(Object.assign(new Error("400"), {
            status: 400,
            body: { error: "invalid_request", details: ["too_short", "missing_special"] },
        }));

        render(<RegisterForm onSuccess={vi.fn()} />);
        fireEvent.change(screen.getByLabelText(/identifier/i), { target: { value: "alice" } });
        fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "weak" } });
        fireEvent.click(screen.getByRole("button", { name: /register/i }));

        await waitFor(() => {
            expect(screen.getByText(/too_short/i)).toBeInTheDocument();
            expect(screen.getByText(/missing_special/i)).toBeInTheDocument();
        });
    });

    it("renders the Google suggestion hint on 401 (registration_unavailable)", async () => {
        register.mockRejectedValue(Object.assign(new Error("401"), {
            status: 401,
            body: { error: "registration_unavailable" },
        }));

        render(<RegisterForm onSuccess={vi.fn()} />);
        fireEvent.change(screen.getByLabelText(/identifier/i), { target: { value: "alice" } });
        fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "Abcdefg12!" } });
        fireEvent.click(screen.getByRole("button", { name: /register/i }));

        await waitFor(() =>
            expect(screen.getByText(/signed up with Google/i)).toBeInTheDocument()
        );
    });
});
