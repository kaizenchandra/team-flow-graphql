import type {FormEvent, ReactElement, ReactNode} from "react";
import {cloneElement, useEffect, useId} from "react";

export function errorText(error: unknown) {
    return error instanceof Error
        ? error.message
        : "Something went wrong. Please try again.";
}

export function Notice({error}: { error: unknown }) {
    return error ? (
        <p className="notice error" role="alert">
            {errorText(error)}
        </p>
    ) : null;
}

export function Field({
                          label,
                          children,
                      }: {
    label: string;
    children: ReactElement<{ id?: string }>;
}) {
    const id = useId();
    return (
        <div className="field">
            <label htmlFor={id}>{label}</label>
            {cloneElement(children, {id})}
        </div>
    );
}

export function values(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    return new FormData(e.currentTarget);
}

export function text(f: FormData, key: string) {
    return String(f.get(key) ?? "").trim();
}

export function Modal({
                          title,
                          close,
                          children,
                      }: {
    title: string;
    close: () => void;
    children: ReactNode;
}) {
    useEffect(() => {
        const before = document.activeElement as HTMLElement | null;
        const dialog = document.querySelector('[role="dialog"]') as HTMLElement;
        const focus = () =>
            Array.from(
                dialog.querySelectorAll<HTMLElement>(
                    'button,input,select,textarea,[tabindex="0"]',
                ),
            ).filter((el) => !el.hasAttribute("disabled"));
        focus()[0]?.focus();
        const key = (e: KeyboardEvent) => {
            if (e.key === "Escape") close();
            if (e.key === "Tab") {
                const all = focus();
                if (e.shiftKey && document.activeElement === all[0]) {
                    e.preventDefault();
                    all.at(-1)?.focus();
                } else if (!e.shiftKey && document.activeElement === all.at(-1)) {
                    e.preventDefault();
                    all[0]?.focus();
                }
            }
        };
        document.addEventListener("keydown", key);
        return () => {
            document.removeEventListener("keydown", key);
            before?.focus();
        };
    }, []);
    return (
        <div className="modal-backdrop">
            <section
                className="modal"
                role="dialog"
                aria-modal="true"
                aria-label={title}
            >
                <div className="section-title">
                    <h2>{title}</h2>
                    <button aria-label="Close dialog" onClick={close}>
                        ×
                    </button>
                </div>
                {children}
            </section>
        </div>
    );
}
