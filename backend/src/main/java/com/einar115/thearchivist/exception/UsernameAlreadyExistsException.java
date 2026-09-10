package com.einar115.thearchivist.exception;

public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String username) {
        super("User '" + username + "' already exists");
    }
}
