package com.julian.races;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/** All event-driven race behaviour. */
public final class RaceListener implements Listener {

    private final RacesPlugin plugin;
    private final Random random = new Random();

    public RaceListener(RacesPlugin plugin) {
        this.plugin = plugin;
    }

    private RaceManager races() { return plugin.races(); }

    private Race race(Player p) { return plugin.races().getRace(p); }

    // ================================================================== join / quit / respawn / death

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Race r = race(p);
        if (r == null) {
            r = races().randomJoinRace();
            races().setRace(p, r, false);
            Race finalR = r;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) races().announce(p, finalR);
            }, 40L);
            if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            }
        } else {
            races().apply(p, false);
        }
        plugin.recipes().discover(p);
        plugin.pack().send(p);
        long nl = plugin.cooldowns().remaining(p, "nine_lives");
        if (nl > 0) p.setCooldown(Keys.NINE_LIVES_GROUP, (int) (nl / 50));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.shulker().unload(e.getPlayer().getUniqueId());
        plugin.passive().forget(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            races().apply(p, true);
            var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (max != null) p.setHealth(max.getValue());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        e.getDrops().removeIf(Items::isSoul);
        e.getItemsToKeep().removeIf(Items::isSoul);
        plugin.abilities().clearPending(p);
        if (race(p) == Race.SHULKER && !e.getKeepInventory()) {
            e.getDrops().addAll(plugin.shulker().rollDeath(p));
        }
    }

    // ================================================================== ability trigger

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (p.isSneaking()) {
            e.setCancelled(true);
            plugin.abilities().activate(p);
            return;
        }
        if (race(p) == Race.SPIDER && Util.restricted(p) && isShield(e.getOffHandItem())) {
            e.setCancelled(true);
            Util.actionBar(p, "Spiders can't hold shields in their offhand.", NamedTextColor.RED);
        }
    }

    // ================================================================== melee & combat

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWeaponRestriction(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !Util.restricted(p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return; // abilities are fine
        Material held = p.getInventory().getItemInMainHand().getType();
        Race r = race(p);
        if (r == Race.BRUTE && Items.isDiamondWeapon(held)) {
            e.setCancelled(true);
            Util.actionBar(p, "Brutes can't use diamond weapons!", NamedTextColor.RED);
        } else if (r == Race.PIGLIN && Items.isDiamondTool(held)) {
            e.setCancelled(true);
            Util.actionBar(p, "Piglins can't use diamond tools!", NamedTextColor.RED);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMeleeEffects(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        // ---- attacker is a player hitting in melee
        if (e.getDamager() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            Race r = race(p);
            if (r == Race.SPIDER && plugin.abilities().consumeSpiderVenom(p)) {
                int ticks = plugin.getConfig().getInt("spider.poison-seconds", 5) * 20;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, 2, false, true, true));
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SPIDER_HURT, 1f, 0.6f);
                victim.getWorld().spawnParticle(Particle.ITEM_SLIME, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0);
            } else if (r == Race.SHULKER) {
                if (random.nextDouble() < plugin.getConfig().getDouble("shulker.levitation-chance", 0.015)) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0, false, true, true));
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SHULKER_BULLET_HIT, 1f, 1f);
                }
            } else if (r == Race.WITHERBORN) {
                if (plugin.abilities().consumeWitherInfusion(p)) {
                    e.setDamage(e.getDamage() + plugin.getConfig().getDouble("witherborn.bonus-damage", 4.0));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1, false, true, true));
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.8f);
                    victim.getWorld().spawnParticle(Particle.LARGE_SMOKE, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
                } else {
                    PotionEffect cur = victim.getPotionEffect(PotionEffectType.WITHER);
                    // Wither I for 15s; refreshes instead of stacking and never downgrades a stronger wither
                    if (cur == null || cur.getAmplifier() == 0) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 300, 0, false, true, true));
                    }
                }
            }
        }

        // ---- empowered skeleton arrow
        if (e.getDamager() instanceof AbstractArrow arrow
                && arrow.getPersistentDataContainer().has(Keys.HOMING_ARROW, PersistentDataType.STRING)) {
            e.setDamage(e.getDamage() + plugin.getConfig().getDouble("skeleton.bonus-damage", 4.0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, true));
        }

        // ---- guardian: built-in Thorns II (30% chance, 1-4 damage, no durability cost)
        if (victim instanceof Player g && race(g) == Race.GUARDIAN
                && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                && e.getDamager() instanceof LivingEntity attacker && !attacker.equals(g)) {
            if (random.nextDouble() < 0.30) {
                double dmg = 1 + random.nextInt(4);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (attacker.isValid() && !attacker.isDead()) {
                        Util.abilityDamage(attacker, dmg, g, org.bukkit.damage.DamageType.THORNS, g);
                        g.getWorld().playSound(g.getLocation(), Sound.ENCHANT_THORNS_HIT, 1f, 1f);
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVictimDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Race r = race(p);
        if (r == null) return;
        boolean projectile = e.getDamageSource().getDirectEntity() instanceof Projectile;
        switch (r) {
            case CAT -> {
                if (e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setCancelled(true);
            }
            case SKELETON -> {
                if (projectile) e.setDamage(e.getDamage() * 0.5);
            }
            default -> {
            }
        }
    }

    /** Enders can never be damaged by a projectile, no matter what. Runs last so nothing can override it. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnderProjectileDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && race(p) == Race.ENDER
                && e.getDamageSource().getDirectEntity() instanceof Projectile) {
            e.setCancelled(true);
        }
    }

    // ================================================================== projectiles

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        Entity hit = e.getHitEntity();

        // Enders: projectiles fly straight through
        if (hit instanceof Player hp && race(hp) == Race.ENDER) {
            e.setCancelled(true);
            return;
        }

        // Dragon fireball: custom explosion, no lingering cloud
        if (proj.getPersistentDataContainer().has(Keys.DRAGON_FIREBALL, PersistentDataType.BYTE)) {
            if (hit != null && hit.equals(proj.getShooter())) {
                e.setCancelled(true);
                return;
            }
            e.setCancelled(true);
            Player shooter = proj.getShooter() instanceof Player sp ? sp : null;
            plugin.abilities().dragonImpact(shooter, proj, proj.getLocation());
            proj.remove();
            return;
        }

        // Snow golem snowballs: freeze enemies, pass through friends
        if (proj.getPersistentDataContainer().has(Keys.GOLEM_SNOWBALL, PersistentDataType.BYTE)
                && proj.getShooter() instanceof Player owner && hit != null) {
            if (Util.isEnemy(owner, hit)) {
                plugin.abilities().freeze((LivingEntity) hit, plugin.getConfig().getInt("snowgolem.freeze-seconds", 2) * 20);
            } else {
                e.setCancelled(true);
            }
            return;
        }

        // Skeleton homing arrow ignores shields
        if (proj.getPersistentDataContainer().has(Keys.HOMING_ARROW, PersistentDataType.STRING)
                && hit instanceof LivingEntity le) {
            if (proj.getShooter() instanceof Player owner && !Util.isEnemy(owner, le)) {
                e.setCancelled(true);
                return;
            }
            if (le instanceof HumanEntity he && he.isBlocking()) le.clearActiveItem();
            else if (le.hasActiveItem()) le.clearActiveItem();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSplash(PotionSplashEvent e) {
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player p && race(p) == Race.ENDER) e.setIntensity(le, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        e.getAffectedEntities().removeIf(le -> le instanceof Player p && race(p) == Race.ENDER);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKnockback(io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent e) {
        if (e.getEntity() instanceof Player p && race(p) == Race.ENDER && e.getPushedBy() instanceof Projectile) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        // the soulbound Bone Arrow only fires from the Bone Bow (stops infinite free arrows)
        if (Items.isSoul(e.getConsumable(), Items.SKELETON_ARROW) && !Items.isSoul(e.getBow(), Items.SKELETON_BOW)
                && e.shouldConsumeItem()) {
            e.setCancelled(true);
            Util.actionBar(p, "Bone Arrows only work with the Bone Bow.", NamedTextColor.RED);
            return;
        }
        if (race(p) == Race.SKELETON && e.getProjectile() instanceof AbstractArrow arrow
                && plugin.abilities().consumeSkeletonHoming(p)) {
            plugin.abilities().startHoming(p, arrow);
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.5f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoadCrossbow(io.papermc.paper.event.entity.EntityLoadCrossbowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !e.shouldConsumeItem()) return;
        if (Items.isSoul(firstArrow(p), Items.SKELETON_ARROW)) {
            e.setCancelled(true);
            Util.actionBar(p, "Bone Arrows only work with the Bone Bow.", NamedTextColor.RED);
        }
    }

    /** The arrow a bow/crossbow would pick: offhand, main hand, then inventory order. */
    private static ItemStack firstArrow(Player p) {
        PlayerInventory inv = p.getInventory();
        if (isArrow(inv.getItemInOffHand())) return inv.getItemInOffHand();
        if (isArrow(inv.getItemInMainHand())) return inv.getItemInMainHand();
        for (ItemStack it : inv.getStorageContents()) if (isArrow(it)) return it;
        return null;
    }

    private static boolean isArrow(ItemStack it) {
        if (it == null) return false;
        Material m = it.getType();
        return m == Material.ARROW || m == Material.TIPPED_ARROW || m == Material.SPECTRAL_ARROW;
    }

    // ================================================================== 9 Lives

    @EventHandler(priority = EventPriority.HIGH)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;
        PlayerInventory inv = p.getInventory();
        ItemStack used = inv.getItem(hand);
        if (!Items.isSoul(used, Items.NINE_LIVES)) return;

        boolean usable = race(p) == Race.CAT && plugin.cooldowns().remaining(p, "nine_lives") <= 0;
        if (!usable) {
            // 9 Lives is recharging; fall back to a normal totem in the other hand if there is one
            EquipmentSlot other = hand == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
            ItemStack o = inv.getItem(other);
            if (o.getType() == Material.TOTEM_OF_UNDYING && !Items.isSoul(o)) {
                inv.setItem(hand, used.clone()); // vanilla shrinks its old reference, not this copy
                o.setAmount(o.getAmount() - 1);
                inv.setItem(other, o.getAmount() > 0 ? o : null);
                e.setCancelled(false);
            } else {
                e.setCancelled(true);
            }
            return;
        }
        // never consumed: hand vanilla a detached copy to shrink
        inv.setItem(hand, used.clone());
        e.setCancelled(false);
        plugin.cooldowns().start(p, "nine_lives");
        p.setCooldown(Keys.NINE_LIVES_GROUP, plugin.cooldowns().lengthSeconds("nine_lives") * 20);
        Util.msg(p, "9 Lives saved you! It recharges in "
                + Cooldowns.format(plugin.cooldowns().lengthSeconds("nine_lives") * 1000L) + ".", NamedTextColor.GOLD);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_CAT_PURREOW, 1.2f, 1f);
    }

    // ================================================================== interactions

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null) return;
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;

        // crafted-race essence
        Race essence = Items.essenceRace(item);
        if (essence != null) {
            e.setCancelled(true);
            if (!right) return;
            useEssence(p, essence, e.getHand());
            return;
        }

        if (!Util.restricted(p)) return;
        Race r = race(p);
        if (r == Race.GUARDIAN && right && item.getType() == Material.WIND_CHARGE) {
            e.setCancelled(true);
            Util.actionBar(p, "Guardians can't use wind charges!", NamedTextColor.RED);
            return;
        }
        if (r == Race.PIGLIN && Items.isDiamondTool(item.getType()) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            Util.actionBar(p, "Piglins can't use diamond tools!", NamedTextColor.RED);
        }
    }

    private void useEssence(Player p, Race race, EquipmentSlot hand) {
        if (!plugin.recipes().isEnabled(race)) {
            Util.msg(p, "The " + race.displayName() + " race is currently disabled.", NamedTextColor.RED);
            return;
        }
        if (race(p) == race) {
            Util.msg(p, "You are already a " + race.displayName() + ".", NamedTextColor.YELLOW);
            return;
        }
        ItemStack inHand = p.getInventory().getItem(hand == null ? EquipmentSlot.HAND : hand);
        if (Items.essenceRace(inHand) != race) return;
        inHand.setAmount(inHand.getAmount() - 1);
        p.getInventory().setItem(hand == null ? EquipmentSlot.HAND : hand, inHand.getAmount() > 0 ? inHand : null);
        races().setRace(p, race, true);
        p.getWorld().playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
        Bukkit.broadcast(Util.PREFIX.append(net.kyori.adventure.text.Component.text(
                p.getName() + " has become a ", NamedTextColor.GRAY)).append(
                net.kyori.adventure.text.Component.text(race.displayName() + "!", race.color())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (Items.essenceRace(e.getItemInHand()) != null || Items.isSoul(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (race(p) == Race.PIGLIN && Util.restricted(p)
                && Items.isDiamondTool(p.getInventory().getItemInMainHand().getType())) {
            e.setCancelled(true);
            Util.actionBar(p, "Piglins can't use diamond tools!", NamedTextColor.RED);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();
        if (race(p) == Race.PIGLIN && Util.restricted(p) && Items.isDiamondTool(e.getItemInHand().getType())) {
            e.setCancelled(true);
            Util.actionBar(p, "Piglins can't use diamond tools!", NamedTextColor.RED);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.passive().updateZombieFist(e.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || race(p) != Race.SNOWGOLEM) return;
        PotionEffect ne = e.getNewEffect();
        if (ne != null && ne.getType().equals(PotionEffectType.FIRE_RESISTANCE)) e.setCancelled(true);
    }

    // ================================================================== cobweb limit

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !Util.restricted(p)) return;
        ItemStack stack = e.getItem().getItemStack();
        if (stack.getType() != Material.COBWEB) return;
        int limit = plugin.passive().cobwebLimit(p);
        int have = plugin.passive().countCobwebs(p);
        int room = limit - have;
        if (room >= stack.getAmount()) return;
        e.setCancelled(true);
        if (room <= 0) return;
        ItemStack part = stack.clone();
        part.setAmount(room);
        p.getInventory().addItem(part);
        stack.setAmount(stack.getAmount() - room);
        e.getItem().setItemStack(stack);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.5f);
    }

    // ================================================================== soul items

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (Items.isSoul(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItem(e.getHand());
        if ((Items.isSoul(it) || Items.essenceRace(it) != null)
                && (e.getRightClicked() instanceof ItemFrame || e.getRightClicked() instanceof Allay)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (Items.isSoul(e.getPlayerItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();

        // plugin menus are read-only
        if (top.getHolder() instanceof Menus.MenuHolder holder) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().equals(top)) {
                int slot = e.getSlot();
                ClickType click = e.getClick();
                Bukkit.getScheduler().runTask(plugin, () -> plugin.menus().click(p, holder, slot, click));
            }
            return;
        }

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        ItemStack hotbar = e.getClick() == ClickType.NUMBER_KEY ? p.getInventory().getItem(e.getHotbarButton())
                : e.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : null;
        boolean soulInvolved = Items.isSoul(current) || Items.isSoul(cursor) || Items.isSoul(hotbar);

        // soul items can't go into bundles, crafting grids or any container
        if (soulInvolved) {
            if (isBundle(current) || isBundle(cursor)) {
                e.setCancelled(true);
                return;
            }
            boolean ownInventoryOnly = top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE;
            if (!ownInventoryOnly) {
                e.setCancelled(true);
                return;
            }
            if (e.getSlotType() == InventoryType.SlotType.CRAFTING || e.getSlotType() == InventoryType.SlotType.RESULT) {
                e.setCancelled(true);
                return;
            }
        }

        // shulker storage can't hold shulker boxes
        if (top.getHolder() instanceof ShulkerStorage.Holder) {
            if (isShulker(current) || isShulker(cursor) || isShulker(hotbar)) {
                e.setCancelled(true);
                return;
            }
        }

        // spiders can't move a shield into the offhand slot
        if (race(p) == Race.SPIDER && Util.restricted(p) && e.getClickedInventory() instanceof PlayerInventory
                && e.getSlot() == 40) {
            if (isShield(cursor) || (e.getClick() == ClickType.NUMBER_KEY && isShield(hotbar))) {
                e.setCancelled(true);
                Util.actionBar(p, "Spiders can't hold shields in their offhand.", NamedTextColor.RED);
            }
        }
        if (race(p) == Race.SPIDER && Util.restricted(p) && e.getClick() == ClickType.SWAP_OFFHAND && isShield(current)) {
            e.setCancelled(true);
            Util.actionBar(p, "Spiders can't hold shields in their offhand.", NamedTextColor.RED);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof Menus.MenuHolder) {
            e.setCancelled(true);
            return;
        }
        ItemStack item = e.getOldCursor();
        boolean intoTop = e.getRawSlots().stream().anyMatch(s -> s < top.getSize());
        if (Items.isSoul(item) && (intoTop || top.getType() != InventoryType.CRAFTING)) {
            e.setCancelled(true);
            return;
        }
        if (top.getHolder() instanceof ShulkerStorage.Holder && intoTop && isShulker(item)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof ShulkerStorage.Holder h) {
            plugin.shulker().save(h.owner());
            if (e.getPlayer() instanceof Player p) p.playSound(p.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 1f, 1f);
        }
    }

    // ================================================================== crafting

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (Items.isSoul(it) || Items.essenceRace(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
        Race r = Items.essenceRace(e.getInventory().getResult());
        if (r != null && !plugin.recipes().isEnabled(r)) e.getInventory().setResult(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        Race r = Items.essenceRace(e.getRecipe().getResult());
        if (r != null && !plugin.recipes().isEnabled(r)) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) {
                Util.msg(p, "The " + r.displayName() + " race is currently disabled.", NamedTextColor.RED);
            }
        }
    }

    // ================================================================== helpers

    private static boolean isShield(ItemStack it) {
        return it != null && it.getType() == Material.SHIELD;
    }

    private static boolean isBundle(ItemStack it) {
        return it != null && it.getType().name().endsWith("BUNDLE");
    }

    private static boolean isShulker(ItemStack it) {
        return it != null && Items.isShulkerBox(it.getType());
    }
}
