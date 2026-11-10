package com.gabriel.financeflow.user.dto;

import com.gabriel.financeflow.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String currency,
        String timezone,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCurrency(),
                user.getTimezone(),
                user.getCreatedAt()
        );
    }
}
