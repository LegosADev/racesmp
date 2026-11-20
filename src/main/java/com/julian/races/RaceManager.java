package com.julian.races;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

/** Stores which race each player has, who they trust, and applies a race's passive stats. */
public final class RaceManager {

    private final RacesPlugin plugin;
    private final File file;
    private final Map<UUID, Race> races = new HashMap<>();
    private final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    private final Random random = new Random();

    public RaceManager(RacesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        races.clear();
        trusted.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Race r = Race.fromId(players.getString(key + ".race"));
                if (r != null) races.put(id, r);
                Set<UUID> t = new HashSet<>();
                for (String s : players.getStringList(key + ".trusted")) {
                    try {
                        t.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!t.isEmpty()) trusted.put(id, t);
            }
        }
        plugin.cooldowns().load(yaml);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<UUID> all = new HashSet<>(races.keySet());
        all.addAll(trusted.keySet());
        for (UUID id : all) {
            Race r = races.get(id);
            if (r != null) yaml.set("players." + id + ".race", r.id());
            Set<UUID> t = trusted.get(id);
            if (t != null && !t.isEmpty()) {
                yaml.set("players." + id + ".trusted", t.stream().map(UUID::toString).toList());
            }
        }
        plugin.cooldowns().save(yaml);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ races

    public Race getRace(Player p) {
        return races.get(p.getUniqueId());
    }

    public Race getRace(UUID id) {
        return races.get(id);
    }

    public boolean is(Player p, Race r) {
        return races.get(p.getUniqueId()) == r;
    }

    public Race randomJoinRace() {
        List<Race> pool = Race.joinRaces();
        return pool.get(random.nextInt(pool.size()));
    }

    /** Gives a race to a player, cleaning up the previous one. */
    public void setRace(Player p, Race race, boolean announce) {
        Race old = races.get(p.getUniqueId());
        if (old != null && old != race) cleanup(p, old);
        races.put(p.getUniqueId(), race);
        plugin.abilities().clearPending(p);
        apply(p, true);
        save();
        if (announce) announce(p, race);
    }

    public void announce(Player p, Race race) {
        p.showTitle(Title.title(
                Component.text(race.displayName(), race.color()),
                Component.text("is your race", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        sendInfo(p, race);
    }

    public void sendInfo(Player p, Race race) {
        p.sendMessage(Component.text("━━━━━━ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(race.displayName(), race.color()))
                .append(Component.text(" ━━━━━━", NamedTextColor.DARK_GRAY)));
        for (String line : race.description()) {
            p.sendMessage(Component.text(" • " + line, NamedTextColor.GRAY));
        }
        p.sendMessage(Component.text(" Ability (" + race.abilityName() + "): ", NamedTextColor.YELLOW)
                .append(Component.text("sneak + press ", NamedTextColor.WHITE))
                .append(Component.keybind("key.swapOffhand", NamedTextColor.AQUA))
                .append(Component.text(" (your swap-offhand key)", NamedTextColor.WHITE)));
    }

    public double hearts(Race r) {
        return plugin.getConfig().getDouble("hearts." + r.id(), r.defaultHearts());
    }

    // ------------------------------------------------------------------ trust

    public boolean isTrusted(UUID owner, UUID other) {
        Set<UUID> t = trusted.get(owner);
        return t != null && t.contains(other);
    }

    public boolean trust(UUID owner, UUID other) {
        boolean added = trusted.computeIfAbsent(owner, u -> new HashSet<>()).add(other);
        save();
        return added;
    }

    public boolean untrust(UUID owner, UUID other) {
        Set<UUID> t = trusted.get(owner);
        boolean removed = t != null && t.remove(other);
        save();
        return removed;
    }

    public Set<UUID> trustedOf(UUID owner) {
        return trusted.getOrDefault(owner, Collections.emptySet());
    }

    // ------------------------------------------------------------------ applying passives

    /** Permanent potion effects per race. */
    public static List<PotionEffectType> permanentEffects(Race r) {
        return switch (r) {
            case SHULKER -> List.of(PotionEffectType.SLOWNESS);
            case WARDEN -> List.of(PotionEffectType.BLINDNESS);
            case ZOMBIE -> List.of(PotionEffectType.HUNGER, PotionEffectType.REGENERATION);
            case BLAZEN -> List.of(PotionEffectType.FIRE_RESISTANCE);
            case ELDER_GUARDIAN -> List.of(PotionEffectType.WATER_BREATHING);
            case DRAGON -> List.of(PotionEffectType.SLOWNESS);
            default -> List.of();
        };
    }

    /**
     * Applies attribute modifiers, permanent effects and soulbound items. Safe to call repeatedly;
     * the passive task calls it every second so that milk / respawns / relogs can't remove anything.
     */
    public void apply(Player p, boolean fresh) {
        Race r = getRace(p);
        if (r == null) return;

        double extraHealth = hearts(r) * 2 - 20;
        setModifier(p, Attribute.MAX_HEALTH, Keys.HEALTH, extraHealth, AttributeModifier.Operation.ADD_NUMBER);

        double scale = switch (r) {
            case SPIDER -> -0.15;                       // 15% smaller hitbox
            case ENDER -> (1.8 + 0.5) / 1.8 - 1.0;       // 0.5 blocks taller (2.3 blocks)
            default -> 0;
        };
        setModifier(p, Attribute.SCALE, Keys.SCALE, scale, AttributeModifier.Operation.ADD_SCALAR);

        double speed = r == Race.CAT ? 0.125 : 0;
        setModifier(p, Attribute.MOVEMENT_SPEED, Keys.SPEED, speed, AttributeModifier.Operation.ADD_SCALAR);

        double reach = switch (r) {
            case PIGLIN -> -0.3;
            case ENDER -> 0.3;
            default -> 0;
        };
        setModifier(p, Attribute.BLOCK_INTERACTION_RANGE, Keys.BLOCK_REACH, reach, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.ENTITY_INTERACTION_RANGE, Keys.ENTITY_REACH, reach, AttributeModifier.Operation.ADD_NUMBER);

        AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            if (fresh && p.getHealth() > maxHp.getValue()) p.setHealth(maxHp.getValue());
            else if (p.getHealth() > maxHp.getValue()) p.setHealth(maxHp.getValue());
        }

        for (PotionEffectType type : permanentEffects(r)) {
            PotionEffect current = p.getPotionEffect(type);
            if (current == null || current.getAmplifier() < 0) {
                p.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, 0, true, false, true));
            }
        }

        giveSoulItems(p, r);
    }

    private void setModifier(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(key);
        if (existing != null && Math.abs(existing.getAmount() - amount) < 1e-9 && existing.getOperation() == op) return;
        if (existing != null) inst.removeModifier(key);
        if (Math.abs(amount) > 1e-9) inst.addTransientModifier(new AttributeModifier(key, amount, op));
    }

    /** Removes everything the given (old) race added. */
    public void cleanup(Player p, Race old) {
        for (Attribute a : List.of(Attribute.MAX_HEALTH, Attribute.SCALE, Attribute.MOVEMENT_SPEED,
                Attribute.BLOCK_INTERACTION_RANGE, Attribute.ENTITY_INTERACTION_RANGE, Attribute.ATTACK_DAMAGE)) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) continue;
            inst.removeModifier(Keys.HEALTH);
            inst.removeModifier(Keys.SCALE);
            inst.removeModifier(Keys.SPEED);
            inst.removeModifier(Keys.BLOCK_REACH);
            inst.removeModifier(Keys.ENTITY_REACH);
            inst.removeModifier(Keys.ZOMBIE_FIST);
            inst.removeModifier(Keys.WEB_SPEED);
        }
        for (PotionEffectType type : permanentEffects(old)) {
            PotionEffect e = p.getPotionEffect(type);
            if (e != null && e.isInfinite()) p.removePotionEffect(type);
        }
        removeForeignSoulItems(p, null);
    }

    /** Makes sure the race's soulbound items exist exactly once. */
    public void giveSoulItems(Player p, Race r) {
        PlayerInventory inv = p.getInventory();
        switch (r) {
            case CAT -> {
                if (!has(p, Items.NINE_LIVES)) Util.giveOrDrop(p, Items.nineLives());
            }
            case SKELETON -> {
                if (!has(p, Items.SKELETON_BOW)) Util.giveOrDrop(p, Items.skeletonBow());
                if (!has(p, Items.SKELETON_ARROW)) Util.giveOrDrop(p, Items.skeletonArrow());
            }
            case SNOWGOLEM -> {
                ItemStack helmet = inv.getHelmet();
                if (!Items.isSoul(helmet, Items.GOLEM_PUMPKIN)) {
                    removeAll(p, Items.GOLEM_PUMPKIN);
                    if (helmet != null && !helmet.getType().isAir()) Util.giveOrDrop(p, helmet);
                    inv.setHelmet(Items.golemPumpkin());
                }
            }
            case ELARYAN -> {
                ItemStack chest = inv.getChestplate();
                if (!Items.isSoul(chest, Items.ELARYAN_ELYTRA)) {
                    removeAll(p, Items.ELARYAN_ELYTRA);
                    if (chest != null && !chest.getType().isAir()) Util.giveOrDrop(p, chest);
                    inv.setChestplate(Items.elaryanElytra());
                }
            }
            default -> {
            }
        }
    }

    /** True if the soul item is anywhere on the player: inventory, armor, offhand, cursor or crafting grid. */
    public boolean has(Player p, String soulId) {
        for (ItemStack it : p.getInventory().getContents()) if (Items.isSoul(it, soulId)) return true;
        if (Items.isSoul(p.getItemOnCursor(), soulId)) return true;
        if (p.getOpenInventory().getTopInventory() instanceof org.bukkit.inventory.CraftingInventory ci) {
            for (ItemStack it : ci.getMatrix()) if (Items.isSoul(it, soulId)) return true;
        }
        return false;
    }

    private void removeAll(Player p, String soulId) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (Items.isSoul(contents[i], soulId)) inv.setItem(i, null);
        }
    }

    /** Removes soul items that don't belong to the player's current race (or all, if race is null). */
    public void removeForeignSoulItems(Player p, Race current) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            String id = Items.soulId(contents[i]);
            if (id != null && Items.ownerOf(id) != current) inv.setItem(i, null);
        }
        String cursor = Items.soulId(p.getItemOnCursor());
        if (cursor != null && Items.ownerOf(cursor) != current) p.setItemOnCursor(null);
    }

    public void applyAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) apply(p, false);
    }
}
