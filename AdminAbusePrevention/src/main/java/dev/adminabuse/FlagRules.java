package dev.adminabuse;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Decides which items are "worth taking OP away for", and how to describe an item in chat. */
public final class FlagRules {

    private final Set<String> exact = new HashSet<>();
    private final List<String> prefixes = new ArrayList<>();
    private final List<String> suffixes = new ArrayList<>();

    public void load(FileConfiguration config) {
        exact.clear();
        prefixes.clear();
        suffixes.clear();
        for (String s : config.getStringList("flagged.materials")) exact.add(s.trim().toUpperCase(Locale.ROOT));
        for (String s : config.getStringList("flagged.tier-prefixes")) prefixes.add(s.trim().toUpperCase(Locale.ROOT));
        for (String s : config.getStringList("flagged.tier-suffixes")) suffixes.add(s.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isFlagged(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        if (exact.contains(name)) return true;
        for (String p : prefixes) {
            if (!name.startsWith(p)) continue;
            for (String s : suffixes) {
                if (name.endsWith(s)) return true;
            }
        }
        return false;
    }

    /** "Diamond Chestplate", "Potion (Strength) x3". */
    public static String describe(ItemStack item) {
        String base = pretty(item.getType().name());
        if (item.getItemMeta() instanceof PotionMeta meta) {
            PotionType type = meta.getBasePotionType();
            if (type != null) {
                base += " (" + pretty(type.getKey().getKey()) + ")";
            }
        }
        int amount = item.getAmount();
        return amount > 1 ? base + " x" + amount : base;
    }

    public static String pretty(String raw) {
        StringBuilder out = new StringBuilder();
        for (String part : raw.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
