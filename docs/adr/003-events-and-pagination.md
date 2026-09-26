# ADR 003: After-commit events and keyset pagination

Accepted. Persist activity in the business transaction; emit only after commit using a Spring transaction event
listener. In-process delivery is lossy across disconnect/restart; authoritative refetch handles gaps. A transactional
outbox is the next step when durable delivery or multiple instances is required. Task cursors encode project, sort, sort
value and unique ID; page size is 1–50. Concurrent edits that change sort values can move rows across page boundaries,
so event refresh restarts the displayed page. A workspace row lock protects membership changes and the last-owner
invariant; reassess lock contention with measured load.
