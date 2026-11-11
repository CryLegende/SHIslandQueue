package org.Cry_Legende.sHIslandQueue;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * /shislandqueue reload | list : administration de la file d'attente de création d'îles.
 */
final class AdminCommand implements TabExecutor {

    private record Sub(String name, String permission) {
    }

    private static final List<Sub> SUBS = List.of(
        new Sub("reload", "shislandqueue.admin.reload"),
        new Sub("list", "shislandqueue.admin.list"));

    private final SHIslandQueue plugin;

    AdminCommand(SHIslandQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Sub sub = args.length == 0 ? null : find(args[0]);
        if (sub == null || !sender.hasPermission(sub.permission())) {
            usage(sender);
            return true;
        }
        switch (sub.name()) {
            case "reload" -> reload(sender);
            case "list" -> list(sender);
            default -> usage(sender);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        try {
            plugin.loadSettings();
        } catch (SHIslandQueue.ConfigException e) {
            plugin.getLogger().log(Level.SEVERE, "Rechargement annulé, " + e.getFile() + " illisible : " + e.getMessage());
            sender.sendMessage(plugin.getMessage("reload-failed", "%file%", e.getFile(), "%error%", e.getMessage()));
            return;
        }
        sender.sendMessage(plugin.getMessage("reloaded", "%interval%", String.valueOf(plugin.getCreationIntervalSeconds())));
    }

    private void list(CommandSender sender) {
        long now = System.currentTimeMillis();
        long untilSlot = Math.max(0L, plugin.getNextAvailableSlotTimestamp() - now);
        String interval = String.valueOf(plugin.getCreationIntervalSeconds());
        String slot = String.valueOf((untilSlot + 500L) / 1000L);
        var queue = plugin.getCreationQueue();
        if (queue.isEmpty()) {
            sender.sendMessage(plugin.getMessage("list-empty", "%interval%", interval, "%slot%", slot));
            return;
        }
        sender.sendMessage(plugin.getMessage("list-header", "%count%", String.valueOf(queue.size()), "%interval%", interval, "%slot%", slot));
        int position = 1;
        for (QueuedCreation request : queue) {
            long waitMillis = untilSlot + (long) (position - 1) * plugin.getCreationIntervalSeconds() * 1000L;
            sender.sendMessage(plugin.getMessage("list-line", "%position%", String.valueOf(position),
                "%player%", request.getPlayerName(), "%wait%", String.valueOf((waitMillis + 500L) / 1000L)));
            position++;
        }
    }

    private void usage(CommandSender sender) {
        List<String> allowed = allowed(sender);
        if (allowed.isEmpty()) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }
        sender.sendMessage(plugin.getMessage("admin-usage", "%subcommands%", String.join("|", allowed)));
    }

    private static List<String> allowed(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Sub sub : SUBS) {
            if (sender.hasPermission(sub.permission())) {
                names.add(sub.name());
            }
        }
        return names;
    }

    private static Sub find(String name) {
        for (Sub sub : SUBS) {
            if (sub.name().equalsIgnoreCase(name)) {
                return sub;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return allowed(sender).stream().filter(name -> name.startsWith(prefix)).toList();
    }
}
