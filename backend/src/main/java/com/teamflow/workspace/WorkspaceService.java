package com.teamflow.workspace;

import com.teamflow.identity.*;
import com.teamflow.platform.*;
import com.teamflow.platform.Api.*;
import com.teamflow.platform.Events;
import com.teamflow.work.TaskEntity;
import jakarta.persistence.*;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkspaceService {

    private final EntityManager em;
    private final IdentityService identity;
    private final UserRepository users;
    private final Events events;

    public WorkspaceService(
            EntityManager em,
            IdentityService identity,
            UserRepository users,
            Events events
    ) {
        this.em = em;
        this.identity = identity;
        this.users = users;
        this.events = events;
    }

    public static void mayChange(
            Role actor,
            Role before,
            Role after,
            long owners
    ) {
        if (
                actor == Role.MEMBER ||
                        (actor == Role.ADMIN && (before == Role.OWNER || after == Role.OWNER))
        ) throw Problem.forbidden();
        if (
                before == Role.OWNER && after != Role.OWNER && owners <= 1
        ) throw new Problem("LAST_OWNER", "A workspace must retain an owner.");
    }

    public MembershipEntity require(String email, String workspaceId) {
        var uid = identity.me(email).id();
        var member = em
                .createQuery(
                        "from MembershipEntity where workspaceId=:w and userId=:u",
                        MembershipEntity.class
                )
                .setParameter("w", workspaceId)
                .setParameter("u", uid)
                .getResultStream()
                .findFirst()
                .orElseThrow(Problem::forbidden);
        em.refresh(member);
        return member;
    }

    public void requireManager(String email, String workspaceId) {
        if (
                require(email, workspaceId).role == Role.MEMBER
        ) throw Problem.forbidden();
    }

    public void lock(String workspaceId) {
        if (
                em.find(
                        WorkspaceEntity.class,
                        workspaceId,
                        LockModeType.PESSIMISTIC_WRITE
                ) == null
        ) throw Problem.forbidden();
    }

    public List<Workspace> list(String email) {
        return em
                .createQuery(
                        "select w,m.role from WorkspaceEntity w, MembershipEntity m where w.id=m.workspaceId and m.userId=:u order by w.name,w.id",
                        Object[].class
                )
                .setParameter("u", identity.me(email).id())
                .getResultList()
                .stream()
                .map(r ->
                        new Workspace(
                                ((WorkspaceEntity) r[0]).id,
                                ((WorkspaceEntity) r[0]).name,
                                (Role) r[1]
                        )
                )
                .toList();
    }

    public Workspace create(String email, WorkspaceInput in) {
        var w = new WorkspaceEntity();
        w.name = in.name().trim();
        em.persist(w);
        var m = new MembershipEntity();
        m.workspaceId = w.id;
        m.userId = identity.me(email).id();
        m.role = Role.OWNER;
        em.persist(m);
        return new Workspace(w.id, w.name, m.role);
    }

    public List<Member> members(String email, String wid) {
        require(email, wid);
        return em
                .createQuery(
                        "select u,m.role from UserEntity u, MembershipEntity m where u.id=m.userId and m.workspaceId=:w order by u.name,u.id",
                        Object[].class
                )
                .setParameter("w", wid)
                .getResultList()
                .stream()
                .map(r ->
                        new Member(IdentityService.view((UserEntity) r[0]), (Role) r[1])
                )
                .toList();
    }

    private long owners(String wid) {
        return em
                .createQuery(
                        "select count(m) from MembershipEntity m where workspaceId=:w and role=:r",
                        Long.class
                )
                .setParameter("w", wid)
                .setParameter("r", Role.OWNER)
                .getSingleResult();
    }

    public Member setMember(String email, MemberInput in) {
        require(email, in.workspaceId());
        lock(in.workspaceId());
        var actor = require(email, in.workspaceId());
        var user = users
                .findByEmail(IdentityService.normalize(in.email()))
                .orElseThrow(() ->
                        Problem.invalid("Register this user before adding them.")
                );
        var existing = em
                .createQuery(
                        "from MembershipEntity where workspaceId=:w and userId=:u",
                        MembershipEntity.class
                )
                .setParameter("w", in.workspaceId())
                .setParameter("u", user.id)
                .getResultStream()
                .findFirst();
        mayChange(
                actor.role,
                existing.map(m -> m.role).orElse(null),
                in.role(),
                owners(in.workspaceId())
        );
        var m = existing.orElseGet(MembershipEntity::new);
        m.workspaceId = in.workspaceId();
        m.userId = user.id;
        m.role = in.role();
        if (existing.isEmpty()) em.persist(m);
        events.record(
                in.workspaceId(),
                user.id,
                "MEMBER_CHANGED",
                identity.me(email).id(),
                null
        );
        return new Member(IdentityService.view(user), m.role);
    }

    public String removeMember(String email, RemoveMemberInput in) {
        require(email, in.workspaceId());
        lock(in.workspaceId());
        var actor = require(email, in.workspaceId());
        var m = em
                .createQuery(
                        "from MembershipEntity where workspaceId=:w and userId=:u",
                        MembershipEntity.class
                )
                .setParameter("w", in.workspaceId())
                .setParameter("u", in.userId())
                .getResultStream()
                .findFirst()
                .orElseThrow(Problem::forbidden);
        mayChange(actor.role, m.role, null, owners(in.workspaceId()));
        em.createQuery(
                        "update TaskEntity set assigneeId=null, version=version+1, updatedAt=:now where workspaceId=:w and assigneeId=:u"
                )
                .setParameter("now", java.time.Instant.now())
                .setParameter("w", in.workspaceId())
                .setParameter("u", in.userId())
                .executeUpdate();
        em.remove(m);
        events.record(
                in.workspaceId(),
                in.userId(),
                "MEMBER_REMOVED",
                identity.me(email).id(),
                null
        );
        return in.userId();
    }

    public void validateAssignee(String wid, String uid) {
        if (
                uid != null &&
                        em
                                .createQuery(
                                        "select count(m) from MembershipEntity m where workspaceId=:w and userId=:u",
                                        Long.class
                                )
                                .setParameter("w", wid)
                                .setParameter("u", uid)
                                .getSingleResult() == 0
        ) throw Problem.invalid("Assignee must be a workspace member.");
    }
}
