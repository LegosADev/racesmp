package com.julian.races;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Every race in the plugin. "Join" races are handed out at random (equal chance) the first time a
 * player joins. "Crafted" races are never handed out; they are obtained by crafting their essence.
 */
public enum Race {

    // ------------------------------------------------------------------ join races
    BRUTE("Brute", 0x9C4A1A, 13, false, Material.NETHERITE_AXE,
            "Axe Smash",
            "13 hearts",
            "Can't use diamond weapons (netherite is fine)",
            "Armor Protection is capped at III",
            "Ability: a giant axe smashes the ground, 3 hearts to everyone nearby (90s)"),
    PIGLIN("Piglin", 0xF2C14E, 10, false, Material.GOLD_BLOCK,
            "Barter",
            "Can't use diamond armor or diamond tools (netherite is fine)",
            "Gold armor gets Protection II + Unbreaking X while worn",
            "0.3 blocks less reach",
            "Ability: consume 1 gold block, roll the barter table 4 times (45s)"),
    SPIDER("Spider", 0x5A3D2B, 10, false, Material.COBWEB,
            "Venom Strike",
            "Can carry 2 stacks of cobwebs (other races: 1)",
            "15% smaller hitbox, 50% faster through cobwebs",
            "Can't hold shields in the offhand",
            "Ability: next hit applies Poison III (60s)"),
    CAT("Cat", 0xE89B3C, 9, false, Material.TOTEM_OF_UNDYING,
            "Pounce",
            "9 hearts, 12.5% faster, no fall damage",
            "9 Lives: an unbreakable totem with a 30 minute cooldown",
            "Ability: leap 2-4 blocks where you look, then Speed III for 5s (25s)"),
    ENDER("Ender", 0x8E44AD, 10, false, Material.ENDER_PEARL,
            "Blink",
            "0.5 blocks taller, 0.3 blocks more reach",
            "Projectiles never hit or hurt you",
            "Water hurts: 1 heart on contact, then 1 heart per second",
            "Ability: instantly teleport 10 blocks where you look (45s)"),
    SHULKER("Shulker", 0xA77BCA, 10, false, Material.SHULKER_SHELL,
            "Shell Storage",
            "Permanent Slowness I",
            "1.5% chance per hit to give Levitation (2s)",
            "On death each stored stack has a 10% chance to stay",
            "Ability: open your personal shulker storage (1s)"),
    GUARDIAN("Guardian", 0x4FB3A9, 12, false, Material.PRISMARINE_SHARD,
            "Guardian's Gaze",
            "12 hearts, built-in Thorns II",
            "Can't use wind charges",
            "Ability: Mining Fatigue IV to enemies in 20 blocks for 10s (3 min)",
            "  + a 4s chest laser, half a heart per second (120s)"),
    WARDEN("Warden", 0x0F4C5C, 15, false, Material.SCULK_SHRIEKER,
            "Sonic Boom",
            "15 hearts, permanently blind",
            "Ability: 2s charge, then a sonic boom (max 4 hearts)",
            "  and Darkness to everyone within 5 blocks (90s)"),
    SNOWGOLEM("Snow Golem", 0xDDF3FF, 10, false, Material.CARVED_PUMPKIN,
            "Frostbite",
            "Wears an unbreakable cursed pumpkin with Protection X",
            "Fire Resistance is removed automatically",
            "Ability: freeze your target for 2s and blast snowballs for 2s;",
            "  anyone within 2 blocks gets frozen (60s)"),
    SKELETON("Skeleton", 0xD9D9D9, 8, false, Material.BOW,
            "Seeker Shot",
            "8 hearts, half damage from projectiles",
            "Comes with a Power V / Punch / Flame / Piercing / Infinity bow",
            "Holding a bow gives Speed II",
            "Ability: next arrow homes in, glows the target, +2 hearts, ignores shields (45s)"),
    ZOMBIE("Zombie", 0x5E8C3A, 10, false, Material.ROTTEN_FLESH,
            "Undying Rage",
            "Permanent Hunger I and Regeneration I",
            "Empty-hand punches deal 1 heart",
            "Ability: Regeneration VIII + Weakness I for 1.5s (2 min)"),
    BLAZEN("Blazen", 0xFF7A1A, 11, false, Material.BLAZE_ROD,
            "Flame Wave",
            "11 hearts, permanent Fire Resistance",
            "Water hurts: half a heart per second",
            "Ability: a row of fire that ignores armor and Fire Resistance (90s)"),

    // ------------------------------------------------------------------ crafted races
    ELDER_GUARDIAN("Elder Guardian", 0xB9C7B0, 12, true, Material.HEART_OF_THE_SEA,
            "Elder Beam",
            "12 hearts, permanent Water Breathing",
            "Ability: 4s beam, 1 heart per second + 6 min Mining Fatigue (60s)"),
    WITHERBORN("Witherborn", 0x3B3B3B, 12, true, Material.WITHER_SKELETON_SKULL,
            "Dark Infusion",
            "12 hearts, every hit gives Wither I for 15s",
            "Ability: next hit deals +2 hearts and Wither II for 5s (45s)"),
    ELARYAN("Elaryan", 0x7FD1FF, 13, true, Material.ELYTRA,
            "Skyburst",
            "13 hearts, a permanent elytra replaces your chestplate",
            "Ability: a flight-duration-5 rocket boost (15s)"),
    DRAGON("Dragon", 0xC04CFD, 15, true, Material.DRAGON_EGG,
            "Dragon's Breath",
            "15 hearts, permanent Slowness I",
            "Ability: shoot a dragon fireball that deals 4 hearts (90s)");

    private final String displayName;
    private final TextColor color;
    private final double defaultHearts;
    private final boolean crafted;
    private final Material icon;
    private final String abilityName;
    private final List<String> description;

    Race(String displayName, int rgb, double defaultHearts, boolean crafted, Material icon,
         String abilityName, String... description) {
        this.displayName = displayName;
        this.color = TextColor.color(rgb);
        this.defaultHearts = defaultHearts;
        this.crafted = crafted;
        this.icon = icon;
        this.abilityName = abilityName;
        this.description = List.of(description);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String displayName() { return displayName; }
    public TextColor color() { return color; }
    public double defaultHearts() { return defaultHearts; }
    public boolean isCrafted() { return crafted; }
    public Material icon() { return icon; }
    public String abilityName() { return abilityName; }
    public List<String> description() { return description; }

    public static Race fromId(String id) {
        if (id == null) return null;
        String norm = id.toLowerCase(Locale.ROOT).replace(' ', '_').replace("-", "_");
        for (Race r : values()) {
            if (r.id().equals(norm) || r.displayName.equalsIgnoreCase(id)) return r;
        }
        if (norm.equals("snow_golem")) return SNOWGOLEM;
        if (norm.equals("elder")) return ELDER_GUARDIAN;
        return null;
    }

    public static List<Race> joinRaces() {
        return Arrays.stream(values()).filter(r -> !r.crafted).toList();
    }

    public static List<Race> craftedRaces() {
        return Arrays.stream(values()).filter(r -> r.crafted).toList();
    }
}
