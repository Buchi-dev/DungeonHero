package com.dungeonhero.framework.context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record GameplayContext(Optional<PlayerContext> player, Optional<TeamContext> team,
                              Map<String, Object> attributes) {
    public GameplayContext {
        player = player == null ? Optional.empty() : player;
        team = team == null ? Optional.empty() : team;
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public static GameplayContext forPlayer(PlayerContext player, Map<String, Object> attributes) {
        return new GameplayContext(Optional.of(player), Optional.empty(), attributes);
    }
}
