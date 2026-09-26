package com.teamflow.identity;

import com.teamflow.platform.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IdentityService service;

    public AuthController(IdentityService service) {
        this.service = service;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "token",
                token.getToken(),
                "headerName",
                token.getHeaderName()
        );
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Api.User register(@RequestBody @Valid Registration input) {
        if (
                input
                        .password()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        .length > 72
        ) throw Problem.invalid("Password must be at most 72 UTF-8 bytes.");
        return service.register(input.email(), input.name(), input.password());
    }

    @ExceptionHandler(Problem.class)
    ResponseEntity<?> problem(Problem e) {
        return ResponseEntity.badRequest().body(
                Map.of("code", e.code, "message", e.getMessage())
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate() {
        return ResponseEntity.status(409).body(
                Map.of("message", "An account with that email already exists.")
        );
    }

    @ExceptionHandler(
            org.springframework.web.bind.MethodArgumentNotValidException.class
    )
    ResponseEntity<?> invalid() {
        return ResponseEntity.badRequest().body(
                Map.of(
                        "message",
                        "Check your email, name (1–80 characters) and password (12–72 characters)."
                )
        );
    }

    public record Registration(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(min = 12, max = 72) String password
    ) {
    }
}
