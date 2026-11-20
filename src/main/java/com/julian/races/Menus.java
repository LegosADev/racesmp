package com.julian.races;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /recipes (everyone, read-only) and /recipe (operators, toggle crafted races). */
public final class Menus {

    public enum Type { RECIPES, RECIPE_VIEW, ADMIN }

    public static final class MenuHolder implements InventoryHolder {
        final Type type;
        final Race race;
        Inventory inventory;

        MenuHolder(Type type, Race race) {
            this.type = type;
            this.race = race;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private static final int[] RACE_SLOTS = {10, 12, 14, 16};
    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private final RacesPlugin plugin;

    public Menus(RacesPlugin plugin) {
        this.plugin = plugin;
    }

    private Inventory create(Type type, Race race, int size, Component title) {
        MenuHolder h = new MenuHolder(type, race);
        Inventory inv = Bukkit.createInventory(h, size, title);
        h.inventory = inv;
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        return inv;
    }

    private static ItemStack named(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.itemName(name);
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ /recipes

    public void openRecipes(Player p) {
        Inventory inv = create(Type.RECIPES, null, 27, Component.text("Crafted Race Recipes", NamedTextColor.DARK_PURPLE));
        List<Race> crafted = Race.craftedRaces();
        for (int i = 0; i < crafted.size() && i < RACE_SLOTS.length; i++) {
            Race r = crafted.get(i);
            ItemStack icon = Items.essence(r);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(status(r));
            lore.add(Util.plain("Click to view the recipe", NamedTextColor.AQUA));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(RACE_SLOTS[i], icon);
        }
        p.openInventory(inv);
    }

    private Component status(Race r) {
        return plugin.recipes().isEnabled(r)
                ? Util.plain("● Enabled", NamedTextColor.GREEN)
                : Util.plain("● Disabled by an operator", NamedTextColor.RED);
    }

    private void openRecipeView(Player p, Race r) {
        Inventory inv = create(Type.RECIPE_VIEW, r, 45, Component.text(r.displayName() + " Recipe", r.color()));
        Material[] grid = Recipes.GRIDS.get(r);
        for (int i = 0; i < 9; i++) {
            inv.setItem(GRID_SLOTS[i], grid[i] == null ? null : new ItemStack(grid[i]));
        }
        inv.setItem(23, named(Material.ARROW, Component.text("Crafts into", NamedTextColor.WHITE), List.of()));
        ItemStack result = Items.essence(r);
        ItemMeta meta = result.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.empty());
        lore.add(status(r));
        meta.lore(lore);
        result.setItemMeta(meta);
        inv.setItem(25, result);
        inv.setItem(40, named(Material.BARRIER, Component.text("Back", NamedTextColor.RED), List.of()));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ /recipe (admin)

    public void openAdmin(Player p) {
        Inventory inv = create(Type.ADMIN, null, 36, Component.text("Toggle Crafted Races", NamedTextColor.DARK_RED));
        List<Race> crafted = Race.craftedRaces();
        for (int i = 0; i < crafted.size() && i < RACE_SLOTS.length; i++) {
            Race r = crafted.get(i);
            boolean on = plugin.recipes().isEnabled(r);
            inv.setItem(RACE_SLOTS[i], Items.essence(r));
            inv.setItem(RACE_SLOTS[i] + 9, named(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    Component.text(r.displayName() + ": " + (on ? "ENABLED" : "DISABLED"),
                            on ? NamedTextColor.GREEN : NamedTextColor.RED),
                    List.of(Util.plain("Click to " + (on ? "disable" : "enable") + " crafting and using",
                            NamedTextColor.GRAY), Util.plain("this race's essence.", NamedTextColor.GRAY))));
        }
        inv.setItem(31, named(Material.BOOK, Component.text("Recipes", NamedTextColor.AQUA),
                List.of(Util.plain("Players can view these with /recipes", NamedTextColor.GRAY))));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ clicks

    public void click(Player p, MenuHolder holder, int slot, ClickType click) {
        List<Race> crafted = Race.craftedRaces();
        switch (holder.type) {
            case RECIPES -> {
                for (int i = 0; i < crafted.size() && i < RACE_SLOTS.length; i++) {
                    if (RACE_SLOTS[i] == slot) {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                        openRecipeView(p, crafted.get(i));
                    }
                }
            }
            case RECIPE_VIEW -> {
                if (slot == 40) {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                    openRecipes(p);
                }
            }
            case ADMIN -> {
                if (!p.hasPermission("races.admin")) {
                    p.closeInventory();
                    return;
                }
                if (slot == 31) {
                    openRecipes(p);
                    return;
                }
                for (int i = 0; i < crafted.size() && i < RACE_SLOTS.length; i++) {
                    if (RACE_SLOTS[i] == slot || RACE_SLOTS[i] + 9 == slot) {
                        Race r = crafted.get(i);
                        boolean now = !plugin.recipes().isEnabled(r);
                        plugin.recipes().setEnabled(r, now);
                        p.playSound(p.getLocation(), now ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1.2f);
                        Util.msg(p, r.displayName() + " is now " + (now ? "enabled" : "disabled") + ".",
                                now ? NamedTextColor.GREEN : NamedTextColor.RED);
                        openAdmin(p);
                    }
                }
            }
        }
    }
}
