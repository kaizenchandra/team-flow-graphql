import {type APIRequestContext, expect, test} from '@playwright/test';

async function csrf(request: APIRequestContext) {
    return (await request.get('/auth/csrf')).json();
}

async function signup(request: APIRequestContext, email: string) {
    let c = await csrf(request);
    expect((await request.post('/auth/register', {
        headers: {[c.headerName]: c.token},
        data: {email, name: 'Security test', password: 'TeamFlow-secure-test-2026'}
    })).status()).toBe(201);
    c = await csrf(request);
    expect((await request.post('/auth/login', {
        headers: {[c.headerName]: c.token},
        form: {email, password: 'TeamFlow-secure-test-2026'}
    })).ok()).toBeTruthy();
}

test('wire protocol denies unauthorized subscriptions and operations after logout', async ({browser}) => {
    const a = await browser.newContext(), b = await browser.newContext();
    const stamp = Date.now();
    await signup(a.request, `security-owner-${stamp}@example.test`);
    await signup(b.request, `security-other-${stamp}@example.test`);
    const c = await csrf(a.request);
    const created = await (await a.request.post('/graphql', {
        headers: {[c.headerName]: c.token},
        data: {query: 'mutation{createWorkspace(input:{name:"Restricted"}){workspace{id}}}'}
    })).json();
    const wid = created.data.createWorkspace.workspace.id;
    const page = await b.newPage();
    await page.goto('/');
    const messages = await page.evaluate(async (workspaceId) => new Promise<string[]>((resolve, reject) => {
        const received: string[] = [];
        const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/graphql`, 'graphql-transport-ws');
        const timer = setTimeout(() => {
            socket.close();
            reject(Error('No subscription authorization response'));
        }, 10000);
        socket.onopen = () => socket.send(JSON.stringify({type: 'connection_init'}));
        socket.onmessage = e => {
            const msg = JSON.parse(e.data);
            if (msg.type === 'connection_ack') socket.send(JSON.stringify({
                id: 'denied',
                type: 'subscribe',
                payload: {
                    query: 'subscription($id:ID!){workspaceChanges(workspaceId:$id){id entityId}}',
                    variables: {id: workspaceId}
                }
            })); else if (msg.type === 'next' || msg.type === 'error') {
                received.push(e.data);
                clearTimeout(timer);
                socket.close();
                resolve(received);
            }
        };
        socket.onerror = () => reject(Error('Unexpected handshake failure'));
    }), wid);
    expect(messages.join('')).toMatch(/error/i);
    expect(messages.join('')).not.toMatch(/"entityId":/);
    await page.evaluate(() => new Promise<void>((resolve, reject) => {
        const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/graphql`, 'graphql-transport-ws');
        const state = {socket, messages: [] as string[], closed: false};
        (window as unknown as { securitySocket: typeof state }).securitySocket = state;
        const timer = setTimeout(() => reject(Error('No connection ack')), 10000);
        socket.onopen = () => socket.send(JSON.stringify({type: 'connection_init'}));
        socket.onclose = () => {
            state.closed = true;
        };
        socket.onmessage = e => {
            const msg = JSON.parse(e.data);
            if (msg.type === 'connection_ack') {
                clearTimeout(timer);
                resolve();
            } else state.messages.push(e.data);
        };
    }));
    const logoutCsrf = await csrf(b.request);
    expect((await b.request.post('/auth/logout', {headers: {[logoutCsrf.headerName]: logoutCsrf.token}})).status()).toBe(204);
    await page.evaluate(() => {
        (window as unknown as {
            securitySocket: { socket: WebSocket }
        }).securitySocket.socket.send(JSON.stringify({
            id: 'afterLogout',
            type: 'subscribe',
            payload: {query: '{me{id email}}'}
        }));
    });
    await expect.poll(() => page.evaluate(() => {
        const state = (window as unknown as { securitySocket: { messages: string[]; closed: boolean } }).securitySocket;
        return state.closed || state.messages.length > 0;
    })).toBeTruthy();
    const after = await page.evaluate(() => (window as unknown as {
        securitySocket: { messages: string[] }
    }).securitySocket.messages);
    expect(after.join('')).not.toMatch(/"email":/);
    await a.close();
    await b.close();
});
