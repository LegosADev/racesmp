package com.julian.races;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runs every tick and enforces all the "always on" race rules. */
public final class PassiveTask extends BukkitRunnable {

    private final RacesPlugin plugin;
    private long tick = 0;
    /** ticks spent in water (Ender / Blazen), -1 = was not in water last tick */
    private final Map<UUID, Integer> waterTicks = new HashMap<>();

    public PassiveTask(RacesPlugin plugin) {
        this.plugin = plugin;
    }

    public void forget(Player p) {
        waterTicks.remove(p.getUniqueId());
    }

    @Override
    public void run() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Race r = plugin.races().getRace(p);
            if (r == null || p.isDead()) continue;
            try {
                everyTick(p, r);
                if (tick % 5 == 0) everyQuarterSecond(p, r);
                if (tick % 20 == 0) plugin.races().apply(p, false);
            } catch (Exception ex) {
                plugin.getLogger().warning("Passive error for " + p.getName() + ": " + ex);
            }
        }
    }

    // ------------------------------------------------------------------ every tick

    private void everyTick(Player p, Race r) {
        switch (r) {
            case SPIDER -> spiderWebSpeed(p);
            case ENDER -> water(p, 2.0, true);
            case BLAZEN -> water(p, 1.0, false);
            default -> {
            }
        }
    }

    /** Ender: 1 heart on contact, then 1 heart/second. Blazen: half a heart per second. */
    private void water(Player p, double damage, boolean instant) {
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (!p.isInWater()) {
            waterTicks.remove(p.getUniqueId());
            return;
        }
        Integer t = waterTicks.get(p.getUniqueId());
        if (t == null) {
            waterTicks.put(p.getUniqueId(), 0);
            if (instant) hurtByWater(p, damage);
            return;
        }
        t++;
        if (t >= 20) {
            t = 0;
            hurtByWater(p, damage);
        }
        waterTicks.put(p.getUniqueId(), t);
    }

    private void hurtByWater(Player p, double damage) {
        p.setNoDamageTicks(0);
        p.damage(damage, DamageSource.builder(DamageType.DROWN).build());
    }

    /** +50% movement speed while inside cobwebs (cobwebs scale movement, so this is 50% faster). */
    private void spiderWebSpeed(Player p) {
        boolean inWeb = false;
        BoundingBox box = p.getBoundingBox();
        int minX = (int) Math.floor(box.getMinX()), maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY()), maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ()), maxZ = (int) Math.floor(box.getMaxZ());
        outer:
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = p.getWorld().getBlockAt(x, y, z);
                    if (b.getType() == Material.COBWEB) {
                        inWeb = true;
                        break outer;
                    }
                }
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(Keys.WEB_SPEED) != null;
        if (inWeb && !has) {
            speed.addTransientModifier(new AttributeModifier(Keys.WEB_SPEED, 0.5, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        } else if (!inWeb && has) {
            speed.removeModifier(Keys.WEB_SPEED);
        }
    }

    // ------------------------------------------------------------------ every 5 ticks

    private void everyQuarterSecond(Player p, Race r) {
        plugin.races().removeForeignSoulItems(p, r);
        plugin.races().giveSoulItems(p, r);
        armor(p, r);
        if (Util.restricted(p)) {
            cobwebs(p);
            if (r == Race.SPIDER && p.getInventory().getItemInOffHand().getType() == Material.SHIELD) {
                ItemStack shield = p.getInventory().getItemInOffHand();
                p.getInventory().setItemInOffHand(null);
                Util.giveOrDrop(p, shield);
                Util.actionBar(p, "Spiders can't hold shields in their offhand.", NamedTextColor.RED);
            }
        }
        updateZombieFist(p);
        switch (r) {
            case SKELETON -> {
                PlayerInventory inv = p.getInventory();
                if (inv.getItemInMainHand().getType() == Material.BOW || inv.getItemInOffHand().getType() == Material.BOW) {
                    PotionEffect cur = p.getPotionEffect(PotionEffectType.SPEED);
                    if (cur == null || cur.getAmplifier() < 1 || (cur.getAmplifier() == 1 && cur.getDuration() < 20)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, true, false, true));
                    }
                }
            }
            case SNOWGOLEM -> {
                if (p.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            }
            case WITHERBORN -> {
                if (plugin.abilities().hasWitherInfusion(p)) {
                    p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1.0, 0)
                            .add(p.getLocation().getDirection().multiply(0.5)), 4, 0.15, 0.2, 0.15, 0.01);
                }
            }
            case SPIDER -> {
                if (plugin.abilities().hasSpiderVenom(p) && tick % 10 == 0) {
                    p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation().add(0, 1.0, 0), 2, 0.2, 0.2, 0.2, 0);
                }
            }
            default -> {
            }
        }
    }

    /** Armor rules: Brute protection cap, Piglin gold buffs / no diamond armor. */
    private void armor(Player p, Race r) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack it = armor[i];
            if (it == null || it.getType().isAir() || Items.isSoul(it)) continue;
            Material m = it.getType();
            if (r == Race.PIGLIN && Items.isDiamondArmor(m) && Util.restricted(p)) {
                armor[i] = null;
                armorChanged = true;
                Util.giveOrDrop(p, it);
                Util.actionBar(p, "Piglins can't wear diamond armor!", NamedTextColor.RED);
                continue;
            }
            String want = null;
            Map<Enchantment, Integer> target = null;
            if (r == Race.PIGLIN && Items.isGoldArmor(m)) {
                want = "piglin";
                target = new HashMap<>();
                target.put(Enchantment.PROTECTION, Math.max(2, it.getEnchantmentLevel(Enchantment.PROTECTION)));
                target.put(Enchantment.UNBREAKING, 10);
            } else if (r == Race.BRUTE && it.getEnchantmentLevel(Enchantment.PROTECTION) > 3) {
                want = "brute";
                target = Items.map(Enchantment.PROTECTION, 3);
            }
            String current = Items.adjustedBy(it);
            if (want == null) {
                if (current != null && Items.restore(it)) armorChanged = true;
            } else if (!want.equals(current)) {
                if (Items.adjust(it, want, target)) armorChanged = true;
            }
        }
        if (armorChanged) inv.setArmorContents(armor);

        // anything adjusted that is no longer worn goes back to normal
        ItemStack[] storage = inv.getStorageContents();
        boolean storageChanged = false;
        for (ItemStack it : storage) {
            if (it != null && Items.adjustedBy(it) != null && Items.restore(it)) storageChanged = true;
        }
        if (storageChanged) inv.setStorageContents(storage);
        ItemStack off = inv.getItemInOffHand();
        if (Items.adjustedBy(off) != null && Items.restore(off)) inv.setItemInOffHand(off);
        ItemStack cursor = p.getItemOnCursor();
        if (Items.adjustedBy(cursor) != null && Items.restore(cursor)) p.setItemOnCursor(cursor);
    }

    // ------------------------------------------------------------------ cobwebs

    public int cobwebLimit(Player p) {
        return plugin.races().getRace(p) == Race.SPIDER
                ? plugin.getConfig().getInt("cobwebs.spider-limit", 128)
                : plugin.getConfig().getInt("cobwebs.limit", 64);
    }

    public int countCobwebs(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == Material.COBWEB) n += it.getAmount();
        }
        ItemStack cursor = p.getItemOnCursor();
        if (cursor.getType() == Material.COBWEB) n += cursor.getAmount();
        return n;
    }

    private void cobwebs(Player p) {
        int limit = cobwebLimit(p);
        int count = countCobwebs(p);
        if (count <= limit) return;
        int excess = count - limit;
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = contents.length - 1; i >= 0 && excess > 0; i--) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != Material.COBWEB) continue;
            int take = Math.min(excess, it.getAmount());
            excess -= take;
            ItemStack drop = it.clone();
            drop.setAmount(take);
            it.setAmount(it.getAmount() - take);
            inv.setItem(i, it.getAmount() > 0 ? it : null);
            p.getWorld().dropItem(p.getLocation(), drop, item -> item.setPickupDelay(60));
        }
        Util.actionBar(p, "You can only carry " + limit + " cobwebs.", NamedTextColor.RED);
    }

    // ------------------------------------------------------------------ zombie fist

    /** Zombie: empty-hand punches deal 1 heart (base fist damage is half a heart, so +1). */
    public void updateZombieFist(Player p) {
        AttributeInstance atk = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk == null) return;
        boolean want = plugin.races().getRace(p) == Race.ZOMBIE && p.getInventory().getItemInMainHand().getType().isAir();
        boolean has = atk.getModifier(Keys.ZOMBIE_FIST) != null;
        if (want && !has) {
            atk.addTransientModifier(new AttributeModifier(Keys.ZOMBIE_FIST, 1.0, AttributeModifier.Operation.ADD_NUMBER));
        } else if (!want && has) {
            atk.removeModifier(Keys.ZOMBIE_FIST);
        }
    }
}
