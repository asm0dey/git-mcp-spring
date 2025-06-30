package com.github.asm0dey.git_mcp_spring.model;

public sealed interface Result<T> permits Result.Success, Result.Failure {
    public record Success<T>(T value) implements Result<T> {
    }

    public record Failure<T>(String message) implements Result<T> {
    }

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }
    static <T> Result<T> failure(String message) {
        return new Failure<>(message);
    }
}
