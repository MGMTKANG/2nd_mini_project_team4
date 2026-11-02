package com.example.sporty.support;

import java.util.HashMap;
import java.util.Map;

/** Register users and map test labels to generated user IDs. */
public final class MatchUserFixtures {
    private MatchUserFixtures() {}

    public static Map<Long, Long> create(FakeUserClient userClient, int count) {
        Map<Long, Long> users = new HashMap<>();
        for (long label = 1; label <= count; label++) {
            users.put(label, userClient.addActiveUser());
        }
        return users;
    }
}
