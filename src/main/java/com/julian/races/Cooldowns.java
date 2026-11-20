package com.julian.races;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ability cooldowns, stored as absolute expiry times so they survive relogs and restarts. */
public final class Cooldowns {

    /** Default cooldown length in seconds for every cooldown key. Overridable in config.yml. */
    public static final Map<String, Integer> DEFAULTS = new java.util.LinkedHashMap<>();
    static {
        DEFAULTS.put("brute", 90);
        DEFAULTS.put("piglin", 45);
        DEFAULTS.put("spider", 60);
        DEFAULTS.put("cat", 25);
        DEFAULTS.put("nine_lives", 1800);
        DEFAULTS.put("ender", 45);
        DEFAULTS.put("shulker", 1);
        DEFAULTS.put("guardian_fatigue", 180);
        DEFAULTS.put("guardian_beam", 120);
        DEFAULTS.put("warden", 90);
        DEFAULTS.put("snowgolem", 60);
        DEFAULTS.put("skeleton", 45);
        DEFAULTS.put("zombie", 120);
        DEFAULTS.put("blazen", 90);
        DEFAULTS.put("elder_guardian", 60);
        DEFAULTS.put("witherborn", 45);
        DEFAULTS.put("elaryan", 15);
        DEFAULTS.put("dragon", 90);
    }

    private final RacesPlugin plugin;
    private final Map<UUID, Map<String, Long>> expiries = new HashMap<>();

    public Cooldowns(RacesPlugin plugin) {
        this.plugin = plugin;
    }

    public int lengthSeconds(String key) {
        return plugin.getConfig().getInt("cooldowns." + key, DEFAULTS.getOrDefault(key, 30));
    }

    /** @return remaining milliseconds, 0 if ready */
    public long remaining(Player p, String key) {
        Map<String, Long> m = expiries.get(p.getUniqueId());
        if (m == null) return 0;
        Long until = m.get(key);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            m.remove(key);
            return 0;
        }
        return left;
    }

    public void start(Player p, String key) {
        expiries.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>())
                .put(key, System.currentTimeMillis() + lengthSeconds(key) * 1000L);
    }

    public void clear(UUID uuid) {
        expiries.remove(uuid);
    }

    public void save(YamlConfiguration yaml) {
        long now = System.currentTimeMillis();
        yaml.set("cooldowns", null);
        for (Map.Entry<UUID, Map<String, Long>> e : expiries.entrySet()) {
            for (Map.Entry<String, Long> c : e.getValue().entrySet()) {
                if (c.getValue() > now) yaml.set("cooldowns." + e.getKey() + "." + c.getKey(), c.getValue());
            }
        }
    }

    public void load(YamlConfiguration yaml) {
        expiries.clear();
        ConfigurationSection sec = yaml.getConfigurationSection("cooldowns");
        if (sec == null) return;
        for (String uuid : sec.getKeys(false)) {
            ConfigurationSection ps = sec.getConfigurationSection(uuid);
            if (ps == null) continue;
            try {
                UUID id = UUID.fromString(uuid);
                Map<String, Long> m = new HashMap<>();
                for (String key : ps.getKeys(false)) m.put(key, ps.getLong(key));
                expiries.put(id, m);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static String format(long ms) {
        long s = (ms + 999) / 1000;
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }
}
