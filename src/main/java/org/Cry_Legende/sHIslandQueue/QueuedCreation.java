package org.Cry_Legende.sHIslandQueue;

import java.util.UUID;
import java.util.Objects;

public class QueuedCreation {
    private final UUID playerUniqueId;
    private final String playerName;

    public QueuedCreation(UUID playerUniqueId, String playerName) {
        this.playerUniqueId = playerUniqueId;
        this.playerName = playerName;
    }

    public UUID getPlayerUniqueId() {
        return playerUniqueId;
    }

    public String getPlayerName() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueuedCreation that = (QueuedCreation) o;
        return Objects.equals(playerUniqueId, that.playerUniqueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUniqueId);
    }
}