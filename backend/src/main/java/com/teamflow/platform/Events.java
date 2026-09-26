package com.teamflow.platform;

import com.teamflow.work.ActivityEntity;
import jakarta.persistence.EntityManager;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import reactor.core.publisher.*;

@Component
public class Events {

    private final EntityManager em;
    private final ApplicationEventPublisher publisher;
    private final Sinks.Many<Api.ChangeEvent> sink = Sinks.many()
            .multicast()
            .directBestEffort();

    public Events(EntityManager em, ApplicationEventPublisher publisher) {
        this.em = em;
        this.publisher = publisher;
    }

    public void record(
            String wid,
            String entity,
            String type,
            String actor,
            Long version
    ) {
        var a = new ActivityEntity();
        a.workspaceId = wid;
        a.entityId = entity;
        a.type = type;
        a.actorId = actor;
        a.createdAt = Instant.now();
        em.persist(a);
        publisher.publishEvent(
                new Api.ChangeEvent(
                        a.id,
                        wid,
                        entity,
                        type,
                        a.createdAt.toString(),
                        version
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void committed(Api.ChangeEvent event) {
        sink.tryEmitNext(event);
    }

    public Flux<Api.ChangeEvent> stream() {
        return sink.asFlux();
    }
}
