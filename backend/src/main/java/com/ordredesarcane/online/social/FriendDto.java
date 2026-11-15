package com.ordredesarcane.online.social;

import java.time.Instant;
import java.util.UUID;

public record FriendDto(
        UUID friendshipId,
        UUID friendUserId,
        String friendUsername,
        String status,
        boolean incoming,
        boolean online,
        Instant createdAt
) {
}
