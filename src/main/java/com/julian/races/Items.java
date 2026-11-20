package com.julian.races;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds and identifies the plugin's special items. */
public final class Items {
    private Items() {}

    public static final String NINE_LIVES = "nine_lives";
    public static final String SKELETON_BOW = "skeleton_bow";
    public static final String SKELETON_ARROW = "skeleton_arrow";
    public static final String GOLEM_PUMPKIN = "golem_pumpkin";
    public static final String ELARYAN_ELYTRA = "elaryan_elytra";

    /** Which race owns which soul item. */
    public static Race ownerOf(String soulId) {
        return switch (soulId) {
            case NINE_LIVES -> Race.CAT;
            case SKELETON_BOW, SKELETON_ARROW -> Race.SKELETON;
            case GOLEM_PUMPKIN -> Race.SNOWGOLEM;
            case ELARYAN_ELYTRA -> Race.ELARYAN;
            default -> null;
        };
    }

    public static String soulId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.SOUL_ITEM, PersistentDataType.STRING);
    }

    public static boolean isSoul(ItemStack item) {
        return soulId(item) != null;
    }

    public static boolean isSoul(ItemStack item, String id) {
        return id.equals(soulId(item));
    }

    private static Registry<Enchantment> enchants() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    }

    private static ItemStack soul(Material mat, String id, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(name);
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.SOUL_ITEM, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack nineLives() {
        List<Component> lore = new ArrayList<>();
        lore.add(Util.plain("A cat always lands on its feet.", NamedTextColor.GRAY));
        lore.add(Util.plain("Hold it like a totem. It is never used up,", NamedTextColor.GRAY));
        lore.add(Util.plain("but needs 30 minutes to recover.", NamedTextColor.GRAY));
        lore.add(Util.plain("Soulbound to the Cat race", NamedTextColor.DARK_PURPLE));
        ItemStack item = soul(Material.TOTEM_OF_UNDYING, NINE_LIVES,
                Component.text("9 Lives", Race.CAT.color()), lore);
        ItemMeta meta = item.getItemMeta();
        // resource-pack hook: assets/minecraft/items/totem_of_undying.json selects on this string
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of("races:nine_lives"));
        meta.setCustomModelDataComponent(cmd);
        // own cooldown group so the 30 minute recharge shows as an overlay on this item only
        org.bukkit.inventory.meta.components.UseCooldownComponent cd = meta.getUseCooldown();
        cd.setCooldownSeconds(1f);
        cd.setCooldownGroup(Keys.NINE_LIVES_GROUP);
        meta.setUseCooldown(cd);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack skeletonBow() {
        List<Component> lore = new ArrayList<>();
        lore.add(Util.plain("Soulbound to the Skeleton race", NamedTextColor.DARK_PURPLE));
        ItemStack item = soul(Material.BOW, SKELETON_BOW, Component.text("Bone Bow", Race.SKELETON.color()), lore);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.POWER, 5, true);
        meta.addEnchant(Enchantment.PUNCH, 1, true);
        meta.addEnchant(Enchantment.FLAME, 1, true);
        meta.addEnchant(Enchantment.PIERCING, 1, true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Infinity needs one arrow in the inventory to draw the bow; this one is soulbound. */
    public static ItemStack skeletonArrow() {
        List<Component> lore = new ArrayList<>();
        lore.add(Util.plain("Lets your Bone Bow fire (Infinity)", NamedTextColor.GRAY));
        lore.add(Util.plain("Soulbound to the Skeleton race", NamedTextColor.DARK_PURPLE));
        return soul(Material.ARROW, SKELETON_ARROW, Component.text("Bone Arrow", Race.SKELETON.color()), lore);
    }

    public static ItemStack golemPumpkin() {
        List<Component> lore = new ArrayList<>();
        lore.add(Util.plain("Soulbound to the Snow Golem race", NamedTextColor.DARK_PURPLE));
        ItemStack item = soul(Material.CARVED_PUMPKIN, GOLEM_PUMPKIN,
                Component.text("Golem Head", Race.SNOWGOLEM.color()), lore);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.PROTECTION, 10, true);
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack elaryanElytra() {
        List<Component> lore = new ArrayList<>();
        lore.add(Util.plain("Soulbound to the Elaryan race", NamedTextColor.DARK_PURPLE));
        ItemStack item = soul(Material.ELYTRA, ELARYAN_ELYTRA,
                Component.text("Elaryan Wings", Race.ELARYAN.color()), lore);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------ crafted-race essences

    public static ItemStack essence(Race race) {
        ItemStack item = new ItemStack(race.icon());
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text(race.displayName() + " Essence", race.color()));
        List<Component> lore = new ArrayList<>();
        for (String line : race.description()) lore.add(Util.plain(line, NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Util.plain("Right-click to become a " + race.displayName(), NamedTextColor.YELLOW));
        lore.add(Util.plain("(replaces your current race)", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(Keys.ESSENCE, PersistentDataType.STRING, race.id());
        item.setItemMeta(meta);
        return item;
    }

    public static Race essenceRace(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(Keys.ESSENCE, PersistentDataType.STRING);
        return Race.fromId(id);
    }

    // ------------------------------------------------------------ temporary enchant adjustments

    /**
     * Temporarily changes enchantment levels on a worn armor piece, remembering the originals so
     * {@link #restore} can undo it once the item is no longer worn by that race.
     *
     * @param targets enchantment -> level it should have while worn (0 = removed)
     * @return true if the item changed
     */
    public static boolean adjust(ItemStack item, String byRace, Map<Enchantment, Integer> targets) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String current = meta.getPersistentDataContainer().get(Keys.ADJUSTED_BY, PersistentDataType.STRING);
        if (byRace.equals(current)) return false;
        if (current != null) {
            restore(item);
            meta = item.getItemMeta();
        }
        StringBuilder orig = new StringBuilder();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> t : targets.entrySet()) {
            int now = meta.getEnchantLevel(t.getKey());
            if (now == t.getValue()) continue;
            if (orig.length() > 0) orig.append(';');
            orig.append(t.getKey().getKey()).append('=').append(now);
            if (t.getValue() <= 0) meta.removeEnchant(t.getKey());
            else meta.addEnchant(t.getKey(), t.getValue(), true);
            changed = true;
        }
        if (!changed) return false;
        meta.getPersistentDataContainer().set(Keys.ADJUSTED_BY, PersistentDataType.STRING, byRace);
        meta.getPersistentDataContainer().set(Keys.ADJUSTED_ORIGINAL, PersistentDataType.STRING, orig.toString());
        item.setItemMeta(meta);
        return true;
    }

    public static String adjustedBy(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.ADJUSTED_BY, PersistentDataType.STRING);
    }

    /** Undoes {@link #adjust}. */
    public static boolean restore(ItemStack item) {
        if (adjustedBy(item) == null) return false;
        ItemMeta meta = item.getItemMeta();
        String orig = meta.getPersistentDataContainer().get(Keys.ADJUSTED_ORIGINAL, PersistentDataType.STRING);
        if (orig != null && !orig.isEmpty()) {
            for (String part : orig.split(";")) {
                String[] kv = part.split("=");
                if (kv.length != 2) continue;
                NamespacedKey key = NamespacedKey.fromString(kv[0]);
                Enchantment ench = key == null ? null : enchants().get(key);
                if (ench == null) continue;
                int level;
                try {
                    level = Integer.parseInt(kv[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (level <= 0) meta.removeEnchant(ench);
                else meta.addEnchant(ench, level, true);
            }
        }
        meta.getPersistentDataContainer().remove(Keys.ADJUSTED_BY);
        meta.getPersistentDataContainer().remove(Keys.ADJUSTED_ORIGINAL);
        item.setItemMeta(meta);
        return true;
    }

    public static Map<Enchantment, Integer> map(Enchantment e, int level) {
        Map<Enchantment, Integer> m = new HashMap<>();
        m.put(e, level);
        return m;
    }

    // ------------------------------------------------------------ material groups

    public static boolean isDiamondWeapon(Material m) {
        String n = m.name();
        return n.equals("DIAMOND_SWORD") || n.equals("DIAMOND_AXE") || n.equals("DIAMOND_SPEAR");
    }

    public static boolean isDiamondTool(Material m) {
        String n = m.name();
        return n.startsWith("DIAMOND_") && (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE")
                || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_SPEAR"));
    }

    public static boolean isDiamondArmor(Material m) {
        String n = m.name();
        return n.startsWith("DIAMOND_") && (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS"));
    }

    public static boolean isGoldArmor(Material m) {
        String n = m.name();
        return n.startsWith("GOLDEN_") && (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS"));
    }

    public static boolean isShulkerBox(Material m) {
        return m.name().endsWith("SHULKER_BOX");
    }
}
