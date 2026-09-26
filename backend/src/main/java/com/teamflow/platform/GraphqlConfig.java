package com.teamflow.platform;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.analysis.*;
import graphql.schema.DataFetchingEnvironment;

import java.util.Map;

import org.springframework.context.annotation.*;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.server.WebGraphQlInterceptor;

@Configuration
public class GraphqlConfig {

    @Bean
    org.springframework.boot.web.servlet.ServletContextInitializer websocketLimits() {
        return context -> context.setInitParameter("org.apache.tomcat.websocket.textBufferSize", "65536");
    }

    @Bean
    MaxQueryDepthInstrumentation depth() {
        return new MaxQueryDepthInstrumentation(10);
    }

    @Bean
    MaxQueryComplexityInstrumentation complexity() {
        return new MaxQueryComplexityInstrumentation(250);
    }

    @Bean
    WebGraphQlInterceptor sessionContext(SessionAccess sessions) {
        var operations = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .maximumSize(10000).expireAfterWrite(java.time.Duration.ofMinutes(1))
                .<String, java.util.concurrent.atomic.AtomicInteger>build();
        return (request, chain) -> {
            var session = sessions.find(request.getHeaders().getFirst("Cookie"));
            if (request instanceof org.springframework.graphql.server.WebSocketGraphQlRequest) {
                // The handshake principal is stale after logout; validate each operation against its session.
                sessions.email(session);
                if (operations.get(session.getId(), key -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet() > 120)
                    return reactor.core.publisher.Mono.error(new Problem("RATE_LIMITED", "Too many WebSocket operations. Reconnect after one minute."));
            }
            if (session != null) request.configureExecutionInput((input, builder) ->
                    builder.graphQLContext(Map.of("session", session)).build()
            );
            return chain.next(request);
        };
    }

    @Bean
    DataFetcherExceptionResolverAdapter errors() {
        return new DataFetcherExceptionResolverAdapter() {
            @Override
            protected GraphQLError resolveToSingleError(
                    Throwable ex,
                    DataFetchingEnvironment env
            ) {
                String code = "INTERNAL_ERROR",
                        message = "Unable to complete this operation.";
                if (ex instanceof Problem p) {
                    code = p.code;
                    message = p.getMessage();
                } else if (
                        ex instanceof
                                org.springframework.dao.OptimisticLockingFailureException ||
                                ex instanceof jakarta.persistence.OptimisticLockException
                ) {
                    code = "CONFLICT";
                    message = Problem.conflict().getMessage();
                } else if (
                        ex instanceof jakarta.validation.ValidationException ||
                                ex instanceof IllegalArgumentException ||
                                ex instanceof org.springframework.validation.BindException
                ) {
                    code = "BAD_INPUT";
                    message = "Check the supplied values and field lengths.";
                }
                return GraphqlErrorBuilder.newError(env)
                        .message(message)
                        .extensions(Map.of("code", code))
                        .build();
            }
        };
    }
}
