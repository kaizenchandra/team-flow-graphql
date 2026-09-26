# Verified dependency baseline

- Java 21 (actual local runtime 21.0.12.1), Spring Boot 4.1.1. Official stable/Java
  compatibility: https://docs.spring.io/spring-boot/system-requirements.html . Local Maven resolution, compilation and
  PostgreSQL integration tests succeeded.
- Spring GraphQL 2.0.5, GraphQL Java and Spring Data/Security versions follow Boot's BOM. Supported MVC WebSocket
  transport: https://docs.spring.io/spring-graphql/reference/transports.html . Actual graphql-transport-ws browser
  exchange verified.
- React 19.3.0, Vite 8.3.1, Apollo Client 4.3.1, graphql-ws 6.3.0 and TypeScript 6.0.3 resolved, pinned and
  production-built. Apollo WebSocket link: https://www.apollographql.com/docs/react/api/link/apollo-link-subscriptions .
- GraphQL Code Generator client preset 6.2.0 changed enum generation to `enumType: 'enum'`; checked against the official
  v6 migration notes: https://the-guild.dev/graphql/codegen/docs/migration/operations-and-client-preset-from-5-0 .
  Actual code generation/type-check succeeded.
- Docker image tags resolved and both production images built. Runtime PostgreSQL version 17.6; Flyway migration and
  Hibernate schema validation passed.

No Java or Boot downgrade was made. Exact frontend transitives are in package-lock.json; Maven supported dependency
versions are managed by Boot.
