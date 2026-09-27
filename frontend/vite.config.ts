import {defineConfig} from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
    plugins: [react()],
    server: {
        watch: {ignored: ["**/test-results/**", "**/playwright-report/**"]},
        proxy: {
            "/graphql": {target: "http://localhost:8080", ws: true},
            "/auth": "http://localhost:8080",
            "/actuator": "http://localhost:8080",
        },
    },
    test: {
        environment: "jsdom",
        setupFiles: ["./src/test-setup.ts"],
        exclude: ["e2e/**", "node_modules/**"],
    },
});
