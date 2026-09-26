package com.teamflow;

import static com.teamflow.platform.Api.Role.*;
import static org.assertj.core.api.Assertions.*;

import com.teamflow.platform.*;
import com.teamflow.work.WorkService;
import com.teamflow.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;

class RulesTest {

    @Test
    void expiredSessionsCannotAuthorizeSocketDelivery() {
        var expired = new org.springframework.mock.web.MockHttpSession() {
            @Override public long getLastAccessedTime() { return System.currentTimeMillis() - 5_000; }
        };
        expired.setMaxInactiveInterval(1);
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("expired@example.test", "", java.util.List.of()));
        expired.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        assertThatThrownBy(() -> new SessionAccess().email(expired)).isInstanceOf(Problem.class);
    }

    @Test
    void permissionMatrix() {
        assertThatThrownBy(() ->
                WorkspaceService.mayChange(MEMBER, MEMBER, ADMIN, 2)
        ).isInstanceOf(Problem.class);
        assertThatThrownBy(() ->
                WorkspaceService.mayChange(ADMIN, OWNER, MEMBER, 2)
        ).isInstanceOf(Problem.class);
        assertThatThrownBy(() ->
                WorkspaceService.mayChange(ADMIN, MEMBER, OWNER, 2)
        ).isInstanceOf(Problem.class);
        assertThatCode(() ->
                WorkspaceService.mayChange(ADMIN, MEMBER, ADMIN, 1)
        ).doesNotThrowAnyException();
        assertThatCode(() ->
                WorkspaceService.mayChange(OWNER, OWNER, MEMBER, 2)
        ).doesNotThrowAnyException();
    }

    @Test
    void lastOwner() {
        assertThatThrownBy(() -> WorkspaceService.mayChange(OWNER, OWNER, null, 1))
                .isInstanceOf(Problem.class)
                .hasMessageContaining("retain an owner");
        assertThatThrownBy(() ->
                WorkspaceService.mayChange(OWNER, OWNER, MEMBER, 1)
        ).isInstanceOf(Problem.class);
    }

    @Test
    void boundedPages() {
        assertThat(WorkService.pageSize(50)).isEqualTo(50);
        assertThatThrownBy(() -> WorkService.pageSize(51)).isInstanceOf(
                Problem.class
        );
        assertThatThrownBy(() -> WorkService.pageSize(0)).isInstanceOf(
                Problem.class
        );
    }
}
