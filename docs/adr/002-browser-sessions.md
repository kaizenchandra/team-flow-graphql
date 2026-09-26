# ADR 002: Same-origin sessions

Accepted. Framework-managed sessions and CSRF protect the browser application without local-storage tokens. Cookies are
HttpOnly, SameSite=Strict, and Secure when configured for HTTPS. Logout and expired sessions terminate subscription
delivery. Session references and login rate limits are local and bounded; multi-instance deployment requires shared
sessions/limits. Default accounts are deliberately absent; browser tests create development-only test accounts in the
test database.
