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
    MaxQueryDepthInstrumentation depth() {
        return new MaxQueryDepthInstrumentation(10);
    }

    @Bean
    MaxQueryComplexityInstrumentation complexity() {
        return new MaxQueryComplexityInstrumentation(250);
    }

    @Bean
    WebGraphQlInterceptor sessionContext(SessionAccess sessions) {
        return (request, chain) -> {
            var session = sessions.find(request.getHeaders().getFirst("Cookie"));
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
