package com.shubh.movietickets.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/*
 Thrown when a seat lock or booking conflicts with an existing lock/booking.
 Returns 409 Conflict — the client should retry or choose different seats.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
