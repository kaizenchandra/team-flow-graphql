import {useQuery} from "@apollo/client/react";
import * as G from "../gql/graphql";
import {Notice} from "./ui";
import {useState} from "react";

export function Activity({workspaceId}: { workspaceId: string }) {
    const q = useQuery(G.ActivityDocument, {variables: {workspaceId}});
    const [hasMore, setHasMore] = useState(true);
    const [error, setError] = useState<unknown>();
    return (
        <section>
            <div className="page-heading">
                <div>
                    <p className="eyebrow">THE STORY SO FAR</p>
                    <h1>Activity</h1>
                    <p className="muted">A shared record of what moved forward.</p>
                </div>
            </div>
            <Notice error={q.error ?? error}/>
            {q.loading && <p>Loading activity…</p>}
            <div className="panel">
                {q.data?.activity.length === 0 && <p>No activity yet.</p>}
                {q.data?.activity.map((a) => (
                    <article className="activity-row" key={a.id}>
                        <span className="avatar">{a.actor.name.slice(0, 1)}</span>
                        <div>
                            <strong>{a.actor.name}</strong>
                            <p>{a.type.toLowerCase().replaceAll("_", " ")}</p>
                            <small>{new Date(a.createdAt).toLocaleString()}</small>
                        </div>
                    </article>
                ))}
                {hasMore && (q.data?.activity.length ?? 0) >= 30 && (
                    <button
                        onClick={() =>
                            void q.fetchMore({
                                variables: {after: q.data?.activity.at(-1)?.id},
                                updateQuery: (prev, {fetchMoreResult: next}) => ({
                                    activity: [...prev.activity, ...next.activity],
                                }),
                            }).then(result => setHasMore(result.data.activity.length > 0)).catch(setError)
                        }
                    >
                        Load more activity
                    </button>
                )}
            </div>
        </section>
    );
}
