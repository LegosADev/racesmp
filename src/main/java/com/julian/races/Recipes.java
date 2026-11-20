package com.julian.races;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Crafting recipes for the crafted races, and whether each one is enabled. */
public final class Recipes {

    /** 3x3 grid of a recipe, row by row (null = empty). Used for both the real recipe and the menu. */
    public static final Map<Race, Material[]> GRIDS = new EnumMap<>(Race.class);

    static {
        Material D = Material.DIAMOND_BLOCK, P = Material.PRISMARINE, C = Material.CONDUIT;
        GRIDS.put(Race.ELDER_GUARDIAN, new Material[]{
                D, P, D,
                P, C, P,
                D, P, D});
        Material N = Material.NETHERITE_INGOT, S = Material.SOUL_SAND, W = Material.WITHER_SKELETON_SKULL;
        GRIDS.put(Race.WITHERBORN, new Material[]{
                N, S, N,
                W, W, W,
                N, S, N});
        Material M = Material.PHANTOM_MEMBRANE, F = Material.CHORUS_FRUIT;
        GRIDS.put(Race.ELARYAN, new Material[]{
                M, F, M,
                F, N, F,
                M, F, M});
        Material E = Material.DRAGON_EGG;
        GRIDS.put(Race.DRAGON, new Material[]{
                D, D, D,
                D, E, D,
                D, D, D});
    }

    private final RacesPlugin plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public Recipes(RacesPlugin plugin) {
        this.plugin = plugin;
    }

    public NamespacedKey key(Race r) {
        return new NamespacedKey(plugin, "essence_" + r.id());
    }

    public void register() {
        for (Map.Entry<Race, Material[]> en : GRIDS.entrySet()) {
            Race r = en.getKey();
            Material[] g = en.getValue();
            NamespacedKey key = key(r);
            Bukkit.removeRecipe(key);
            ShapedRecipe recipe = new ShapedRecipe(key, Items.essence(r));
            char[] letters = "abcdefghi".toCharArray();
            String[] rows = new String[3];
            for (int row = 0; row < 3; row++) {
                StringBuilder sb = new StringBuilder();
                for (int col = 0; col < 3; col++) {
                    Material m = g[row * 3 + col];
                    sb.append(m == null ? ' ' : letters[row * 3 + col]);
                }
                rows[row] = sb.toString();
            }
            recipe.shape(rows);
            for (int i = 0; i < 9; i++) if (g[i] != null) recipe.setIngredient(letters[i], g[i]);
            Bukkit.addRecipe(recipe);
            keys.add(key);
        }
    }

    public void unregister() {
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k);
        keys.clear();
    }

    public void discover(Player p) {
        List<NamespacedKey> enabled = new ArrayList<>();
        for (Race r : GRIDS.keySet()) if (isEnabled(r)) enabled.add(key(r));
        p.discoverRecipes(enabled);
    }

    public boolean isEnabled(Race r) {
        return plugin.getConfig().getBoolean("crafted-races." + r.id() + ".enabled", true);
    }

    public void setEnabled(Race r, boolean enabled) {
        plugin.getConfig().set("crafted-races." + r.id() + ".enabled", enabled);
        plugin.saveConfig();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (enabled) p.discoverRecipe(key(r));
            else p.undiscoverRecipe(key(r));
        }
    }
}
