package com.julian.races;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Util {
    private Util() {}

    public static final Component PREFIX = Component.text("[Races] ", NamedTextColor.GOLD);

    public static void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(PREFIX.append(Component.text(text, color)));
    }

    public static void actionBar(Player p, String text, NamedTextColor color) {
        p.sendActionBar(Component.text(text, color));
    }

    /** Plain, non-italic text for item names and lore. */
    public static Component plain(String text, net.kyori.adventure.text.format.TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Restrictions only apply to players in survival / adventure. */
    public static boolean restricted(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    /**
     * True if {@code target} is something an ability used by {@code owner} should hit:
     * a living, non armor-stand entity that is not the owner, and (for players) not someone the
     * owner has /trusted and not in creative/spectator.
     */
    public static boolean isEnemy(Player owner, Entity target) {
        if (!(target instanceof LivingEntity le)) return false;
        if (target.equals(owner) || le.isDead() || target instanceof ArmorStand) return false;
        if (target instanceof Player tp) {
            if (tp.getGameMode() == GameMode.CREATIVE || tp.getGameMode() == GameMode.SPECTATOR) return false;
            return !RacesPlugin.get().races().isTrusted(owner.getUniqueId(), tp.getUniqueId());
        }
        return true;
    }

    public static List<LivingEntity> enemiesNear(Player owner, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!isEnemy(owner, e)) continue;
            if (e.getLocation().distanceSquared(center) <= radius * radius
                    || ((LivingEntity) e).getEyeLocation().distanceSquared(center) <= radius * radius) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    /**
     * Ability damage. Uses a damage type that ignores armor so the number of hearts matches the
     * description, and credits the attacking player for kills.
     */
    public static void abilityDamage(LivingEntity target, double amount, Player attacker, DamageType type,
                                     Entity direct) {
        DamageSource.Builder b = DamageSource.builder(type);
        if (attacker != null) b.withCausingEntity(attacker);
        if (direct != null) b.withDirectEntity(direct);
        else if (attacker != null) b.withDirectEntity(attacker);
        target.setNoDamageTicks(0);
        target.damage(amount, b.build());
    }

    public static void abilityDamage(LivingEntity target, double amount, Player attacker) {
        abilityDamage(target, amount, attacker, DamageType.INDIRECT_MAGIC, null);
    }

    /** Puts an item into the player's inventory, dropping whatever does not fit at their feet. */
    public static void giveOrDrop(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItem(p.getLocation(), rest, it -> it.setPickupDelay(40));
        }
    }

    /** Distance from point to the segment a-b. */
    public static double distToSegment(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1e-9) return p.distance(a);
        double t = Math.max(0, Math.min(1, p.clone().subtract(a).dot(ab) / len2));
        return p.distance(a.clone().add(ab.multiply(t)));
    }

    public static Vector flatForward(Location loc) {
        Vector f = loc.getDirection().setY(0);
        if (f.lengthSquared() < 1e-6) {
            double yaw = Math.toRadians(loc.getYaw());
            f = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return f.normalize();
    }
}
