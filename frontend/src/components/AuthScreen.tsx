import type {FormEvent} from "react";
import {useState} from "react";
import {auth} from "../client";
import {Field, Notice, text, values} from "./ui";

export function AuthScreen({onDone}: { onDone: () => void }) {
    const [register, setRegister] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<unknown>();

    async function submit(e: FormEvent<HTMLFormElement>) {
        const f = values(e);
        setBusy(true);
        setError(undefined);
        try {
            if (register)
                await auth("register", {
                    name: text(f, "name"),
                    email: text(f, "email"),
                    password: String(f.get("password")),
                });
            await auth("login", {
                email: text(f, "email"),
                password: String(f.get("password")),
            });
            onDone();
        } catch (e) {
            setError(e);
        } finally {
            setBusy(false);
        }
    }

    return (
        <main className="auth-layout">
            <section className="auth-story">
                <a className="brand" href="/">
                    ▦ TeamFlow
                </a>
                <div>
                    <p className="eyebrow">A shared space for good work</p>
                    <h1>
                        Less chasing.
                        <br/>
                        More creating.
                    </h1>
                    <p>
                        Bring your projects, people, and next steps together. Stay in sync
                        as the work moves forward.
                    </p>
                    <div className="story-stats">
                        <span>01 / Plan together</span>
                        <span>02 / Keep moving</span>
                        <span>03 / Make it happen</span>
                    </div>
                </div>
                <small>Built for teams. Focused on progress.</small>
            </section>
            <section className="auth-form">
                <p className="eyebrow">YOUR WORK, CONNECTED</p>
                <h2>{register ? "Make room for your team." : "Welcome back."}</h2>
                <p className="muted">
                    {register
                        ? "Create your account to get started."
                        : "Sign in and pick up where you left off."}
                </p>
                <form onSubmit={submit}>
                    {register && (
                        <Field label="Your name">
                            <input name="name" autoComplete="name" required maxLength={80}/>
                        </Field>
                    )}
                    <Field label="Email address">
                        <input
                            type="email"
                            name="email"
                            autoComplete="email"
                            required
                            maxLength={254}
                        />
                    </Field>
                    <Field label="Password">
                        <input
                            type="password"
                            name="password"
                            autoComplete={register ? "new-password" : "current-password"}
                            required
                            minLength={register ? 12 : 1}
                            maxLength={72}
                        />
                    </Field>
                    {register && <small>Use 12–72 characters.</small>}
                    <Notice error={error}/>
                    <button className="primary wide" disabled={busy}>
                        {busy ? "Please wait…" : register ? "Create account" : "Sign in"}{" "}
                        <span aria-hidden="true">→</span>
                    </button>
                </form>
                <button
                    className="link"
                    onClick={() => {
                        setRegister(!register);
                        setError(undefined);
                    }}
                >
                    {register
                        ? "Already have an account? Sign in"
                        : "New to TeamFlow? Create an account"}
                </button>
            </section>
        </main>
    );
}
