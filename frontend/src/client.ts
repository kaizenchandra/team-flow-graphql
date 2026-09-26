import {ApolloClient, ApolloLink, HttpLink, InMemoryCache, split,} from "@apollo/client";
import {GraphQLWsLink} from "@apollo/client/link/subscriptions";
import {getMainDefinition} from "@apollo/client/utilities";
import {createClient} from "graphql-ws";

let csrf: { token: string; headerName: string } | undefined;

export async function token() {
    const res = await fetch("/auth/csrf");
    if (!res.ok) throw Error("Unable to connect. Try again.");
    csrf = await res.json();
    return csrf!;
}

export async function auth(path: string, body?: object) {
    const c = await token();
    const form = path === "login";
    const res = await fetch("/auth/" + path, {
        method: "POST",
        headers: {
            [c.headerName]: c.token,
            "Content-Type": form
                ? "application/x-www-form-urlencoded"
                : "application/json",
        },
        body: body
            ? form
                ? new URLSearchParams(body as Record<string, string>).toString()
                : JSON.stringify(body)
            : undefined,
    });
    if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw Error(
            data.message ??
            (res.status === 429
                ? "Too many attempts. Wait one minute."
                : "Unable to sign in. Check your details."),
        );
    }
    csrf = undefined;
}

export function makeClient() {
    let connected = false;
    const ws = createClient({
        url: () =>
            `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/graphql`,
        lazy: true,
        retryAttempts: 8,
        retryWait: async (attempt) => {
            await new Promise((r) =>
                setTimeout(
                    r,
                    Math.min(1000 * 2 ** attempt, 15000) + Math.random() * 300,
                ),
            );
        },
        on: {
            connected: () => {
                window.dispatchEvent(
                    new CustomEvent("teamflow-connection", {detail: "Live"}),
                );
                if (connected) void client.refetchQueries({include: "active"});
                connected = true;
            },
            closed: () =>
                window.dispatchEvent(
                    new CustomEvent("teamflow-connection", {detail: "Reconnecting"}),
                ),
        },
    });
    const transport = new HttpLink({
        uri: "/graphql",
        fetch: async (uri, options) => {
            const c = csrf ?? (await token());
            const response = await fetch(uri, {
                ...options,
                headers: {...options?.headers, [c.headerName]: c.token},
            });
            if (response.status === 401)
                window.dispatchEvent(new Event("teamflow-expired"));
            return response;
        },
    });
    const link = split(
        ({query}) => {
            const d = getMainDefinition(query);
            return d.kind === "OperationDefinition" && d.operation === "subscription";
        },
        new GraphQLWsLink(ws),
        transport,
    );
    const client = new ApolloClient({
        link: ApolloLink.from([link]),
        cache: new InMemoryCache(),
        defaultOptions: {watchQuery: {fetchPolicy: "cache-and-network"}},
    });
    return {
        client,
        dispose: () => {
            void ws.dispose();
            client.stop();
        },
    };
}
