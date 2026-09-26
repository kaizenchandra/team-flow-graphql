package com.teamflow.platform;

import com.teamflow.identity.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email ->
                users
                        .findByEmail(IdentityService.normalize(email))
                        .map(u ->
                                User.withUsername(u.email).password(u.password).roles("USER").build()
                        )
                        .orElseThrow(() ->
                                new UsernameNotFoundException("Invalid credentials")
                        );
    }

    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            RequestGuard guard,
            SessionAccess sessions
    ) throws Exception {
        http
                .authorizeHttpRequests(a ->
                        a
                                .requestMatchers(
                                        "/auth/csrf",
                                        "/auth/register",
                                        "/auth/login",
                                        "/actuator/health/**"
                                )
                                .permitAll()
                                .anyRequest()
                                .authenticated()
                )
                .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .formLogin(f ->
                        f
                                .loginProcessingUrl("/auth/login")
                                .usernameParameter("email")
                                .successHandler((req, res, auth) -> {
                                    sessions.add(req.getSession());
                                    res.setContentType("application/json");
                                    res.getWriter().write("{\"ok\":true}");
                                })
                                .failureHandler((req, res, e) -> {
                                    res.setStatus(401);
                                    res.setContentType("application/json");
                                    res
                                            .getWriter()
                                            .write("{\"message\":\"Invalid email or password.\"}");
                                })
                )
                .logout(l ->
                        l
                                .logoutUrl("/auth/logout")
                                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204))
                )
                .exceptionHandling(e ->
                        e
                                .authenticationEntryPoint((req, res, error) -> res.sendError(401))
                                .accessDeniedHandler((req, res, error) -> res.sendError(403))
                )
                .addFilterBefore(guard, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
