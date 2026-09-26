import {EventWindow} from "../events";
import {useEffect, useRef, useState} from "react";
import {useApolloClient, useMutation, useSubscription,} from "@apollo/client/react";
import * as G from "../gql/graphql";
import {Field, Notice, text, values} from "./ui";
import {TaskBoard, TaskDetail} from "./Tasks";
import {Settings} from "./Settings";
import {Activity} from "./Activity";

export function Dashboard({
                              home,
                              refresh,
                              logout,
                          }: {
    home: G.HomeQuery;
    refresh: () => Promise<unknown>;
    logout: () => Promise<void>;
}) {
    const [wid, setWid] = useState("");
    const [pid, setPid] = useState("");
    const [view, setView] = useState<"work" | "settings" | "activity">("work");
    const [tid, setTid] = useState<string>();
    const [notice, setNotice] = useState("");
    const [error, setError] = useState<unknown>();
    const [connection, setConnection] = useState("Connecting");
    const workspace =
        home.workspaces.find((w) => w.id === wid) ?? home.workspaces[0];
    const project =
        workspace?.projects.find((p) => p.id === pid) ?? workspace?.projects[0];
    const client = useApolloClient();
    const [createWorkspace] = useMutation(G.CreateWorkspaceDocument);
    const [createProject] = useMutation(G.CreateProjectDocument);
    const seen = useRef(new EventWindow());
    useEffect(() => {
        seen.current.clear();
    }, [workspace?.id]);
    useSubscription(G.ChangesDocument, {
        variables: {workspaceId: workspace?.id ?? ""},
        skip: !workspace,
        onData: ({data}) => {
            const event = data.data?.workspaceChanges;
            if (!event || !seen.current.accept(event.id)) return;
            void client.refetchQueries({include: "active"});
        },
        onError: (e) => {
            setConnection("Disconnected");
            setError(e);
            void refresh();
        },
    });
    useEffect(() => {
        const change = (e: Event) =>
            setConnection((e as CustomEvent<string>).detail);
        window.addEventListener("teamflow-connection", change);
        return () => window.removeEventListener("teamflow-connection", change);
    }, []);

    async function action(fn: () => Promise<unknown>, message: string) {
        setError(undefined);
        try {
            await fn();
            await refresh();
            setNotice(message);
        } catch (e) {
            setError(e);
        }
    }

    return (
        <div className="app-shell">
            <aside className="sidebar">
                <a href="/" className="brand">
                    ▦ TeamFlow
                </a>
                <p className="sidebar-label">WORKSPACE</p>
                <label className="sr-only" htmlFor="workspace">
                    Workspace
                </label>
                <select
                    id="workspace"
                    value={workspace?.id ?? ""}
                    onChange={(e) => {
                        setWid(e.target.value);
                        setPid("");
                        setTid(undefined);
                        setView("work");
                    }}
                >
                    {home.workspaces.map((w) => (
                        <option key={w.id} value={w.id}>
                            {w.name}
                        </option>
                    ))}
                </select>
                <nav aria-label="Main navigation">
                    <button
                        aria-label="Projects"
                        className={view === "work" ? "active" : ""}
                        onClick={() => setView("work")}
                    >
                        ▦ &nbsp; Projects
                    </button>
                    <button
                        aria-label="Activity"
                        className={view === "activity" ? "active" : ""}
                        onClick={() => setView("activity")}
                    >
                        ◷ &nbsp; Activity
                    </button>
                    <button
                        aria-label="Workspace settings"
                        className={view === "settings" ? "active" : ""}
                        onClick={() => setView("settings")}
                    >
                        ⚙ &nbsp; Workspace settings
                    </button>
                </nav>
                <div className="sidebar-projects">
                    <p className="sidebar-label">
                        YOUR PROJECTS <span>{workspace?.projects.length ?? 0}</span>
                    </p>
                    {workspace?.projects.map((p) => (
                        <button
                            className={
                                project?.id === p.id ? "project-link selected" : "project-link"
                            }
                            key={p.id}
                            onClick={() => {
                                setPid(p.id);
                                setView("work");
                                setTid(undefined);
                            }}
                        >
                            ◈ &nbsp; {p.name}
                        </button>
                    ))}
                </div>
                <details>
                    <summary>New workspace</summary>
                    <form
                        onSubmit={(e) => {
                            const f = values(e);
                            const form = e.currentTarget;
                            void action(async () => {
                                const r = await createWorkspace({
                                    variables: {input: {name: text(f, "name")}},
                                });
                                setWid(r.data?.createWorkspace.workspace.id ?? "");
                                setPid("");
                                form.reset();
                                const details = form.closest("details");
                                if (details) details.open = false;
                            }, "Workspace created.");
                        }}
                    >
                        <Field label="Workspace name">
                            <input name="name" required maxLength={120}/>
                        </Field>
                        <button className="primary">Create workspace</button>
                    </form>
                </details>
                <div className="profile">
                    <span className="avatar">{home.me.name.slice(0, 1)}</span>
                    <div>
                        <strong>{home.me.name}</strong>
                        <small>{home.me.email}</small>
                    </div>
                    <button
                        aria-label="Sign out"
                        title="Sign out"
                        onClick={() => void action(logout, "Signed out")}
                    >
                        ↪
                    </button>
                </div>
            </aside>
            <main className="main">
                <header className="topbar">
          <span>
            {workspace?.name ?? "Your workspace"}{" "}
              <span className="muted">
              /{" "}
                  {view === "work"
                      ? "Projects"
                      : view === "settings"
                          ? "Settings"
                          : "Activity"}
            </span>
          </span>
                    <span className="live">
            <i className={connection === "Live" ? "online" : ""}/>
                        {connection}
                        {connection === "Disconnected" && (
                            <button onClick={() => window.location.reload()}>
                                Reconnect
                            </button>
                        )}
          </span>
                </header>
                <Notice error={error}/>
                {notice && (
                    <p role="status" className="notice success">
                        {notice}
                        <button
                            aria-label="Dismiss notification"
                            onClick={() => setNotice("")}
                        >
                            ×
                        </button>
                    </p>
                )}
                {!workspace ? (
                    <section className="empty">
                        <p className="eyebrow">LET’S GET STARTED</p>
                        <h1>Your team’s next chapter.</h1>
                        <p>
                            Create a workspace using the sidebar, then invite your
                            collaborators.
                        </p>
                    </section>
                ) : view === "settings" ? (
                    <Settings workspace={workspace}/>
                ) : view === "activity" ? (
                    <Activity workspaceId={workspace.id}/>
                ) : (
                    <>
                        <div className="page-heading">
                            <div>
                                <p className="eyebrow">LET’S MOVE WORK FORWARD</p>
                                <h1>{project?.name ?? "Projects"}</h1>
                                <p className="muted">A little clarity. A lot of progress.</p>
                                {workspace.projects.length > 1 && (
                                    <Field label="Project">
                                        <select
                                            value={project?.id ?? ""}
                                            onChange={(e) => {
                                                setPid(e.target.value);
                                                setTid(undefined);
                                            }}
                                        >
                                            {workspace.projects.map((p) => (
                                                <option key={p.id} value={p.id}>
                                                    {p.name}
                                                </option>
                                            ))}
                                        </select>
                                    </Field>
                                )}
                            </div>
                            {workspace.role !== G.Role.Member && (
                                <details className="create-project">
                                    <summary>+ New project</summary>
                                    <form
                                        onSubmit={(e) => {
                                            const f = values(e);
                                            const form = e.currentTarget;
                                            void action(async () => {
                                                const r = await createProject({
                                                    variables: {
                                                        input: {
                                                            workspaceId: workspace.id,
                                                            name: text(f, "name"),
                                                        },
                                                    },
                                                });
                                                setPid(r.data?.createProject.project.id ?? "");
                                                form.reset();
                                                const details = form.closest("details");
                                                if (details) details.open = false;
                                            }, "Project created.");
                                        }}
                                    >
                                        <Field label="Project name">
                                            <input name="name" required maxLength={120}/>
                                        </Field>
                                        <button className="primary">Create project</button>
                                    </form>
                                </details>
                            )}
                        </div>
                        {project ? (
                            <TaskBoard
                                key={project.id}
                                project={project}
                                workspaceId={workspace.id}
                                select={setTid}
                            />
                        ) : (
                            <section className="empty">
                                <h2>A fresh start.</h2>
                                <p>
                                    {workspace.role === G.Role.Member
                                        ? "Ask an owner or admin to create a project."
                                        : "Create your first project to start organizing the work."}
                                </p>
                            </section>
                        )}
                    </>
                )}
                {tid && workspace && (
                    <TaskDetail
                        id={tid}
                        workspaceId={workspace.id}
                        close={() => setTid(undefined)}
                    />
                )}
            </main>
        </div>
    );
}
