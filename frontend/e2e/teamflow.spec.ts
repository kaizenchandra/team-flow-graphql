import {type APIRequestContext, expect, type Page, test,} from "@playwright/test";

const password = "TeamFlow-test-password-2026";

async function register(page: Page, name: string, email: string) {
    await page.goto("/");
    await page.getByText("New to TeamFlow? Create an account").click();
    await page.getByLabel("Your name").fill(name);
    await page.getByLabel("Email address").fill(email);
    await page.getByLabel("Password", {exact: true}).fill(password);
    await page
        .getByRole("button", {name: "Create account", exact: true})
        .click();
    await expect(page.getByRole("button", {name: "Sign out"})).toBeVisible();
}

async function gql(
    request: APIRequestContext,
    query: string,
    variables: Record<string, unknown> = {},
) {
    const csrf = await (await request.get("/auth/csrf")).json();
    const response = await request.post("/graphql", {
        headers: {[csrf.headerName]: csrf.token},
        data: {query, variables},
    });
    expect(response.ok()).toBeTruthy();
    return response.json();
}

test("complete workflow, independent browsers, reconnect and revoked membership", async ({
                                                                                             browser,
                                                                                         }) => {
    const a = await browser.newContext();
    const b = await browser.newContext();
    await b.addInitScript(() => {
        const original = window.WebSocket;
        const sockets: WebSocket[] = [];
        (window as unknown as { testSockets: WebSocket[] }).testSockets = sockets;
        window.WebSocket = class extends original {
            constructor(url: string | URL, protocols?: string | string[]) {
                super(url, protocols);
                sockets.push(this);
            }
        };
    });
    const pa = await a.newPage();
    const pb = await b.newPage();
    const suffix = Date.now();
    const owner = `owner-${suffix}@example.test`;
    const member = `member-${suffix}@example.test`;
    await register(pb, "Morgan Lee", member);
    await register(pa, "Alex Rivers", owner);
    await pa.getByText("New workspace", {exact: true}).click();
    await pa.getByLabel("Workspace name").fill("Product Studio");
    await pa
        .getByRole("button", {name: "Create workspace", exact: true})
        .click();
    await expect(pa.getByLabel("Workspace", {exact: true})).toContainText(
        "Product Studio",
    );
    await pa.getByRole("button", {name: "Workspace settings"}).click();
    await pa.getByLabel("Member email").fill(member);
    await pa.getByRole("button", {name: "Add member", exact: true}).click();
    await expect(pa.getByText(member, {exact: true})).toBeVisible();
    await pa.getByRole("button", {name: "Projects", exact: true}).click();
    await pa.getByText("+ New project", {exact: true}).click();
    await pa.getByLabel("Project name").fill("Launch readiness");
    await pa.getByRole("button", {name: "Create project", exact: true}).click();
    await expect(
        pa.getByRole("heading", {name: "Launch readiness"}),
    ).toBeVisible();
    await pb.reload();
    await expect(
        pb.getByRole("heading", {name: "Launch readiness"}),
    ).toBeVisible();
    await expect(pb.getByText("Live", {exact: true})).toBeVisible();
    await pa.getByRole("button", {name: "+ New task"}).click();
    await pa.getByLabel("Task title").fill("Validate release checklist");
    await pa
        .getByLabel("Description", {exact: true})
        .fill("Review migrations and rollback with the team.");
    await pa.getByLabel("Task priority").selectOption("HIGH");
    await pa.getByLabel("Assign to").selectOption({label: "Morgan Lee"});
    await pa.getByRole("button", {name: "Create task", exact: true}).click();
    await expect(
        pb.getByRole("button", {name: /Validate release checklist/}),
    ).toBeVisible();
    await pa.getByRole("button", {name: /Validate release checklist/}).click();
    await pb.getByRole("button", {name: /Validate release checklist/}).click();
    await pa.getByLabel("Task title").fill("Release checklist reviewed");
    await pa.getByRole("button", {name: "Save changes"}).click();
    await expect(pa.getByText("Changes saved.", {exact: true})).toBeVisible();
    // B retains its editing snapshot and receives a useful conflict rather than silently overwriting A.
    await pb.getByLabel("Description", {exact: true}).fill("Stale edit");
    await pb.getByRole("button", {name: "Save changes"}).click();
    await expect(pb.getByRole("alert")).toContainText("changed");
    await pb.getByRole("button", {name: "Close dialog"}).click();
    await pb.getByRole("button", {name: /Release checklist reviewed/}).click();
    await pa.getByLabel("Add a comment").fill("Ready for review.");
    await pa.getByRole("button", {name: "Post comment"}).click();
    await expect(
        pb.getByText("Ready for review.", {exact: true}),
    ).toBeVisible();
    await pa.getByRole("button", {name: "Close dialog"}).click();
    await pb.getByRole("button", {name: "Close dialog"}).click();
    const home = await gql(a.request, "{workspaces{id projects{id}}}");
    const wid = home.data.workspaces[0].id;
    const pid = home.data.workspaces[0].projects[0].id;
    // Terminate B's actual socket by taking its browser context offline, then mutate persisted state.
    await b.setOffline(true);
    await pb.evaluate(() => {
        for (const socket of (window as unknown as { testSockets: WebSocket[] })
            .testSockets)
            if (socket.readyState === WebSocket.OPEN)
                socket.close(4205, "test reconnect");
    });
    await expect(pb.getByText("Reconnecting", {exact: true})).toBeVisible();
    await pa.getByRole("button", {name: "+ New task"}).click();
    await pa.getByLabel("Task title").fill("Created during disconnect");
    await pa.getByRole("button", {name: "Create task", exact: true}).click();
    await expect(
        pa.getByRole("button", {name: /Created during disconnect/}),
    ).toBeVisible();
    await expect(
        pb.getByRole("button", {name: /Created during disconnect/}),
    ).toHaveCount(0);
    await b.setOffline(false);
    await expect(
        pb.getByRole("button", {name: /Created during disconnect/}),
    ).toBeVisible({timeout: 30000});
    await pa.screenshot({
        path: "test-results/dashboard-desktop.png",
        fullPage: true,
    });
    await pa.setViewportSize({width: 390, height: 844});
    await pa.screenshot({
        path: "test-results/dashboard-mobile.png",
        fullPage: true,
    });
    expect(
        await pa.evaluate(
            () => document.documentElement.scrollWidth <= window.innerWidth,
        ),
    ).toBeTruthy();
    await pa.setViewportSize({width: 1280, height: 800});
    await pa.getByRole("button", {name: "Workspace settings"}).click();
    await pa.getByRole("button", {name: "Remove Morgan Lee"}).click();
    await expect(
        pb.getByRole("heading", {name: "Your team’s next chapter."}),
    ).toBeVisible();
    const denied = await gql(
        b.request,
        "query($id:ID!){tasks(projectId:$id){nodes{id}}}",
        {id: pid},
    );
    expect(denied.errors[0].extensions.code).toBe("FORBIDDEN");
    await pa.getByRole("button", {name: "Activity", exact: true}).click();
    await expect(
        pa.getByText("task created", {exact: true}).first(),
    ).toBeVisible();
    await pa.getByRole("button", {name: "Sign out"}).click();
    await expect(
        pa.getByRole("heading", {name: "Welcome back."}),
    ).toBeVisible();
    await pa.getByLabel("Email address").fill(owner);
    await pa.getByLabel("Password", {exact: true}).fill(password);
    await pa.getByRole("button", {name: "Sign in", exact: true}).click();
    await expect(pa.getByRole("button", {name: "Sign out"})).toBeVisible();
    const persisted = await gql(
        a.request,
        "query($id:ID!){tasks(projectId:$id){nodes{id title}}}",
        {id: pid},
    );
    expect(persisted.data.tasks.nodes).toHaveLength(2);
    expect(wid).toBeTruthy();
    await a.close();
    await b.close();
});
