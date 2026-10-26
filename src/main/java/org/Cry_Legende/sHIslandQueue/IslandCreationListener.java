package org.Cry_Legende.sHIslandQueue;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI; // Import statique
import com.bgsoftware.superiorskyblock.api.events.PreIslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class IslandCreationListener implements Listener {

    private final SHIslandQueue plugin;

    public IslandCreationListener(SHIslandQueue plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIslandPreCreate(PreIslandCreateEvent event) {
        SuperiorPlayer superiorPlayer = event.getPlayer();
        if (superiorPlayer == null) return;

        Player player = superiorPlayer.asPlayer();
        if (player == null) {
            this.plugin.getLogger().warning("SuperiorPlayer " + superiorPlayer.getName() + " (" + superiorPlayer.getUniqueId() + ") n'a pas pu être converti en Bukkit Player.");
            event.setCancelled(true);
            return;
        }

        if (plugin.isPlayerBeingProcessed(player.getUniqueId())) {
            this.plugin.getLogger().fine("Joueur " + player.getName() + " en cours de traitement par la file d'attente, création autorisée.");
            return;
        }

        // Utiliser GridManager.getIsland(ownerUUID) pour vérifier si le joueur a une île
        if (SuperiorSkyblockAPI.getGrid().getIsland(player.getUniqueId()) != null) {
            this.plugin.getLogger().fine("Joueur " + player.getName() + " a déjà une île (détecté par SHIslandQueue via GridManager).");
            // Laisser SSB2 gérer le message d'erreur et l'annulation de l'événement
            return;
        }

        QueuedCreation checkRequest = new QueuedCreation(player.getUniqueId(), player.getName());
        if (plugin.getCreationQueue().contains(checkRequest)) {
            SHIslandQueue.sendMessage(player, plugin.getMessage("already-in-queue"));
            event.setCancelled(true);
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime >= plugin.getNextAvailableSlotTimestamp() && plugin.getCreationQueue().isEmpty()) {
            this.plugin.getLogger().info("Joueur " + player.getName() + " crée son île instantanément. Prochain slot dans " + plugin.getCreationIntervalSeconds() + "s.");
            plugin.setNextAvailableSlotTimestamp(currentTime + (plugin.getCreationIntervalSeconds() * 1000L));
        } else {
            event.setCancelled(true);

            QueuedCreation newRequest = new QueuedCreation(player.getUniqueId(), player.getName());
            plugin.getCreationQueue().offer(newRequest);

            long timeUntilNextScheduledProcessing = Math.max(0, plugin.getNextAvailableSlotTimestamp() - currentTime);
            long estimatedWaitMillis = timeUntilNextScheduledProcessing +
                (long) (plugin.getCreationQueue().size() - 1) * plugin.getCreationIntervalSeconds() * 1000L;
            long estimatedWaitSeconds = (estimatedWaitMillis + 500) / 1000L;

            SHIslandQueue.sendMessage(player, plugin.getMessage("added-to-queue",
                "%time%", String.valueOf(estimatedWaitSeconds),
                "%position%", String.valueOf(plugin.getCreationQueue().size())));

            this.plugin.getLogger().info("Joueur " + player.getName() + " ajouté à la file. Position: " + plugin.getCreationQueue().size() + ". Attente estimée: " + estimatedWaitSeconds + "s.");
        }
    }
}
