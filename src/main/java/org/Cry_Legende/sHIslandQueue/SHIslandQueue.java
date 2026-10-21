package org.Cry_Legende.sHIslandQueue;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.schematic.Schematic;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer; // Important pour la conversion

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Biome; // Import pour le type Biome
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal; // Import pour BigDecimal
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Level;

public class SHIslandQueue extends JavaPlugin {

    private final Queue<QueuedCreation> creationQueue = new LinkedList<>();
    private final UuidSet processingPlayers = new UuidSet();

    private long nextAvailableSlotTimestamp = 0L;
    private int creationIntervalSeconds;
    private BukkitTask queueProcessorTask;

    private String msgPrefix;
    private String msgAddedToQueue;
    private String msgCreationInProgress;
    private String msgCreationSuccessful;
    private String msgCreationFailed;
    private String msgAlreadyInQueue;
    private String msgAlreadyHasIsland;
    private String msgPlayerOffline;
    private String msgNoDefaultSchematic;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        if (Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2") == null) {
            getLogger().severe("SuperiorSkyblock2 non trouvé ! Le plugin " + this.getName() + " ne peut pas fonctionner.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            if (SuperiorSkyblockAPI.getGrid() == null) {
                throw new IllegalStateException("SuperiorSkyblockAPI.getGrid() a retourné null.");
            }
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().log(Level.SEVERE, "L'API SuperiorSkyblock2 n'a pas pu être chargée. " + this.getName() + " désactivé.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.nextAvailableSlotTimestamp = System.currentTimeMillis();
        getServer().getPluginManager().registerEvents(new IslandCreationListener(this), this);
        startQueueProcessor();
        getLogger().info(this.getName() + " activé ! Intervalle de création: " + creationIntervalSeconds + " secondes.");
    }

    @Override
    public void onDisable() {
        if (queueProcessorTask != null && !queueProcessorTask.isCancelled()) {
            queueProcessorTask.cancel();
        }
        creationQueue.clear();
        processingPlayers.clear();
        getLogger().info(this.getName() + " désactivé.");
    }

    public void loadConfigValues() {
        FileConfiguration config = getConfig();
        config.addDefault("creation-interval-seconds", 10);
        config.addDefault("messages.prefix", "&8[&bIslandQueue&8] &r");
        config.addDefault("messages.added-to-queue", "%prefix%&aVous avez été ajouté à la file d'attente. Votre île (type par défaut) sera créée dans environ &e%time% &asecondes. Position : &e%position%&a.");
        config.addDefault("messages.creation-in-progress", "%prefix%&6La création de votre île (type par défaut) est en cours...");
        config.addDefault("messages.creation-successful", "%prefix%&2Votre île a été créée avec succès !");
        config.addDefault("messages.creation-failed", "%prefix%&cLa création de votre île a échoué. Veuillez contacter un administrateur.");
        config.addDefault("messages.already-in-queue", "%prefix%&cVous êtes déjà dans la file d'attente pour la création d'une île.");
        config.addDefault("messages.already-has-island", "%prefix%&cVous possédez déjà une île !");
        config.addDefault("messages.player-offline", "%prefix%&cLe joueur %player% était hors ligne lors de la tentative de création de son île.");
        config.addDefault("messages.no-default-schematic", "%prefix%&cAucun schematic par défaut n'est configuré. Impossible de créer l'île.");
        config.options().copyDefaults(true);
        saveConfig();

        creationIntervalSeconds = config.getInt("creation-interval-seconds");
        msgPrefix = translateColors(config.getString("messages.prefix"));
        msgAddedToQueue = config.getString("messages.added-to-queue");
        msgCreationInProgress = config.getString("messages.creation-in-progress");
        msgCreationSuccessful = config.getString("messages.creation-successful");
        msgCreationFailed = config.getString("messages.creation-failed");
        msgAlreadyInQueue = config.getString("messages.already-in-queue");
        msgAlreadyHasIsland = config.getString("messages.already-has-island");
        msgPlayerOffline = config.getString("messages.player-offline");
        msgNoDefaultSchematic = config.getString("messages.no-default-schematic");
    }

    private String translateColors(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String key, String... replacements) {
        String messageFormat;
        switch (key) {
            case "added-to-queue":
                messageFormat = msgAddedToQueue;
                break;
            case "creation-in-progress":
                messageFormat = msgCreationInProgress;
                break;
            case "creation-successful":
                messageFormat = msgCreationSuccessful;
                break;
            case "creation-failed":
                messageFormat = msgCreationFailed;
                break;
            case "already-in-queue":
                messageFormat = msgAlreadyInQueue;
                break;
            case "already-has-island":
                messageFormat = msgAlreadyHasIsland;
                break;
            case "player-offline":
                messageFormat = msgPlayerOffline;
                break;
            case "no-default-schematic":
                messageFormat = msgNoDefaultSchematic;
                break;
            default:
                messageFormat = "%prefix%&cMessage inconnu (" + this.getName() + "): " + key;
        }
        String actualPrefix = (msgPrefix != null && !msgPrefix.isEmpty()) ? msgPrefix : "&8[&e" + this.getName() + "&8] &r";
        messageFormat = messageFormat.replace("%prefix%", actualPrefix);
        String message = messageFormat;
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        return translateColors(message);
    }

    private void startQueueProcessor() {
        queueProcessorTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (creationQueue.isEmpty()) {
                return;
            }
            long currentTime = System.currentTimeMillis();
            if (currentTime >= nextAvailableSlotTimestamp) {
                QueuedCreation request = creationQueue.peek();
                if (request == null) {
                    creationQueue.poll();
                    return;
                }
                Player playerToProcess = Bukkit.getPlayer(request.getPlayerUniqueId());
                if (playerToProcess == null || !playerToProcess.isOnline()) {
                    getLogger().warning(getMessage("player-offline", "%player%", request.getPlayerName()));
                    creationQueue.poll();
                    nextAvailableSlotTimestamp = currentTime + (creationIntervalSeconds * 1000L);
                    return;
                }

                // Convertir en SuperiorPlayer
                SuperiorPlayer superiorPlayerToProcess = SuperiorSkyblockAPI.getPlayer(playerToProcess);
                if (superiorPlayerToProcess == null) {
                    getLogger().severe("Impossible de convertir le joueur Bukkit " + playerToProcess.getName() + " en SuperiorPlayer.");
                    playerToProcess.sendMessage(getMessage("creation-failed"));
                    creationQueue.poll(); // Retirer de la file car on ne peut pas traiter
                    processingPlayers.remove(playerToProcess.getUniqueId()); // Assurer le nettoyage
                    nextAvailableSlotTimestamp = currentTime + (creationIntervalSeconds * 1000L);
                    return;
                }

                creationQueue.poll();

                if (SuperiorSkyblockAPI.getGrid().getIsland(superiorPlayerToProcess.getUniqueId()) != null) {
                    playerToProcess.sendMessage(getMessage("already-has-island"));
                    nextAvailableSlotTimestamp = currentTime + (creationIntervalSeconds * 1000L);
                    return;
                }

                playerToProcess.sendMessage(getMessage("creation-in-progress"));
                getLogger().info("Processing island creation for " + playerToProcess.getName());
                processingPlayers.add(playerToProcess.getUniqueId());

                com.bgsoftware.superiorskyblock.api.handlers.SchematicManager schemManager = SuperiorSkyblockAPI.getSchematics();
                Schematic schematicToUse = schemManager.getSchematic("default");
                if (schematicToUse == null) {
                    schematicToUse = schemManager.getSchematics().stream()
                        .map(schemManager::getSchematic)
                        .filter(java.util.Objects::nonNull)
                        .findFirst().orElse(null);
                }

                if (schematicToUse == null) {
                    playerToProcess.sendMessage(getMessage("no-default-schematic"));
                    getLogger().severe("Aucun schematic par défaut ou disponible trouvé pour " + playerToProcess.getName());
                    processingPlayers.remove(playerToProcess.getUniqueId());
                    nextAvailableSlotTimestamp = currentTime + (creationIntervalSeconds * 1000L);
                    return;
                }
                getLogger().info("Utilisation du schematic '" + schematicToUse.getName() + "' pour " + playerToProcess.getName());

                final String schematicName = schematicToUse.getName();
                final com.bgsoftware.superiorskyblock.api.handlers.GridManager finalGridManager = SuperiorSkyblockAPI.getGrid();

                // Déterminer un biome valide
                Biome islandBiome = null;
                // Essayer d'obtenir le biome du monde principal (Overworld)
                // Le premier monde de la liste est généralement le monde principal.
                if (Bukkit.getWorlds().size() > 0) {
                    org.bukkit.World defaultWorld = Bukkit.getWorlds().get(0); // Souvent l'overworld
                    // Prendre le biome au centre du monde (0, milieu, 0) ou un point fixe
                    // Ou utiliser le biome par défaut du monde si disponible via API
                    // Pour la simplicité, on prend le biome à un point fixe (ex: 0,64,0) du premier monde chargé
                    // Il serait mieux si SSB2 a une méthode pour obtenir le biome par défaut d'un type de monde.
                    islandBiome = defaultWorld.getBiome(0, defaultWorld.getSeaLevel(), 0);
                }

                // Fallback si on n'a pas pu déterminer un biome du monde
                if (islandBiome == null) {
                    islandBiome = Biome.PLAINS; // Un biome par défaut très commun
                    getLogger().warning("N'a pas pu déterminer le biome du monde principal, utilisation de PLAINS par défaut pour l'île de " + playerToProcess.getName());
                }

                final Biome finalIslandBiome = islandBiome;
                final String islandNameForCreation = playerToProcess.getName(); // Utiliser le nom du joueur

                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        // Utilisation de finalIslandBiome et islandNameForCreation
                        finalGridManager.createIsland(superiorPlayerToProcess, schematicName,
                            BigDecimal.ZERO, BigDecimal.ZERO,
                            finalIslandBiome, islandNameForCreation, false); // Appel corrigé

                        Island newIsland = finalGridManager.getIsland(superiorPlayerToProcess.getUniqueId());

                        if (newIsland != null) {
                            playerToProcess.sendMessage(getMessage("creation-successful"));
                            getLogger().info("Successfully created island for " + playerToProcess.getName());
                        } else {
                            playerToProcess.sendMessage(getMessage("creation-failed"));
                            getLogger().log(Level.WARNING, "Failed to create island for " + playerToProcess.getName() + " or island object not immediately retrievable.");
                        }
                    } catch (Exception e) {
                        playerToProcess.sendMessage(getMessage("creation-failed"));
                        getLogger().log(Level.SEVERE, "Exception during island creation for " + playerToProcess.getName(), e);
                    } finally {
                        processingPlayers.remove(playerToProcess.getUniqueId());
                    }
                });
                nextAvailableSlotTimestamp = currentTime + (creationIntervalSeconds * 1000L);
            }
        }, 20L, 20L);
    }

    public com.bgsoftware.superiorskyblock.api.handlers.GridManager getSsbGridManager() {
        return SuperiorSkyblockAPI.getGrid();
    }

    public com.bgsoftware.superiorskyblock.api.handlers.SchematicManager getSsbSchematicManager() {
        return SuperiorSkyblockAPI.getSchematics();
    }

    public Queue<QueuedCreation> getCreationQueue() {
        return creationQueue;
    }

    public long getNextAvailableSlotTimestamp() {
        return nextAvailableSlotTimestamp;
    }

    public void setNextAvailableSlotTimestamp(long timestamp) {
        this.nextAvailableSlotTimestamp = timestamp;
    }

    public int getCreationIntervalSeconds() {
        return creationIntervalSeconds;
    }

    public boolean isPlayerBeingProcessed(UUID uuid) {
        return processingPlayers.contains(uuid);
    }

    private static class UuidSet {
        private final java.util.Set<UUID> uuids = new java.util.HashSet<>();

        public void add(UUID uuid) {
            uuids.add(uuid);
        }

        public void remove(UUID uuid) {
            uuids.remove(uuid);
        }

        public boolean contains(UUID uuid) {
            return uuids.contains(uuid);
        }

        public void clear() {
            uuids.clear();
        }
    }
}
