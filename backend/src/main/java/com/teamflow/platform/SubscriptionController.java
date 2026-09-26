package com.teamflow.platform;

import com.teamflow.workspace.WorkspaceService;
import jakarta.servlet.http.HttpSession;

import java.time.Duration;

import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;

@Controller
public class SubscriptionController {

    private final Events events;
    private final WorkspaceService spaces;
    private final SessionAccess sessions;

    public SubscriptionController(
            Events events,
            WorkspaceService spaces,
            SessionAccess sessions
    ) {
        this.events = events;
        this.spaces = spaces;
        this.sessions = sessions;
    }

    private void authorized(HttpSession session, String wid) {
        spaces.require(sessions.email(session), wid);
    }

    @SubscriptionMapping
    public Flux<Api.ChangeEvent> workspaceChanges(
            @Argument String workspaceId,
            @ContextValue(name = "session", required = false) HttpSession session
    ) {
        // All blocking JPA authorization is explicitly isolated from Reactor event-loop threads.
        return Mono.fromRunnable(() -> authorized(session, workspaceId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(
                        events
                                .stream()
                                .filter(e -> e.workspaceId().equals(workspaceId))
                                .onBackpressureBuffer(128)
                                .publishOn(Schedulers.boundedElastic(), 1)
                                .handle((event, sink) -> {
                                    authorized(session, workspaceId);
                                    sink.next(event);
                                })
                                .cast(Api.ChangeEvent.class)
                                .mergeWith(
                                        Flux.interval(Duration.ofSeconds(2))
                                                .publishOn(Schedulers.boundedElastic())
                                                .handle((tick, sink) -> authorized(session, workspaceId))
                                )
                );
    }
}
