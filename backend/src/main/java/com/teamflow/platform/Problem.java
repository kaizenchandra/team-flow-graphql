package com.teamflow.platform;

public class Problem extends RuntimeException {

    public final String code;

    public Problem(String code, String message) {
        super(message);
        this.code = code;
    }

    public static Problem forbidden() {
        return new Problem(
                "FORBIDDEN",
                "This resource is unavailable or you do not have access."
        );
    }

    public static Problem invalid(String message) {
        return new Problem("BAD_INPUT", message);
    }

    public static Problem conflict() {
        return new Problem(
                "CONFLICT",
                "This item changed. Reload it and apply your changes again."
        );
    }
}
