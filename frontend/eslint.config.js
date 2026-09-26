import js from "@eslint/js";
import ts from "typescript-eslint";
import hooks from "eslint-plugin-react-hooks";

export default ts.config(
    {
        ignores: [
            "dist/**",
            "src/gql/**",
            "playwright-report/**",
            "test-results/**",
        ],
    },
    js.configs.recommended,
    ...ts.configs.recommended,
    {
        files: ["**/*.{ts,tsx}"],
        plugins: {"react-hooks": hooks},
        rules: {
            ...hooks.configs.recommended.rules,
            "react-hooks/set-state-in-effect": "off",
            "react-hooks/refs": "off",
            "react-hooks/exhaustive-deps": "off",
        },
        languageOptions: {
            globals: {
                window: "readonly",
                document: "readonly",
                fetch: "readonly",
                console: "readonly",
                setTimeout: "readonly",
                URLSearchParams: "readonly",
                FormData: "readonly",
                HTMLFormElement: "readonly",
                HTMLInputElement: "readonly",
                WebSocket: "readonly",
            },
        },
    },
);
