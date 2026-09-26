package com.teamflow.identity;

import com.teamflow.platform.*;

import java.util.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public IdentityService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static Api.User view(UserEntity u) {
        return new Api.User(u.id, u.email, u.name);
    }

    @Transactional
    public Api.User register(String email, String name, String password) {
        if (users.findByEmail(normalize(email)).isPresent()) throw new Problem(
                "DUPLICATE_ACCOUNT",
                "An account with that email already exists."
        );
        var u = new UserEntity();
        u.email = normalize(email);
        u.name = name.trim();
        u.password = passwords.encode(password);
        return view(users.saveAndFlush(u));
    }

    public Api.User me(String email) {
        return view(users.findByEmail(email).orElseThrow(Problem::forbidden));
    }

    public Map<String, Api.User> users(Collection<String> ids) {
        var result = new HashMap<String, Api.User>();
        users.findAllById(ids).forEach(u -> result.put(u.id, view(u)));
        return result;
    }
}
