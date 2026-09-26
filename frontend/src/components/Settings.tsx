import {useState} from "react";
import {useApolloClient, useMutation, useQuery} from "@apollo/client/react";
import * as G from "../gql/graphql";
import {Field, Notice, text, values} from "./ui";

export function Settings({
                             workspace,
                         }: {
    workspace: G.HomeQuery["workspaces"][number];
}) {
    const q = useQuery(G.MembersDocument, {
        variables: {workspaceId: workspace.id},
    });
    const [set] = useMutation(G.SetMemberDocument);
    const [remove] = useMutation(G.RemoveMemberDocument);
    const [error, setError] = useState<unknown>();
    const [message, setMessage] = useState("");
    const client = useApolloClient();
    const manager = workspace.role !== G.Role.Member;

    async function run(fn: () => Promise<unknown>) {
        setError(undefined);
        try {
            await fn();
            await client.refetchQueries({include: "active"});
            setMessage("Membership updated.");
        } catch (e) {
            setError(e);
        }
    }

    return (
        <section>
            <div className="page-heading">
                <div>
                    <p className="eyebrow">BETTER TOGETHER</p>
                    <h1>Workspace settings</h1>
                    <p className="muted">
                        {workspace.name} · Your role: {workspace.role}
                    </p>
                </div>
            </div>
            <Notice error={error ?? q.error}/>
            {message && (
                <p role="status" className="notice success">
                    {message}
                </p>
            )}
            <div className="panel">
                <h2>People</h2>
                {q.loading && <p>Loading members…</p>}
                {q.data?.members.map((m) => (
                    <div className="member-row" key={m.user.id}>
                        <div>
                            <strong>{m.user.name}</strong>
                            <small>{m.user.email}</small>
                        </div>
                        {manager &&
                        !(workspace.role === G.Role.Admin && m.role === G.Role.Owner) ? (
                            <>
                                <select
                                    aria-label={`Role for ${m.user.name}`}
                                    value={m.role}
                                    onChange={(e) =>
                                        void run(() =>
                                            set({
                                                variables: {
                                                    input: {
                                                        workspaceId: workspace.id,
                                                        email: m.user.email,
                                                        role: e.target.value as G.Role,
                                                    },
                                                },
                                            }),
                                        )
                                    }
                                >
                                    {Object.values(G.Role)
                                        .filter(
                                            (r) =>
                                                workspace.role === G.Role.Owner || r !== G.Role.Owner,
                                        )
                                        .map((r) => (
                                            <option key={r}>{r}</option>
                                        ))}
                                </select>
                                <button
                                    className="danger"
                                    aria-label={`Remove ${m.user.name}`}
                                    onClick={() =>
                                        void run(() =>
                                            remove({
                                                variables: {
                                                    input: {
                                                        workspaceId: workspace.id,
                                                        userId: m.user.id,
                                                    },
                                                },
                                            }),
                                        )
                                    }
                                >
                                    Remove
                                </button>
                            </>
                        ) : (
                            <span className="badge">{m.role}</span>
                        )}
                    </div>
                ))}
                {manager && (
                    <form
                        onSubmit={(e) => {
                            const f = values(e);
                            void run(() =>
                                set({
                                    variables: {
                                        input: {
                                            workspaceId: workspace.id,
                                            email: text(f, "email"),
                                            role: text(f, "role") as G.Role,
                                        },
                                    },
                                }),
                            );
                        }}
                    >
                        <h3>Add an existing user</h3>
                        <div className="form-grid">
                            <Field label="Member email">
                                <input type="email" name="email" required/>
                            </Field>
                            <Field label="Member role">
                                <select name="role" defaultValue={G.Role.Member}>
                                    {Object.values(G.Role)
                                        .filter(
                                            (r) =>
                                                workspace.role === G.Role.Owner || r !== G.Role.Owner,
                                        )
                                        .map((r) => (
                                            <option key={r}>{r}</option>
                                        ))}
                                </select>
                            </Field>
                        </div>
                        <button className="primary">Add member</button>
                    </form>
                )}
            </div>
        </section>
    );
}
