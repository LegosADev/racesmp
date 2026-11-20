package com.julian.races;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Every active ability (sneak + swap-offhand key). */
public final class Abilities {

    private final RacesPlugin plugin;
    private final Random random = new Random();

    /** Players whose next melee hit / arrow is empowered. */
    private final Set<UUID> spiderVenom = new HashSet<>();
    private final Set<UUID> skeletonHoming = new HashSet<>();
    private final Set<UUID> witherInfusion = new HashSet<>();
    /** Frozen entities -> server tick when the freeze ends. */
    private final Map<UUID, Long> frozen = new HashMap<>();
    private long tick = 0;

    public Abilities(RacesPlugin plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                tick++;
                tickFrozen();
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    public boolean hasSpiderVenom(Player p) { return spiderVenom.contains(p.getUniqueId()); }
    public boolean consumeSpiderVenom(Player p) { return spiderVenom.remove(p.getUniqueId()); }
    public boolean consumeSkeletonHoming(Player p) { return skeletonHoming.remove(p.getUniqueId()); }
    public boolean hasWitherInfusion(Player p) { return witherInfusion.contains(p.getUniqueId()); }
    public boolean consumeWitherInfusion(Player p) { return witherInfusion.remove(p.getUniqueId()); }

    public void clearPending(Player p) {
        spiderVenom.remove(p.getUniqueId());
        skeletonHoming.remove(p.getUniqueId());
        witherInfusion.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ entry point

    public void activate(Player p) {
        Race race = plugin.races().getRace(p);
        if (race == null) return;
        if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) return;
        switch (race) {
            case BRUTE -> once(p, "brute", () -> bruteAxe(p));
            case PIGLIN -> piglinBarter(p);
            case SPIDER -> once(p, "spider", () -> spiderVenom(p));
            case CAT -> once(p, "cat", () -> catLeap(p));
            case ENDER -> enderBlink(p);
            case SHULKER -> once(p, "shulker", () -> plugin.shulker().open(p));
            case GUARDIAN -> guardian(p);
            case WARDEN -> once(p, "warden", () -> wardenBoom(p));
            case SNOWGOLEM -> once(p, "snowgolem", () -> snowGolem(p));
            case SKELETON -> once(p, "skeleton", () -> skeletonHoming(p));
            case ZOMBIE -> once(p, "zombie", () -> zombieRage(p));
            case BLAZEN -> once(p, "blazen", () -> blazenFire(p));
            case ELDER_GUARDIAN -> once(p, "elder_guardian", () -> beam(p, 2.0, true, Color.fromRGB(0xB9C7B0)));
            case WITHERBORN -> once(p, "witherborn", () -> witherInfuse(p));
            case ELARYAN -> once(p, "elaryan", () -> elaryanBoost(p));
            case DRAGON -> once(p, "dragon", () -> dragonBreath(p));
        }
    }

    /** Runs the ability if the cooldown is ready, then starts the cooldown. */
    private void once(Player p, String key, Runnable ability) {
        long left = plugin.cooldowns().remaining(p, key);
        if (left > 0) {
            Util.actionBar(p, "Ability on cooldown: " + Cooldowns.format(left), NamedTextColor.RED);
            return;
        }
        ability.run();
        plugin.cooldowns().start(p, key);
    }

    private boolean ready(Player p, String key) {
        return plugin.cooldowns().remaining(p, key) <= 0;
    }

    // ------------------------------------------------------------------ BRUTE

    private void bruteAxe(Player p) {
        World w = p.getWorld();
        Location origin = p.getLocation().add(0, 0.6, 0);
        Vector fwd = Util.flatForward(p.getLocation());
        float yaw = p.getLocation().getYaw() + 90f; // display local +X == player's forward
        float scale = 4.0f;

        ItemDisplay axe = w.spawn(origin, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.NETHERITE_AXE));
            d.setPersistent(false);
            d.setRotation(yaw, 0);
            d.setTransformation(axeTransform(45f, scale));
            d.getPersistentDataContainer().set(Keys.ABILITY_ENTITY, PersistentDataType.BYTE, (byte) 1);
        });
        w.playSound(origin, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 0.6f);

        // swing down over 6 ticks (client interpolates), then impact
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!axe.isValid()) return;
            axe.setInterpolationDelay(0);
            axe.setInterpolationDuration(6);
            axe.setTransformation(axeTransform(-80f, scale));
        }, 2L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location impact = origin.clone().add(fwd.clone().multiply(3.5));
            impact = groundBelow(impact);
            w.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
            w.playSound(impact, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.8f);
            w.spawnParticle(Particle.EXPLOSION, impact, 3, 0.8, 0.2, 0.8, 0);
            Block ground = impact.clone().subtract(0, 0.5, 0).getBlock();
            if (!ground.getType().isAir()) {
                w.spawnParticle(Particle.BLOCK, impact, 120, 2.0, 0.2, 2.0, 0.2, ground.getBlockData());
            }
            w.spawnParticle(Particle.CRIT, impact, 60, 2.5, 0.3, 2.5, 0.3);
            double radius = plugin.getConfig().getDouble("brute.radius", 5.0);
            double dmg = plugin.getConfig().getDouble("brute.damage", 6.0);
            for (LivingEntity e : Util.enemiesNear(p, impact, radius)) {
                Util.abilityDamage(e, dmg, p);
                e.setVelocity(e.getVelocity().add(e.getLocation().toVector().subtract(impact.toVector())
                        .setY(0).normalize().multiply(0.4).setY(0.35)));
            }
        }, 8L);

        Bukkit.getScheduler().runTaskLater(plugin, axe::remove, 24L);
    }

    /** Rotates the axe sprite around its handle end, in the display's local XY plane. */
    private Transformation axeTransform(float degrees, float scale) {
        Quaternionf rot = new Quaternionf(new AxisAngle4f((float) Math.toRadians(degrees), 0, 0, 1));
        // keep the bottom of the handle (sprite corner -0.5,-0.5) fixed at the entity origin
        Vector3f handle = new Vector3f(-0.5f * scale, -0.5f * scale, 0);
        Vector3f translation = rot.transform(new Vector3f(handle)).negate();
        return new Transformation(translation, rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private Location groundBelow(Location loc) {
        Location l = loc.clone();
        for (int i = 0; i < 6; i++) {
            Block below = l.clone().subtract(0, 0.1, 0).getBlock();
            if (below.getType().isSolid()) break;
            l.subtract(0, 1, 0);
        }
        l.setY(Math.floor(l.getY()) + 0.05);
        return l;
    }

    // ------------------------------------------------------------------ PIGLIN

    private void piglinBarter(Player p) {
        long left = plugin.cooldowns().remaining(p, "piglin");
        if (left > 0) {
            Util.actionBar(p, "Ability on cooldown: " + Cooldowns.format(left), NamedTextColor.RED);
            return;
        }
        if (!p.getInventory().containsAtLeast(new ItemStack(Material.GOLD_BLOCK), 1)) {
            Util.actionBar(p, "You need a block of gold to barter!", NamedTextColor.RED);
            p.playSound(p.getLocation(), Sound.ENTITY_PIGLIN_ANGRY, 1f, 1f);
            return;
        }
        p.getInventory().removeItem(new ItemStack(Material.GOLD_BLOCK, 1));
        int rolls = plugin.getConfig().getInt("piglin.rolls-per-gold-block", 4);
        for (int i = 0; i < rolls; i++) {
            ItemStack loot = plugin.piglinLoot().roll(random);
            if (loot != null) Util.giveOrDrop(p, loot);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PIGLIN_ADMIRING_ITEM, 1f, 1f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PIGLIN_CELEBRATE, 1f, 1.1f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0);
        plugin.cooldowns().start(p, "piglin");
    }

    // ------------------------------------------------------------------ SPIDER

    private void spiderVenom(Player p) {
        spiderVenom.add(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.8f);
        Util.actionBar(p, "Your next hit applies Poison III!", NamedTextColor.DARK_GREEN);
    }

    // ------------------------------------------------------------------ CAT

    private void catLeap(Player p) {
        float pitch = p.getLocation().getPitch();
        double distance;
        if (Math.abs(pitch) >= 85) distance = 2;        // staring straight up or down (within 5 degrees)
        else if (Math.abs(pitch) <= 5) distance = 4;    // looking straight ahead (within 5 degrees)
        else distance = 3;                              // anywhere in between
        Vector dir = p.getLocation().getDirection().normalize();
        int ticks = 4;
        Vector step = dir.clone().multiply(distance / ticks);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_CAT_HISS, 1f, 1.3f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t < ticks) {
                    p.setVelocity(step);
                    p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 3, 0.1, 0.1, 0.1, 0.01);
                } else {
                    Vector v = p.getVelocity();
                    p.setVelocity(new Vector(0, Math.min(v.getY(), 0), 0));
                    p.setFallDistance(0);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, false, true, true));
                    cancel();
                }
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ------------------------------------------------------------------ ENDER

    private void enderBlink(Player p) {
        long left = plugin.cooldowns().remaining(p, "ender");
        if (left > 0) {
            Util.actionBar(p, "Ability on cooldown: " + Cooldowns.format(left), NamedTextColor.RED);
            return;
        }
        double range = plugin.getConfig().getDouble("ender.teleport-distance", 10);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double max = hit == null ? range : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.4);
        double eyeHeight = p.getEyeHeight();
        Location from = p.getLocation();
        for (double d = max; d >= 0.5; d -= 0.25) {
            Location feet = eye.clone().add(dir.clone().multiply(d)).subtract(0, eyeHeight, 0);
            BoundingBox box = p.getBoundingBox().shift(feet.toVector().subtract(from.toVector()));
            if (!p.getWorld().hasCollisionsIn(box)) {
                feet.setYaw(from.getYaw());
                feet.setPitch(from.getPitch());
                p.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.5);
                p.teleport(feet);
                p.setFallDistance(0);
                p.getWorld().playSound(feet, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, feet.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.05);
                plugin.cooldowns().start(p, "ender");
                return;
            }
        }
        Util.actionBar(p, "No room to teleport there.", NamedTextColor.RED);
    }

    // ------------------------------------------------------------------ GUARDIAN

    private void guardian(Player p) {
        boolean fatigueReady = ready(p, "guardian_fatigue");
        boolean beamReady = ready(p, "guardian_beam");
        if (!fatigueReady && !beamReady) {
            Util.actionBar(p, "Gaze: " + Cooldowns.format(plugin.cooldowns().remaining(p, "guardian_fatigue"))
                    + "  |  Laser: " + Cooldowns.format(plugin.cooldowns().remaining(p, "guardian_beam")), NamedTextColor.RED);
            return;
        }
        if (fatigueReady) {
            double radius = plugin.getConfig().getDouble("guardian.fatigue-radius", 20);
            for (LivingEntity e : Util.enemiesNear(p, p.getLocation(), radius)) {
                e.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 3, false, true, true));
                if (e instanceof Player tp) {
                    tp.playSound(tp.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                }
            }
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.4f);
            plugin.cooldowns().start(p, "guardian_fatigue");
        }
        if (beamReady) {
            beam(p, 1.0, false, Color.fromRGB(0x4FB3A9));
            plugin.cooldowns().start(p, "guardian_beam");
        }
        if (!fatigueReady) {
            Util.actionBar(p, "Laser fired. Gaze ready in "
                    + Cooldowns.format(plugin.cooldowns().remaining(p, "guardian_fatigue")), NamedTextColor.AQUA);
        } else if (!beamReady) {
            Util.actionBar(p, "Gaze cast. Laser ready in "
                    + Cooldowns.format(plugin.cooldowns().remaining(p, "guardian_beam")), NamedTextColor.AQUA);
        }
    }

    /**
     * A 4 second beam from the player's chest towards where they look. Whatever it touches takes
     * {@code damagePerSecond} for every full second of contact.
     */
    private void beam(Player p, double damagePerSecond, boolean elder, Color color) {
        double range = plugin.getConfig().getDouble("guardian.beam-range", 20);
        int duration = 80; // 4 seconds
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.1f);
        Map<UUID, Integer> contact = new HashMap<>();
        Set<UUID> fatigued = new HashSet<>();
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1.2f, elder ? 0.7f : 1.1f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t++ >= duration) {
                    cancel();
                    return;
                }
                Location start = p.getEyeLocation().subtract(0, 0.45 * p.getHeight() / 1.8, 0);
                Vector dir = p.getEyeLocation().getDirection().normalize();
                start.add(dir.clone().multiply(0.4));
                RayTraceResult r = p.getWorld().rayTrace(start, dir, range, FluidCollisionMode.NEVER, true, 0.35,
                        e -> Util.isEnemy(p, e));
                double len = r == null ? range : r.getHitPosition().distance(start.toVector());
                for (double d = 0; d < len; d += 0.35) {
                    p.getWorld().spawnParticle(Particle.DUST, start.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
                }
                if (r != null && r.getHitEntity() instanceof LivingEntity le) {
                    if (t % 4 == 0) le.getWorld().spawnParticle(Particle.GLOW, le.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0);
                    if (elder && fatigued.add(le.getUniqueId())) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 6 * 60 * 20, 2, false, true, true));
                    }
                    int c = contact.merge(le.getUniqueId(), 1, Integer::sum);
                    if (c >= 20) {
                        contact.put(le.getUniqueId(), 0);
                        Util.abilityDamage(le, damagePerSecond, p);
                    }
                }
                if (t % 20 == 0) p.getWorld().playSound(start, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.8f);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ------------------------------------------------------------------ WARDEN

    private void wardenBoom(Player p) {
        double darkRadius = plugin.getConfig().getDouble("warden.darkness-radius", 5);
        int darkTicks = plugin.getConfig().getInt("warden.darkness-seconds", 10) * 20;
        for (LivingEntity e : Util.enemiesNear(p, p.getLocation(), darkRadius)) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darkTicks, 0, false, true, true));
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 2f, 1f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t < 40) {
                    if (t % 4 == 0) {
                        p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1.2, 0), 4, 0.4, 0.5, 0.4, 0.02);
                    }
                    t++;
                    return;
                }
                cancel();
                fireSonicBoom(p);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void fireSonicBoom(Player p) {
        double range = plugin.getConfig().getDouble("warden.range", 15);
        double dmg = Math.min(8.0, plugin.getConfig().getDouble("warden.damage", 8.0)); // never more than 4 hearts
        Location start = p.getEyeLocation().subtract(0, 0.3, 0);
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Vector a = start.toVector();
        Vector b = a.clone().add(dir.clone().multiply(range));
        p.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 1f);
        for (double d = 1; d <= range; d += 1) {
            p.getWorld().spawnParticle(Particle.SONIC_BOOM, start.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
        Location mid = start.clone().add(dir.clone().multiply(range / 2));
        for (Entity e : p.getWorld().getNearbyEntities(mid, range / 2 + 2, range / 2 + 2, range / 2 + 2)) {
            if (!Util.isEnemy(p, e)) continue;
            LivingEntity le = (LivingEntity) e;
            Vector center = le.getLocation().toVector().add(new Vector(0, le.getHeight() / 2, 0));
            if (Util.distToSegment(center, a, b) <= 1.2 + le.getWidth() / 2) {
                Util.abilityDamage(le, dmg, p, DamageType.SONIC_BOOM, null);
                le.setVelocity(le.getVelocity().add(dir.clone().multiply(1.2).setY(0.4)));
            }
        }
    }

    // ------------------------------------------------------------------ SNOW GOLEM

    private void snowGolem(Player p) {
        int freezeTicks = plugin.getConfig().getInt("snowgolem.freeze-seconds", 2) * 20;
        double radius = plugin.getConfig().getDouble("snowgolem.radius", 2);
        // freeze the enemy you're looking at
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 16, 0.4,
                e -> Util.isEnemy(p, e));
        if (r != null && r.getHitEntity() instanceof LivingEntity le) freeze(le, freezeTicks);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1.5f, 0.7f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 1.5f, 0.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t > 40) {
                    cancel();
                    return;
                }
                Location c = p.getLocation();
                if (t % 4 == 0) {
                    for (int i = 0; i < 10; i++) {
                        double ang = (Math.PI * 2 / 10) * i + t * 0.15;
                        Vector out = new Vector(Math.cos(ang), 0, Math.sin(ang));
                        Location spawn = c.clone().add(out.clone().multiply(radius)).add(0, 1.0, 0);
                        Snowball ball = p.getWorld().spawn(spawn, Snowball.class, s -> {
                            s.setShooter(p);
                            s.getPersistentDataContainer().set(Keys.GOLEM_SNOWBALL, PersistentDataType.BYTE, (byte) 1);
                        });
                        ball.setVelocity(out.multiply(0.8).setY(0.15));
                    }
                }
                if (t % 2 == 0) {
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 1, 0), 12, radius * 0.6, 0.5, radius * 0.6, 0.02);
                }
                for (LivingEntity e : Util.enemiesNear(p, c, radius)) freeze(e, freezeTicks);
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /** Roots an entity in place with a frost overlay. */
    public void freeze(LivingEntity e, int ticks) {
        boolean wasFrozen = frozen.containsKey(e.getUniqueId());
        frozen.put(e.getUniqueId(), tick + ticks);
        if (!wasFrozen) {
            addTransient(e, Attribute.MOVEMENT_SPEED, Keys.FREEZE_SPEED, -1.0);
            addTransient(e, Attribute.JUMP_STRENGTH, Keys.FREEZE_JUMP, -1.0);
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
        }
        Vector v = e.getVelocity();
        e.setVelocity(new Vector(0, Math.min(0, v.getY()), 0));
        if (e instanceof Player tp && tp.isGliding()) tp.setGliding(false);
    }

    public boolean isFrozen(Entity e) {
        return frozen.containsKey(e.getUniqueId());
    }

    private void addTransient(LivingEntity e, Attribute attr, NamespacedKey key, double amount) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(key);
        inst.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void tickFrozen() {
        if (frozen.isEmpty()) return;
        Iterator<Map.Entry<UUID, Long>> it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || !e.isValid()) {
                it.remove();
                continue;
            }
            if (tick >= en.getValue()) {
                unfreeze(le);
                it.remove();
                continue;
            }
            le.setFreezeTicks(Math.max(le.getFreezeTicks(), 130)); // frost overlay, below freeze-damage threshold
            if (tick % 5 == 0) {
                le.getWorld().spawnParticle(Particle.SNOWFLAKE, le.getLocation().add(0, le.getHeight() / 2, 0), 3, 0.3, 0.4, 0.3, 0);
            }
        }
    }

    private void unfreeze(LivingEntity le) {
        AttributeInstance s = le.getAttribute(Attribute.MOVEMENT_SPEED);
        if (s != null) s.removeModifier(Keys.FREEZE_SPEED);
        AttributeInstance j = le.getAttribute(Attribute.JUMP_STRENGTH);
        if (j != null) j.removeModifier(Keys.FREEZE_JUMP);
    }

    public void unfreezeAll() {
        for (UUID id : frozen.keySet()) {
            if (Bukkit.getEntity(id) instanceof LivingEntity le) unfreeze(le);
        }
        frozen.clear();
    }

    // ------------------------------------------------------------------ SKELETON

    private void skeletonHoming(Player p) {
        skeletonHoming.add(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_SKELETON_AMBIENT, 1f, 0.7f);
        Util.actionBar(p, "Your next arrow will seek its target!", NamedTextColor.WHITE);
    }

    /** Called from the bow listener when an empowered arrow is fired. */
    public void startHoming(Player shooter, AbstractArrow arrow) {
        arrow.getPersistentDataContainer().set(Keys.HOMING_ARROW, PersistentDataType.STRING, shooter.getUniqueId().toString());
        arrow.setGlowing(true);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isInBlock() || arrow.isOnGround() || t++ > 120) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.END_ROD, arrow.getLocation(), 1, 0, 0, 0, 0);
                Vector vel = arrow.getVelocity();
                double speed = vel.length();
                if (speed < 0.1) return;
                LivingEntity best = null;
                double bestD = Double.MAX_VALUE;
                for (Entity e : arrow.getNearbyEntities(24, 24, 24)) {
                    if (!Util.isEnemy(shooter, e)) continue;
                    LivingEntity le = (LivingEntity) e;
                    Vector to = le.getLocation().add(0, le.getHeight() * 0.6, 0).toVector().subtract(arrow.getLocation().toVector());
                    if (to.clone().normalize().dot(vel.clone().normalize()) < 0.2) continue; // must be roughly ahead
                    double d = to.lengthSquared();
                    if (d < bestD) {
                        bestD = d;
                        best = le;
                    }
                }
                if (best == null) return;
                Vector desired = best.getLocation().add(0, best.getHeight() * 0.6, 0).toVector()
                        .subtract(arrow.getLocation().toVector()).normalize();
                Vector newDir = vel.clone().normalize().multiply(0.6).add(desired.multiply(0.4)).normalize();
                arrow.setVelocity(newDir.multiply(Math.max(speed, 1.6)));
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    // ------------------------------------------------------------------ ZOMBIE

    private void zombieRage(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 7, false, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 0, false, true, true));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.2f, 0.6f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0);
    }

    // ------------------------------------------------------------------ BLAZEN

    private void blazenFire(Player p) {
        double length = plugin.getConfig().getDouble("blazen.length", 10);
        double dmg = plugin.getConfig().getDouble("blazen.damage", 6.0);
        Location base = p.getLocation();
        Vector fwd = Util.flatForward(base);
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
        Set<UUID> hit = new HashSet<>();
        p.getWorld().playSound(base, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
        new BukkitRunnable() {
            int step = 1;

            @Override
            public void run() {
                if (step > length) {
                    cancel();
                    return;
                }
                Location row = base.clone().add(fwd.clone().multiply(step));
                row = groundBelow(row.add(0, 1, 0));
                for (int s = -2; s <= 2; s++) {
                    Location cell = row.clone().add(side.clone().multiply(s * 0.75));
                    p.getWorld().spawnParticle(Particle.FLAME, cell.clone().add(0, 0.4, 0), 8, 0.25, 0.4, 0.25, 0.02);
                    if (s % 2 == 0) p.getWorld().spawnParticle(Particle.LAVA, cell, 1, 0.1, 0.1, 0.1, 0);
                }
                if (step % 3 == 0) p.getWorld().playSound(row, Sound.BLOCK_FIRE_AMBIENT, 1.2f, 1f);
                for (LivingEntity e : Util.enemiesNear(p, row.clone().add(0, 0.8, 0), 2.0)) {
                    if (!hit.add(e.getUniqueId())) continue;
                    // INDIRECT_MAGIC ignores armor and is not fire damage, so Fire Resistance can't stop it
                    Util.abilityDamage(e, dmg, p);
                    e.setFireTicks(Math.max(e.getFireTicks(), 80));
                }
                step++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ------------------------------------------------------------------ WITHERBORN

    private void witherInfuse(Player p) {
        witherInfusion.add(p.getUniqueId());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 1.4f);
        Util.actionBar(p, "Your blade is infused with darkness!", NamedTextColor.DARK_GRAY);
    }

    // ------------------------------------------------------------------ ELARYAN

    private void elaryanBoost(Player p) {
        int power = plugin.getConfig().getInt("elaryan.flight-duration", 5);
        if (p.isGliding()) {
            launchRocket(p, power);
            return;
        }
        if (!p.getLocation().subtract(0, 0.2, 0).getBlock().getType().isSolid()) {
            p.setGliding(true);
            launchRocket(p, power);
            return;
        }
        // on the ground: hop up, open the wings, then boost
        p.setVelocity(new Vector(0, 1.1, 0));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.6f, 1.5f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || p.isDead()) return;
            p.setGliding(true);
            launchRocket(p, power);
        }, 6L);
    }

    private void launchRocket(Player p, int power) {
        Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.setPower(Math.max(0, Math.min(127, power)));
            f.setFireworkMeta(meta);
            f.setShooter(p);
            f.getPersistentDataContainer().set(Keys.ABILITY_ENTITY, PersistentDataType.BYTE, (byte) 1);
        });
        fw.setAttachedTo(p);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
    }

    // ------------------------------------------------------------------ DRAGON

    private void dragonBreath(Player p) {
        Vector dir = p.getEyeLocation().getDirection().normalize();
        DragonFireball ball = p.launchProjectile(DragonFireball.class, dir.clone().multiply(1.2));
        ball.setAcceleration(dir.clone().multiply(0.1));
        ball.getPersistentDataContainer().set(Keys.DRAGON_FIREBALL, PersistentDataType.BYTE, (byte) 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 1f);
        // safety: remove if it never hits anything
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ball.isValid()) ball.remove();
        }, 200L);
    }

    /** Called when a dragon fireball lands. */
    public void dragonImpact(Player p, Projectile ball, Location at) {
        double radius = plugin.getConfig().getDouble("dragon.radius", 3.5);
        double dmg = plugin.getConfig().getDouble("dragon.damage", 8.0);
        World w = at.getWorld();
        w.spawnParticle(Particle.DRAGON_BREATH, at, 150, radius * 0.5, 0.6, radius * 0.5, 0.05, 1.0f);
        w.spawnParticle(Particle.EXPLOSION, at, 2, 0.4, 0.4, 0.4, 0);
        w.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 1f);
        if (p == null) return;
        for (LivingEntity e : Util.enemiesNear(p, at, radius)) {
            Util.abilityDamage(e, dmg, p, DamageType.DRAGON_BREATH, ball);
        }
    }
}
