package dev.adminabuse;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AbuseListener implements Listener {

    /** An item the player just picked up from one of their own slots (so putting it back is not a "take"). */
    private record Held(ItemStack item, long time) {
    }

    private static final long HELD_WINDOW_MS = 120_000L;
    private static final long DUPLICATE_MS = 400L;

    private final AdminAbusePrevention plugin;
    private final FlagRules rules;

    private final Map<UUID, Held> held = new HashMap<>();
    private final Map<UUID, String> lastTakeKey = new HashMap<>();
    private final Map<UUID, Long> lastTakeTime = new HashMap<>();
    private final Set<UUID> internalChange = new HashSet<>();
    private final Set<UUID> punishing = new HashSet<>();

    public AbuseListener(AdminAbusePrevention plugin, FlagRules rules) {
        this.plugin = plugin;
        this.rules = rules;
    }

    // ------------------------------------------------------------------ gamemode changes

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        if (internalChange.contains(p.getUniqueId())) return; // our own punishment
        if (plugin.isExcused(p)) return;
        if (!plugin.getConfig().getBoolean("announce.gamemode-changes", true)) return;

        GameMode from = p.getGameMode();
        GameMode to = e.getNewGameMode();
        if (from == to) return;

        Bukkit.broadcast(plugin.format(plugin.message("gamemode-change"),
                "player", p.getName(),
                "from", FlagRules.pretty(from.name()),
                "to", FlagRules.pretty(to.name())));
    }

    // ------------------------------------------------------------------ creative menu takes

    /**
     * Fires when an item is placed into (or dropped from) the creative inventory. getCursor() is
     * the new item, getCurrentItem() is what was in the slot before.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (plugin.isExcused(p)) return;

        UUID id = p.getUniqueId();
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        long now = System.currentTimeMillis();

        // An item left a slot (picked up, moved or deleted). Remember it, so putting it back is not a "take".
        if (cursor == null || cursor.getType().isAir()) {
            if (current != null && !current.getType().isAir()) {
                held.put(id, new Held(current.clone(), now));
            }
            return;
        }

        // The client re-sends unchanged slots; nothing happened.
        if (current != null && current.equals(cursor)) return;

        // Moving an item they already owned.
        Held h = held.get(id);
        if (h != null && now - h.time() <= HELD_WINDOW_MS
                && h.item().isSimilar(cursor) && cursor.getAmount() <= h.item().getAmount()) {
            held.remove(id); // one pick-up pays for one placement; a second copy would be a real take
            return;
        }

        boolean flagged = rules.isFlagged(cursor);
        boolean punish = flagged && plugin.getConfig().getBoolean("punishment.enabled", true);

        if (punish) {
            punish(p, cursor.clone(), e);
            return;
        }

        if (plugin.getConfig().getBoolean("announce.creative-takes", true) && !isDuplicate(id, cursor, now)) {
            Bukkit.broadcast(plugin.format(plugin.message("creative-take"),
                    "player", p.getName(),
                    "item", FlagRules.describe(cursor)));
        }
    }

    private boolean isDuplicate(UUID id, ItemStack item, long now) {
        String key = item.getType().name() + "x" + item.getAmount();
        Long last = lastTakeTime.get(id);
        boolean dup = key.equals(lastTakeKey.get(id)) && last != null && now - last < DUPLICATE_MS;
        lastTakeKey.put(id, key);
        lastTakeTime.put(id, now);
        return dup;
    }

    // ------------------------------------------------------------------ punishment

    private void punish(Player p, ItemStack item, InventoryCreativeEvent e) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        FileConfiguration cfg = plugin.getConfig();

        if (cfg.getBoolean("punishment.cancel-item", true)) {
            e.setCancelled(true);
        }
        if (!punishing.add(id)) return; // already being punished this tick

        String described = FlagRules.describe(item);

        // Run after this event has finished, on the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Player online = Bukkit.getPlayer(id);

                // 1. survival
                if (online != null) {
                    online.setItemOnCursor(null);
                    if (cfg.getBoolean("punishment.set-survival", true)) {
                        internalChange.add(id);
                        try {
                            online.setGameMode(GameMode.SURVIVAL);
                        } finally {
                            internalChange.remove(id);
                        }
                    }
                }

                // 2. the commands (LuckPerms group, deop), run by the console
                for (String template : cfg.getStringList("punishment.commands")) {
                    String command = template.replace("%player%", name);
                    boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    plugin.getLogger().info("Ran /" + command + (ok ? "" : "  (the command reported failure)"));
                }

                // 3. backup: make sure they are really not OP
                if (cfg.getBoolean("punishment.remove-op", true)) {
                    Player again = Bukkit.getPlayer(id);
                    if (again != null) {
                        again.setOp(false);
                        again.updateInventory();
                    } else {
                        Bukkit.getOfflinePlayer(id).setOp(false);
                    }
                }

                // 4. tell everyone
                Bukkit.broadcast(plugin.format(plugin.message("violation"),
                        "player", name,
                        "item", described));
                logViolation(name, described);
            } finally {
                punishing.remove(id);
            }
        });
    }

    private void logViolation(String name, String item) {
        if (!plugin.getConfig().getBoolean("log-violations", true)) return;
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Path file = plugin.getDataFolder().toPath().resolve("violations.log");
            String line = "[" + LocalDateTime.now() + "] " + name + " took " + item + " from creative\n";
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write violations.log: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        held.remove(id);
        lastTakeKey.remove(id);
        lastTakeTime.remove(id);
        internalChange.remove(id);
    }
}
