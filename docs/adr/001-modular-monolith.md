# ADR 001: Modular monolith and blocking persistence

Accepted. Java 21 / Spring MVC with JPA offers one deployable unit and transactional invariants without speculative
distributed infrastructure. GraphQL handlers are transport adapters. Reactor is used only for subscription transport;
all JPA checks in that stream run on boundedElastic. Alternatives: WebFlux + blocking JPA was rejected because it
obscures blocking boundaries; microservices add coordination costs without demonstrated demand.
