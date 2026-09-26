import {useEffect, useMemo, useState} from "react";
import {ApolloProvider, useQuery} from "@apollo/client/react";
import {auth, makeClient} from "./client";
import * as G from "./gql/graphql";
import {AuthScreen} from "./components/AuthScreen";
import {Dashboard} from "./components/Dashboard";

export function App() {
    const [generation, setGeneration] = useState(0);
    const api = useMemo(() => {
        void generation;
        return makeClient();
    }, [generation]);
    useEffect(() => () => api.dispose(), [api]);
    return (
        <ApolloProvider client={api.client}>
            <Session key={generation} reset={() => setGeneration((n) => n + 1)}/>
        </ApolloProvider>
    );
}

function Session({reset}: { reset: () => void }) {
    const home = useQuery(G.HomeDocument);
    const [expired, setExpired] = useState(false);
    useEffect(() => {
        const expire = () => setExpired(true);
        window.addEventListener("teamflow-expired", expire);
        return () => window.removeEventListener("teamflow-expired", expire);
    }, []);
    if (home.loading && !home.data)
        return (
            <main className="welcome">
                <p>Connecting to TeamFlow…</p>
            </main>
        );
    if (!home.data || expired) return <AuthScreen onDone={reset}/>;
    return (
        <Dashboard
            home={home.data}
            refresh={() => home.refetch()}
            logout={async () => {
                await auth("logout");
                reset();
            }}
        />
    );
}
