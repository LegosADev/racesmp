package com.julian.races;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Piglin barter table. Each entry has a chance in percent. If the chances don't add up to 100,
 * whatever is left over is a roll that gives nothing (the default table adds up to 99%, so 1% of
 * rolls give nothing).
 */
public final class PiglinLoot {

    private record Entry(Material material, int amount, double chance) {}

    private final List<Entry> entries = new ArrayList<>();

    public void load(RacesPlugin plugin) {
        entries.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("piglin.loot");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection e = sec.getConfigurationSection(key);
            if (e == null) continue;
            Material m = Material.matchMaterial(e.getString("item", key));
            if (m == null) {
                plugin.getLogger().warning("Unknown piglin loot item: " + e.getString("item", key));
                continue;
            }
            entries.add(new Entry(m, Math.max(1, e.getInt("amount", 1)), Math.max(0, e.getDouble("chance", 0))));
        }
        double total = entries.stream().mapToDouble(Entry::chance).sum();
        plugin.getLogger().info("Loaded " + entries.size() + " piglin barter entries (total chance " + total + "%).");
    }

    public ItemStack roll(Random random) {
        double total = entries.stream().mapToDouble(Entry::chance).sum();
        double space = Math.max(100.0, total);
        double r = random.nextDouble() * space;
        double acc = 0;
        for (Entry e : entries) {
            acc += e.chance();
            if (r < acc) return new ItemStack(e.material(), e.amount());
        }
        return null;
    }
}
