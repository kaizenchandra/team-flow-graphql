package com.teamflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.teamflow.identity.*;
import com.teamflow.platform.*;
import com.teamflow.platform.Api.*;
import com.teamflow.work.*;
import com.teamflow.workspace.*;
import jakarta.persistence.EntityManagerFactory;

import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.test.tester.WebGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.generate_statistics=true"
)
@AutoConfigureMockMvc
@Testcontainers
class IntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            "postgres:17.6-alpine"
    );
    @Autowired
    IdentityService identity;
    @Autowired
    WorkspaceService spaces;
    @Autowired
    WorkService work;
    @Autowired
    MockMvc mvc;
    @Autowired
    WebGraphQlHandler handler;
    @Autowired
    EntityManagerFactory emf;
    @Autowired
    SubscriptionController subscriptions;
    @Autowired
    Events events;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager tx;
    String owner, outsider;
    User member;
    Workspace space;
    Project project;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    org.springframework.mock.web.MockHttpSession session(String email) {
        var session = new org.springframework.mock.web.MockHttpSession();
        var context =
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        email,
                        "",
                        List.of()
                )
        );
        session.setAttribute(
                org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        return session;
    }

    @Test
    void unauthorizedSubscriptionReceivesNoEvents() {
        reactor.test.StepVerifier.create(
                        subscriptions.workspaceChanges(space.id(), session(outsider))
                )
                .expectError(Problem.class)
                .verify(java.time.Duration.ofSeconds(5));
    }

    @Test
    void revokedMembershipAndInvalidatedSessionTerminateSubscriptions() {
        spaces.setMember(owner, new MemberInput(space.id(), outsider, Role.MEMBER));
        var session = session(outsider);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var received = new java.util.concurrent.CopyOnWriteArrayList<ChangeEvent>();
        var subscription = subscriptions
                .workspaceChanges(space.id(), session)
                .subscribe(received::add, failure::set);
        spaces.removeMember(owner, new RemoveMemberInput(space.id(), member.id()));
        create("Restricted after removal");
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(5))
                .until(() -> failure.get() != null);
        assertThat(received).isEmpty();
        subscription.dispose();
        var invalid = session(owner);
        invalid.invalidate();
        reactor.test.StepVerifier.create(
                        subscriptions.workspaceChanges(space.id(), invalid)
                )
                .expectError(Problem.class)
                .verify(java.time.Duration.ofSeconds(5));
    }

    @Test
    void rolledBackChangesNeverPublish() {
        var received = new java.util.concurrent.CopyOnWriteArrayList<ChangeEvent>();
        var subscription = events
                .stream()
                .filter(e -> e.workspaceId().equals(space.id()))
                .subscribe(received::add);
        new org.springframework.transaction.support.TransactionTemplate(
                tx
        ).executeWithoutResult(status -> {
            create("Will roll back");
            status.setRollbackOnly();
        });
        assertThat(received).isEmpty();
        var saved = create("Committed");
        assertThat(received).hasSize(1);
        assertThat(received.getFirst().entityId()).isEqualTo(saved.id());
        assertThat(
                work
                        .tasks(owner, project.id(), null, TaskSort.CREATED_ASC, 20, null)
                        .nodes()
        ).hasSize(1);
        subscription.dispose();
    }

    @Test
    void oversizedRequestsAndComplexityAreRejected() throws Exception {
        mvc
                .perform(
                        post("/graphql")
                                .with(user(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("x".repeat(65537))
                )
                .andExpect(status().isPayloadTooLarge());
        var query =
                "{" +
                        java.util.stream.IntStream.range(0, 251)
                                .mapToObj(i -> "x" + i + ":me{id}")
                                .collect(java.util.stream.Collectors.joining(" ")) +
                        "}";
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        owner,
                        "",
                        List.of()
                )
        );
        WebGraphQlTester.builder(handler)
                .build()
                .document(query)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @BeforeEach
    void setup() {
        owner = UUID.randomUUID() + "@test.example";
        outsider = UUID.randomUUID() + "@test.example";
        identity.register(owner, "Owner", "A-long-test-password");
        member = identity.register(outsider, "Other", "A-long-test-password");
        space = spaces.create(owner, new WorkspaceInput("Team"));
        project = work.createProject(
                owner,
                new ProjectInput(space.id(), "Product")
        );
    }

    Task create(String title) {
        return work.createTask(
                owner,
                new TaskInput(
                        project.id(),
                        title,
                        "Description",
                        TaskStatus.TODO,
                        Priority.HIGH,
                        null,
                        null
                )
        );
    }

    @Test
    void persistenceIsolationAndOptimisticConflict() {
        var t = create("First");
        assertThat(work.task(owner, t.id()).title()).isEqualTo("First");
        assertThatThrownBy(() -> work.task(outsider, t.id())).isInstanceOf(
                Problem.class
        );
        assertThatThrownBy(() ->
                work.comments(outsider, t.id(), 20, null)
        ).isInstanceOf(Problem.class);
        assertThatThrownBy(() ->
                work.createTask(
                        outsider,
                        new TaskInput(
                                project.id(),
                                "Injected",
                                "",
                                TaskStatus.TODO,
                                Priority.LOW,
                                null,
                                null
                        )
                )
        ).isInstanceOf(Problem.class);
        var update = new UpdateTaskInput(
                t.id(),
                t.version(),
                "Edited",
                "",
                TaskStatus.DONE,
                Priority.LOW,
                null,
                null
        );
        assertThat(work.updateTask(owner, update).version()).isGreaterThan(
                t.version()
        );
        assertThatThrownBy(() -> work.updateTask(owner, update))
                .isInstanceOf(Problem.class)
                .hasMessageContaining("changed");
        work.addComment(owner, new CommentInput(t.id(), "Persisted comment"));
        assertThat(work.comments(owner, t.id(), 20, null)).hasSize(1);
        assertThat(work.activity(owner, space.id(), 50, null)).hasSize(4);
    }

    @Test
    void paginationAndAssigneeValidation() {
        for (int i = 0; i < 5; i++) create("Task " + i);
        for (TaskSort sort : TaskSort.values()) {
            var a = work.tasks(owner, project.id(), null, sort, 2, null);
            var b = work.tasks(owner, project.id(), null, sort, 2, a.endCursor());
            assertThat(a.hasNextPage()).isTrue();
            assertThat(a.nodes())
                    .extracting(Task::id)
                    .doesNotContainAnyElementsOf(b.nodes().stream().map(Task::id).toList());
        }
        assertThatThrownBy(() ->
                work.tasks(owner, project.id(), null, TaskSort.CREATED_ASC, 51, null)
        ).isInstanceOf(Problem.class);
        assertThatThrownBy(() ->
                work.createTask(
                        owner,
                        new TaskInput(
                                project.id(),
                                "Bad",
                                "",
                                TaskStatus.TODO,
                                Priority.LOW,
                                member.id(),
                                null
                        )
                )
        ).isInstanceOf(Problem.class);
    }

    @Test
    void ownerInvariantAndAssignmentCleanup() {
        spaces.setMember(owner, new MemberInput(space.id(), outsider, Role.MEMBER));
        var t = work.createTask(
                owner,
                new TaskInput(
                        project.id(),
                        "Assigned",
                        "",
                        TaskStatus.TODO,
                        Priority.LOW,
                        member.id(),
                        null
                )
        );
        spaces.removeMember(owner, new RemoveMemberInput(space.id(), member.id()));
        assertThat(work.task(owner, t.id()).assigneeId()).isNull();
        assertThatThrownBy(() ->
                spaces.removeMember(
                        owner,
                        new RemoveMemberInput(space.id(), identity.me(owner).id())
                )
        ).isInstanceOf(Problem.class);
    }

    @Test
    void concurrentOwnerDemotionsLeaveOneOwner() throws Exception {
        spaces.setMember(owner, new MemberInput(space.id(), outsider, Role.OWNER));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var futures = List.of(owner, outsider)
                    .stream()
                    .map(email ->
                            pool.submit(() -> {
                                gate.await();
                                try {
                                    spaces.setMember(
                                            email,
                                            new MemberInput(space.id(), email, Role.MEMBER)
                                    );
                                    return true;
                                } catch (Problem e) {
                                    return false;
                                }
                            })
                    )
                    .toList();
            gate.countDown();
            int successes = 0;
            for (var f : futures) if (f.get(10, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(
                    spaces
                            .members(owner, space.id())
                            .stream()
                            .filter(m -> m.role() == Role.OWNER)
            ).hasSize(1);
        }
    }

    @Test
    void httpSecurityCsrfAndOrigin() throws Exception {
        mvc
                .perform(
                        post("/graphql")
                                .contentType("application/json")
                                .content("{\"query\":\"{me{id}}\"}")
                )
                .andExpect(status().isForbidden());
        mvc
                .perform(
                        post("/graphql")
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"query\":\"{me{id}}\"}")
                )
                .andExpect(status().isUnauthorized());
        mvc
                .perform(
                        post("/graphql")
                                .with(user(owner))
                                .with(csrf())
                                .header("Origin", "https://evil.example")
                                .contentType("application/json")
                                .content("{\"query\":\"{me{id}}\"}")
                )
                .andExpect(status().isForbidden());
        mvc
                .perform(
                        post("/graphql")
                                .with(user(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"query\":\"{me{email}}\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.me.email").value(owner));
    }

    @Test
    void graphqlErrorsAndNestedBatching() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        owner,
                        "",
                        List.of()
                )
        );
        var tester = WebGraphQlTester.builder(handler).build();
        tester
                .document("query($id:ID!){tasks(projectId:$id,first:51){nodes{id}}}")
                .variable("id", project.id())
                .execute()
                .errors()
                .satisfy(errors ->
                        assertThat(errors.getFirst().getExtensions().get("code")).isEqualTo(
                                "BAD_INPUT"
                        )
                );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        outsider,
                        "",
                        List.of()
                )
        );
        var other = WebGraphQlTester.builder(handler).build();
        other
                .document("query($id:ID!){projects(workspaceId:$id){id}}")
                .variable("id", space.id())
                .execute()
                .errors()
                .satisfy(errors ->
                        assertThat(errors.getFirst().getExtensions().get("code")).isEqualTo(
                                "FORBIDDEN"
                        )
                );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        owner,
                        "",
                        List.of()
                )
        );
        for (int i = 0; i < 8; i++)
            spaces.create(
                    owner,
                    new WorkspaceInput("Workspace " + i)
            );
        var stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.clear();
        tester
                .document("{workspaces{id projects{id name}}}")
                .execute()
                .path("workspaces")
                .entityList(Object.class)
                .hasSize(9);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }
}
