package com.example.sporty.features.users.event;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserWithdrawnEvent {
    private final Long userId;
    private final UUID eventId;
    private final Instant occurredAt;
}
