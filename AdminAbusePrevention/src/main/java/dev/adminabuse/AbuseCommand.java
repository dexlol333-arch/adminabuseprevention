package dev.adminabuse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AbuseCommand implements CommandExecutor, TabCompleter {

    private final AdminAbusePrevention plugin;
    private final FlagRules rules;

    public AbuseCommand(AdminAbusePrevention plugin, FlagRules rules) {
        this.plugin = plugin;
        this.rules = rules;
    }

    private void say(CommandSender to, String legacy) {
        to.sendMessage(plugin.format(legacy));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("abuseguard.admin")) {
            say(sender, "&cYou don't have permission.");
            return true;
        }
        if (args.length == 0) {
            say(sender, "&7/" + label + " reload &8- &7reload the config");
            say(sender, "&7/" + label + " check &8- &7are you excused, and is your held item flagged?");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                say(sender, "&aConfig reloaded.");
            }
            case "check" -> {
                if (!(sender instanceof Player p)) {
                    say(sender, "&cPlayers only.");
                    return true;
                }
                boolean excused = plugin.isExcused(p);
                say(sender, "&7Excused by &f" + AdminAbusePrevention.EXCUSE_PERMISSION + "&7: "
                        + (excused ? "&ayes (you are NOT watched)" : "&cno (you ARE watched)"));
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    say(sender, "&7Hold an item to test whether it is flagged.");
                } else {
                    say(sender, "&7" + FlagRules.describe(hand) + " is "
                            + (rules.isFlagged(hand) ? "&cFLAGGED" : "&anot flagged"));
                }
            }
            default -> say(sender, "&cUnknown subcommand.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("abuseguard.admin")) {
            for (String s : List.of("reload", "check")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }
}
