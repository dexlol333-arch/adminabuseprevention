package dev.adminabuse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminAbusePrevention extends JavaPlugin {

    /** Players with this permission are completely ignored by the plugin. */
    public static final String EXCUSE_PERMISSION = "admin.abuse.prevention.excuse";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final FlagRules rules = new FlagRules();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        rules.load(getConfig());

        getServer().getPluginManager().registerEvents(new AbuseListener(this, rules), this);

        PluginCommand cmd = getCommand("abuseguard");
        if (cmd != null) {
            AbuseCommand handler = new AbuseCommand(this, rules);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("LuckPerms was not found. The 'lp' punishment command will not work and "
                    + "nobody can be excused by permission until LuckPerms is installed.");
        }
        getLogger().info("Watching all players without " + EXCUSE_PERMISSION + ".");
    }

    public void reload() {
        reloadConfig();
        rules.load(getConfig());
    }

    public boolean isExcused(Player player) {
        return player.hasPermission(EXCUSE_PERMISSION);
    }

    /** Builds a chat component: the config prefix + the template, with %placeholders% replaced. */
    public Component format(String template, String... keyValues) {
        String text = getConfig().getString("messages.prefix", "") + template;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            text = text.replace("%" + keyValues[i] + "%", keyValues[i + 1]);
        }
        return LEGACY.deserialize(text);
    }

    public String message(String key) {
        return getConfig().getString("messages." + key, "");
    }
}
