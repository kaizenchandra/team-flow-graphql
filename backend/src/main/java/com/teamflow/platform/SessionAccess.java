package com.teamflow.platform;

import com.github.benmanes.caffeine.cache.*;
import jakarta.servlet.http.HttpSession;

import java.time.Duration;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class SessionAccess {

    private final Cache<String, HttpSession> sessions = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(Duration.ofMinutes(31))
            .build();

    public void add(HttpSession s) {
        sessions.put(s.getId(), s);
    }

    public HttpSession find(String cookie) {
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            var pair = part.trim().split("=", 2);
            if (
                    pair.length == 2 && pair[0].equals("JSESSIONID")
            ) return sessions.getIfPresent(pair[1]);
        }
        return null;
    }

    public String email(HttpSession s) {
        try {
            if (
                    s == null ||
                            System.currentTimeMillis() - s.getLastAccessedTime() >
                                    s.getMaxInactiveInterval() * 1000L
            ) throw Problem.forbidden();
            var context = (SecurityContext) s.getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
            );
            if (
                    context == null ||
                            context.getAuthentication() == null ||
                            !context.getAuthentication().isAuthenticated()
            ) throw Problem.forbidden();
            return context.getAuthentication().getName();
        } catch (IllegalStateException e) {
            throw Problem.forbidden();
        }
    }
}
