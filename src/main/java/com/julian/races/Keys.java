package com.julian.races;

import org.bukkit.NamespacedKey;

/** All NamespacedKeys used by the plugin (attribute modifiers, item tags, entity tags). */
public final class Keys {
    private Keys() {}

    // attribute modifiers
    public static NamespacedKey HEALTH, SCALE, SPEED, BLOCK_REACH, ENTITY_REACH, ZOMBIE_FIST, WEB_SPEED,
            FREEZE_SPEED, FREEZE_JUMP;

    // item tags
    public static NamespacedKey SOUL_ITEM, ESSENCE, ADJUSTED_BY, ADJUSTED_ORIGINAL;

    // entity tags
    public static NamespacedKey HOMING_ARROW, DRAGON_FIREBALL, GOLEM_SNOWBALL, ABILITY_ENTITY;

    // cooldown group for the 9 Lives totem overlay
    public static NamespacedKey NINE_LIVES_GROUP;

    static void init(RacesPlugin plugin) {
        HEALTH = new NamespacedKey(plugin, "race_health");
        SCALE = new NamespacedKey(plugin, "race_scale");
        SPEED = new NamespacedKey(plugin, "race_speed");
        BLOCK_REACH = new NamespacedKey(plugin, "race_block_reach");
        ENTITY_REACH = new NamespacedKey(plugin, "race_entity_reach");
        ZOMBIE_FIST = new NamespacedKey(plugin, "zombie_fist");
        WEB_SPEED = new NamespacedKey(plugin, "spider_web_speed");
        FREEZE_SPEED = new NamespacedKey(plugin, "frozen_speed");
        FREEZE_JUMP = new NamespacedKey(plugin, "frozen_jump");

        SOUL_ITEM = new NamespacedKey(plugin, "soul_item");
        ESSENCE = new NamespacedKey(plugin, "race_essence");
        ADJUSTED_BY = new NamespacedKey(plugin, "adjusted_by");
        ADJUSTED_ORIGINAL = new NamespacedKey(plugin, "adjusted_original");

        HOMING_ARROW = new NamespacedKey(plugin, "homing_arrow");
        DRAGON_FIREBALL = new NamespacedKey(plugin, "dragon_fireball");
        GOLEM_SNOWBALL = new NamespacedKey(plugin, "golem_snowball");
        ABILITY_ENTITY = new NamespacedKey(plugin, "ability_entity");

        NINE_LIVES_GROUP = new NamespacedKey(plugin, "nine_lives");
    }
}
