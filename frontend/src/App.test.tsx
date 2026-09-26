import {cleanup, fireEvent, render, screen, waitFor,} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {AuthScreen} from "./components/AuthScreen";
import {Notice} from "./components/ui";
import {auth} from "./client";

vi.mock("./client", () => ({auth: vi.fn(), makeClient: vi.fn()}));
afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});
describe("Identity forms", () => {
    it("shows validation constraints for registration", () => {
        render(<AuthScreen onDone={() => {
        }}/>);
        fireEvent.click(screen.getByText("New to TeamFlow? Create an account"));
        expect(screen.getByLabelText("Your name")).toBeRequired();
        expect(screen.getByLabelText("Password")).toHaveAttribute(
            "minlength",
            "12",
        );
    });
    it("reports failed login and preserves the form for retry", async () => {
        vi.mocked(auth).mockRejectedValueOnce(
            new Error("Invalid email or password."),
        );
        render(<AuthScreen onDone={() => {
        }}/>);
        fireEvent.change(screen.getByLabelText("Email address"), {
            target: {value: "user@example.com"},
        });
        fireEvent.change(screen.getByLabelText("Password"), {
            target: {value: "invalid"},
        });
        fireEvent.submit(
            screen.getByRole("button", {name: /Sign in/}).closest("form")!,
        );
        await waitFor(() =>
            expect(screen.getByRole("alert")).toHaveTextContent(
                "Invalid email or password.",
            ),
        );
        expect(screen.getByLabelText("Email address")).toHaveValue(
            "user@example.com",
        );
    });
    it("renders server conflicts as text", () => {
        render(<Notice error={new Error("This item changed. Reload it.")}/>);
        expect(screen.getByRole("alert")).toHaveTextContent("Reload it");
    });
});
