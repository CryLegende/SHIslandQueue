package org.Cry_Legende.sHIslandQueue;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.schematic.Schematic;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer; // Important pour la conversion

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Biome; // Import pour le type Biome
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal; // Import pour BigDecimal
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Level;

public class SHIslandQueue extends JavaPlugin {

    // Délai max (en secondes) laissé à SSB2 pour finir de créer une île de la file
    private static final int CREATION_CHECK_ATTEMPTS = 60;

    private final Queue<QueuedCreation> creationQueue = new LinkedList<>();
    private final UuidSet processingPlayers = new UuidSet();

    private long nextAvailableSlotTimestamp = 0L;
    private int creationIntervalSeconds;
    private BukkitTask queueProcessorTask;

    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveMessagesIfAbsent();
        try {
            loadSettings();
        } catch (ConfigException e) {
            getLogger().severe("Fichier " + e.getFile() + " illisible, valeurs du jar utilisées : " + e.getMessage());
            useBundledDefaults();
        }

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
        PluginCommand command = getCommand("shislandqueue");
        if (command != null) {
            AdminCommand admin = new AdminCommand(this);
            command.setExecutor(admin);
            command.setTabCompleter(admin);
        }
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

    /** Erreur de lecture d'un fichier de configuration : nom du fichier et cause. */
    static final class ConfigException extends Exception {
        private final String file;

        ConfigException(String file, String message) {
            super(message);
            this.file = file;
        }

        String getFile() {
            return file;
        }
    }

    /**
     * Relit config.yml et messages.yml. Rien n'est remplacé tant que les deux fichiers ne sont pas lus sans erreur.
     * La file, le créneau en cours et les joueurs en cours de traitement ne sont jamais touchés.
     */
    public void loadSettings() throws ConfigException {
        YamlConfiguration config = readYaml("config.yml");
        YamlConfiguration loadedMessages = readYaml("messages.yml");
        creationIntervalSeconds = Math.max(1, config.getInt("creation-interval-seconds", 10));
        messages = loadedMessages;
    }

    /** Valeurs du jar, quand un fichier est illisible au démarrage. */
    private void useBundledDefaults() {
        creationIntervalSeconds = Math.max(1, bundled("config.yml").getInt("creation-interval-seconds", 10));
        messages = bundled("messages.yml");
    }

    private YamlConfiguration bundled(String name) {
        try (InputStream in = getResource(name)) {
            return in == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    /** Fichier du dossier du plugin lu strictement (erreur de syntaxe signalée), complété par les valeurs du jar. */
    private YamlConfiguration readYaml(String name) throws ConfigException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new File(getDataFolder(), name));
        } catch (IOException | InvalidConfigurationException e) {
            String reason = e.getMessage() == null ? e.toString() : e.getMessage().lines().findFirst().orElse("");
            throw new ConfigException(name, reason);
        }
        yaml.setDefaults(bundled(name));
        return yaml;
    }

    /**
     * Premier démarrage avec ce fichier : messages.yml est créé à partir de la section messages de config.yml
     * (textes déjà en place), complétée par les textes du jar. La section de config.yml n'est plus lue ensuite.
     */
    private void saveMessagesIfAbsent() {
        File file = new File(getDataFolder(), "messages.yml");
        if (file.exists()) {
            return;
        }
        saveResource("messages.yml", false);
        ConfigurationSection old = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")).getConfigurationSection("messages");
        if (old == null) {
            return;
        }
        YamlConfiguration created = YamlConfiguration.loadConfiguration(file);
        for (String key : old.getKeys(false)) {
            if (old.isString(key)) {
                created.set(key, old.getString(key));
            }
        }
        try {
            created.save(file);
            getLogger().info("Messages repris de config.yml dans messages.yml (la section messages de config.yml ne sert plus).");
        } catch (IOException e) {
            getLogger().warning("messages.yml non écrit : " + e.getMessage());
        }
    }

    private String translateColors(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String key, String... replacements) {
        String message = messages == null ? null : messages.getString(key);
        if (message == null) {
            message = "&cMessage inconnu (" + this.getName() + "): " + key;
        }
        message = message.replace("%prefix%", messages == null ? "" : translateColors(messages.getString("prefix", "")));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
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
                    sendMessage(playerToProcess, getMessage("creation-failed"));
                    creationQueue.poll(); // Retirer de la file car on ne peut pas traiter
                    processingPlayers.remove(playerToProcess.getUniqueId()); // Assurer le nettoyage
                    nextAvailableSlotTimestamp = currentTime + (creationIntervalSeconds * 1000L);
                    return;
                }

                creationQueue.poll();

                if (SuperiorSkyblockAPI.getGrid().getIsland(superiorPlayerToProcess.getUniqueId()) != null) {
                    sendMessage(playerToProcess, getMessage("already-has-island"));
                    nextAvailableSlotTimestamp = currentTime + (creationIntervalSeconds * 1000L);
                    return;
                }

                sendMessage(playerToProcess, getMessage("creation-in-progress"));
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
                    sendMessage(playerToProcess, getMessage("no-default-schematic"));
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

                        // SSB2 colle le schematic en asynchrone : l'île n'est enregistrée qu'à la fin
                        awaitIslandCreation(superiorPlayerToProcess.getUniqueId(), playerToProcess.getName());
                    } catch (Exception e) {
                        sendMessage(playerToProcess, getMessage("creation-failed"));
                        getLogger().log(Level.SEVERE, "Exception during island creation for " + playerToProcess.getName(), e);
                    } finally {
                        processingPlayers.remove(playerToProcess.getUniqueId());
                    }
                });
                nextAvailableSlotTimestamp = currentTime + (creationIntervalSeconds * 1000L);
            }
        }, 20L, 20L);
    }

    /**
     * Attend que SSB2 ait enregistré l'île (vérifié chaque seconde) avant d'annoncer le résultat.
     */
    private void awaitIslandCreation(UUID playerUniqueId, String playerName) {
        new BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                Island newIsland = SuperiorSkyblockAPI.getGrid().getIsland(playerUniqueId);
                Player player = Bukkit.getPlayer(playerUniqueId);
                if (newIsland != null) {
                    sendMessage(player, getMessage("creation-successful"));
                    getLogger().info("Successfully created island for " + playerName);
                    cancel();
                } else if (++attempts >= CREATION_CHECK_ATTEMPTS) {
                    sendMessage(player, getMessage("creation-failed"));
                    getLogger().log(Level.WARNING, "Failed to create island for " + playerName + " or island object not retrievable after " + CREATION_CHECK_ATTEMPTS + "s.");
                    cancel();
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    /**
     * Un message vide dans la config est désactivé (pas de ligne blanche dans le chat).
     */
    static void sendMessage(Player player, String message) {
        if (player != null && player.isOnline() && message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }
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
