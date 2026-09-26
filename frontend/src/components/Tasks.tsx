import type {FormEvent} from "react";
import {useEffect, useState} from "react";
import {useApolloClient, useMutation, useQuery} from "@apollo/client/react";
import * as G from "../gql/graphql";
import {Field, Modal, Notice, text, values} from "./ui";

export function TaskBoard({
                              project,
                              workspaceId,
                              select,
                          }: {
    project: G.HomeQuery["workspaces"][number]["projects"][number];
    workspaceId: string;
    select: (id: string) => void;
}) {
    const [status, setStatus] = useState<G.TaskStatus | "">("");
    const [priority, setPriority] = useState<G.Priority | "">("");
    const [assignee, setAssignee] = useState("");
    const [sort, setSort] = useState(G.TaskSort.CreatedAsc);
    const [create, setCreate] = useState(false);
    const members = useQuery(G.MembersDocument, {variables: {workspaceId}});
    const q = useQuery(G.TasksDocument, {
        variables: {
            projectId: project.id,
            filter: {
                status: status || undefined,
                priority: priority || undefined,
                assigneeId: assignee || undefined,
            },
            sort,
            first: 20,
        },
    });
    return (
        <>
            <div className="summary-row">
                <div>
                    <span className="muted">In this view</span>
                    <strong>{q.data?.tasks.nodes.length ?? "—"}</strong>
                    <small>tasks loaded</small>
                </div>
                <div>
                    <span className="muted">In progress</span>
                    <strong>
                        {q.data?.tasks.nodes.filter(
                            (t) => t.status === G.TaskStatus.InProgress,
                        ).length ?? "—"}
                    </strong>
                    <small>moving forward</small>
                </div>
                <div>
                    <span className="muted">Completed</span>
                    <strong>
                        {q.data?.tasks.nodes.filter((t) => t.status === G.TaskStatus.Done)
                            .length ?? "—"}
                    </strong>
                    <small>one step closer</small>
                </div>
            </div>
            <section className="task-section">
                <div className="section-title">
                    <h2>
                        Tasks{" "}
                        <span className="pill">{q.data?.tasks.nodes.length ?? 0}</span>
                    </h2>
                    <button className="primary" onClick={() => setCreate(true)}>
                        + New task
                    </button>
                </div>
                <div className="filters">
                    <Field label="Status">
                        <select
                            value={status}
                            onChange={(e) => setStatus(e.target.value as G.TaskStatus | "")}
                        >
                            <option value="">All statuses</option>
                            {Object.values(G.TaskStatus).map((v) => (
                                <option key={v} value={v}>
                                    {v.replace("_", " ")}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Priority">
                        <select
                            value={priority}
                            onChange={(e) => setPriority(e.target.value as G.Priority | "")}
                        >
                            <option value="">All priorities</option>
                            {Object.values(G.Priority).map((v) => (
                                <option key={v}>{v}</option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Assignee">
                        <select
                            value={assignee}
                            onChange={(e) => setAssignee(e.target.value)}
                        >
                            <option value="">Everyone</option>
                            {members.data?.members.map((m) => (
                                <option value={m.user.id} key={m.user.id}>
                                    {m.user.name}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Sort by">
                        <select
                            value={sort}
                            onChange={(e) => setSort(e.target.value as G.TaskSort)}
                        >
                            <option value={G.TaskSort.CreatedAsc}>Oldest created</option>
                            <option value={G.TaskSort.UpdatedDesc}>Recently updated</option>
                            <option value={G.TaskSort.PriorityDesc}>Highest priority</option>
                        </select>
                    </Field>
                </div>
                <Notice error={q.error}/>
                {q.error && (
                    <button onClick={() => void q.refetch()}>Retry tasks</button>
                )}
                {q.loading && <p role="status">Loading tasks…</p>}
                {!q.loading && q.data?.tasks.nodes.length === 0 && (
                    <div className="empty">
                        <h3>Room for your next idea.</h3>
                        <p>Add a task, or adjust your filters.</p>
                    </div>
                )}
                <div className="task-list">
                    {q.data?.tasks.nodes.map((t) => (
                        <button
                            key={t.id}
                            className="task-row"
                            onClick={() => select(t.id)}
                        >
              <span
                  className={
                      "task-check " + (t.status === G.TaskStatus.Done ? "done" : "")
                  }
              >
                {t.status === G.TaskStatus.Done ? "✓" : "○"}
              </span>
                            <span className="task-name">
                <strong>{t.title}</strong>
                <small>{t.dueDate ? `Due ${t.dueDate}` : "No due date"}</small>
              </span>
                            <span className={"badge " + t.status.toLowerCase()}>
                {t.status.replace("_", " ")}
              </span>
                            <span className={"priority " + t.priority.toLowerCase()}>
                ● {t.priority}
              </span>
                            <span className="assignee">
                {t.assignee?.name ?? "Unassigned"}
              </span>
                            <span>↗</span>
                        </button>
                    ))}
                </div>
                {q.data?.tasks.hasNextPage && (
                    <button
                        className="load-more"
                        disabled={q.loading}
                        onClick={() =>
                            void q.fetchMore({
                                variables: {after: q.data?.tasks.endCursor},
                                updateQuery: (prev, {fetchMoreResult: next}) => ({
                                    ...next,
                                    tasks: {
                                        ...next.tasks,
                                        nodes: [...prev.tasks.nodes, ...next.tasks.nodes],
                                    },
                                }),
                            })
                        }
                    >
                        Load more tasks
                    </button>
                )}
            </section>
            {create && (
                <Modal title="Create a task" close={() => setCreate(false)}>
                    <TaskForm
                        workspaceId={workspaceId}
                        projectId={project.id}
                        done={() => {
                            setCreate(false);
                            void q.refetch();
                        }}
                    />
                </Modal>
            )}
        </>
    );
}

export function TaskForm({
                             workspaceId,
                             projectId,
                             task,
                             done,
                         }: {
    workspaceId: string;
    projectId: string;
    task?: G.TaskFieldsFragment;
    done: () => void;
}) {
    const members = useQuery(G.MembersDocument, {variables: {workspaceId}});
    const [create] = useMutation(G.CreateTaskDocument);
    const [update] = useMutation(G.UpdateTaskDocument);
    const [error, setError] = useState<unknown>();
    const [busy, setBusy] = useState(false);
    const client = useApolloClient();

    async function submit(e: FormEvent<HTMLFormElement>) {
        const f = values(e);
        const input = {
            title: text(f, "title"),
            description: text(f, "description"),
            status: text(f, "status") as G.TaskStatus,
            priority: text(f, "priority") as G.Priority,
            assigneeId: text(f, "assignee") || null,
            dueDate: text(f, "due") || null,
        };
        setBusy(true);
        setError(undefined);
        try {
            if (task)
                await update({
                    variables: {
                        input: {...input, id: task.id, version: task.version},
                    },
                });
            else await create({variables: {input: {...input, projectId}}});
            await client.refetchQueries({include: "active"});
            done();
        } catch (e) {
            setError(e);
        } finally {
            setBusy(false);
        }
    }

    return (
        <form onSubmit={submit}>
            <Field label="Task title">
                <input
                    name="title"
                    required
                    maxLength={200}
                    defaultValue={task?.title}
                />
            </Field>
            <Field label="Description">
        <textarea
            name="description"
            rows={3}
            maxLength={10000}
            defaultValue={task?.description}
        />
            </Field>
            <div className="form-grid">
                <Field label="Task status">
                    <select
                        name="status"
                        defaultValue={task?.status ?? G.TaskStatus.Todo}
                    >
                        {Object.values(G.TaskStatus).map((v) => (
                            <option key={v}>{v}</option>
                        ))}
                    </select>
                </Field>
                <Field label="Task priority">
                    <select
                        name="priority"
                        defaultValue={task?.priority ?? G.Priority.Medium}
                    >
                        {Object.values(G.Priority).map((v) => (
                            <option key={v}>{v}</option>
                        ))}
                    </select>
                </Field>
                <Field label="Assign to">
                    <select name="assignee" defaultValue={task?.assignee?.id ?? ""}>
                        <option value="">Unassigned</option>
                        {members.data?.members.map((m) => (
                            <option key={m.user.id} value={m.user.id}>
                                {m.user.name}
                            </option>
                        ))}
                    </select>
                </Field>
                <Field label="Due date">
                    <input name="due" type="date" defaultValue={task?.dueDate ?? ""}/>
                </Field>
            </div>
            <Notice error={error}/>
            <button className="primary" disabled={busy}>
                {busy ? "Saving…" : task ? "Save changes" : "Create task"}
            </button>
            {Boolean(error) && task && (
                <p className="muted">
                    Your edits are preserved. Close and reopen this task to load the
                    latest version before retrying.
                </p>
            )}
        </form>
    );
}

export function TaskDetail({
                               id,
                               workspaceId,
                               close,
                           }: {
    id: string;
    workspaceId: string;
    close: () => void;
}) {
    const q = useQuery(G.DetailDocument, {variables: {id}});
    const [add] = useMutation(G.AddCommentDocument);
    const [remove] = useMutation(G.DeleteTaskDocument);
    const [error, setError] = useState<unknown>();
    const [message, setMessage] = useState("");
    const client = useApolloClient();
    const [snapshot, setSnapshot] = useState<G.TaskFieldsFragment>();
    const [confirm, setConfirm] = useState(false);
    useEffect(() => {
        if (q.data && !snapshot) setSnapshot(q.data.task);
    }, [q.data, snapshot]);
    return (
        <Modal title="Task details" close={close}>
            <Notice error={q.error ?? error}/>
            {q.loading && !q.data && <p>Loading task…</p>}
            {snapshot && (
                <>
                    <TaskForm
                        key={snapshot.version}
                        workspaceId={workspaceId}
                        projectId={snapshot.projectId}
                        task={snapshot}
                        done={() => {
                            setSnapshot(undefined);
                            setMessage("Changes saved.");
                        }}
                    />
                    {message && (
                        <p className="notice success" role="status">
                            {message}
                        </p>
                    )}
                    <section className="comments">
                        <h3>Conversation</h3>
                        {q.data?.comments.length === 0 && (
                            <p className="muted">Start the conversation.</p>
                        )}
                        {q.data?.comments.map((c) => (
                            <article key={c.id}>
                                <strong>{c.author.name}</strong>
                                <time>{new Date(c.createdAt).toLocaleString()}</time>
                                <p>{c.body}</p>
                            </article>
                        ))}
                        {(q.data?.comments.length ?? 0) >= 50 && (
                            <button
                                onClick={() =>
                                    void client
                                        .query({
                                            query: G.MoreCommentsDocument,
                                            variables: {id, after: q.data!.comments.at(-1)!.id},
                                        })
                                        .then((r) => {
                                            client.cache.updateQuery(
                                                {query: G.DetailDocument, variables: {id}},
                                                (old) =>
                                                    old && {
                                                        ...old,
                                                        comments: [
                                                            ...old.comments,
                                                            ...(r.data?.comments ?? []),
                                                        ],
                                                    },
                                            );
                                        })
                                }
                            >
                                Load older conversation entries
                            </button>
                        )}
                        <form
                            onSubmit={async (e) => {
                                const f = values(e);
                                const form = e.currentTarget;
                                try {
                                    await add({
                                        variables: {input: {taskId: id, body: text(f, "body")}},
                                    });
                                    form.reset();
                                    await q.refetch();
                                    setMessage("Comment added.");
                                } catch (e) {
                                    setError(e);
                                }
                            }}
                        >
                            <Field label="Add a comment">
                                <textarea name="body" rows={2} required maxLength={4000}/>
                            </Field>
                            <button>Post comment</button>
                        </form>
                    </section>
                    {confirm ? (
                        <div className="notice error">
                            Delete this task and its comments?
                            <button
                                onClick={async () => {
                                    try {
                                        await remove({
                                            variables: {input: {id, version: snapshot.version}},
                                        });
                                        await client.refetchQueries({
                                            include: [G.TasksDocument, G.ActivityDocument],
                                        });
                                        close();
                                    } catch (e) {
                                        setError(e);
                                    }
                                }}
                            >
                                Confirm delete
                            </button>
                            <button onClick={() => setConfirm(false)}>Cancel</button>
                        </div>
                    ) : (
                        <button className="danger" onClick={() => setConfirm(true)}>
                            Delete task
                        </button>
                    )}
                </>
            )}
        </Modal>
    );
}
