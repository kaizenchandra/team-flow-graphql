package com.teamflow.platform;

import com.github.benmanes.caffeine.cache.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestGuard extends OncePerRequestFilter {

    private final String origin;
    private final Cache<String, AtomicInteger> counts = Caffeine.newBuilder()
            .maximumSize(20000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public RequestGuard(@Value("${teamflow.origin}") String origin) {
        this.origin = origin;
    }

    @Bean
    FilterRegistrationBean<RequestGuard> disableDuplicateRegistration() {
        var b = new FilterRegistrationBean<>(this);
        b.setEnabled(false);
        return b;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain
    ) throws ServletException, IOException {
        String id = java.util.UUID.randomUUID().toString();
        res.setHeader("X-Request-ID", id);
        org.slf4j.MDC.put("requestId", id);
        try {
            boolean websocket = "websocket".equalsIgnoreCase(
                    req.getHeader("Upgrade")
            );
            String from = req.getHeader("Origin");
            if (
                    (from != null && !origin.equals(from)) || (websocket && from == null)
            ) {
                res.sendError(403);
                return;
            }
            boolean auth =
                    req.getRequestURI().equals("/auth/login") ||
                            req.getRequestURI().equals("/auth/register");
            String key =
                    (auth ? "auth:" : "api:") +
                            req.getRemoteAddr() +
                            ":" +
                            System.currentTimeMillis() / 60000;
            if (
                    counts.get(key, k -> new AtomicInteger()).incrementAndGet() >
                            (auth ? 20 : 600)
            ) {
                res.setHeader("Retry-After", "60");
                res.sendError(429);
                return;
            }
            if (
                    "POST".equals(req.getMethod()) &&
                            req.getContentType() != null &&
                            req.getContentType().startsWith("application/json")
            ) {
                byte[] body = req.getInputStream().readNBytes(65537);
                if (body.length > 65536) {
                    res.sendError(413);
                    return;
                }
                // Form login depends on servlet parameter parsing; only wrap JSON requests.
                if (
                        req.getContentType() != null &&
                                req.getContentType().startsWith("application/json")
                ) {
                    var wrapped = new HttpServletRequestWrapper(req) {
                        @Override
                        public ServletInputStream getInputStream() {
                            var in = new ByteArrayInputStream(body);
                            return new ServletInputStream() {
                                public int read() {
                                    return in.read();
                                }

                                public boolean isFinished() {
                                    return in.available() == 0;
                                }

                                public boolean isReady() {
                                    return true;
                                }

                                public void setReadListener(ReadListener l) {
                                }
                            };
                        }

                        @Override
                        public BufferedReader getReader() {
                            return new BufferedReader(
                                    new InputStreamReader(
                                            getInputStream(),
                                            java.nio.charset.StandardCharsets.UTF_8
                                    )
                            );
                        }
                    };
                    chain.doFilter(wrapped, res);
                    return;
                }
                // Container form parameters must be captured before consuming the request stream.
                if (body.length > 0) {
                    res.sendError(415);
                    return;
                }
            }
            chain.doFilter(req, res);
        } finally {
            org.slf4j.MDC.remove("requestId");
        }
    }
}
