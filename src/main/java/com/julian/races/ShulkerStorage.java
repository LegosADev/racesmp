package com.julian.races;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** The Shulker race's personal 27-slot storage. */
public final class ShulkerStorage {

    public static final class Holder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        Holder(UUID owner) { this.owner = owner; }

        public UUID owner() { return owner; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private final RacesPlugin plugin;
    private final File folder;
    private final Map<UUID, Inventory> cache = new HashMap<>();

    public ShulkerStorage(RacesPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "shulker");
    }

    public Inventory get(UUID owner) {
        return cache.computeIfAbsent(owner, this::load);
    }

    private Inventory load(UUID owner) {
        Holder holder = new Holder(owner);
        Inventory inv = Bukkit.createInventory(holder, InventoryType.SHULKER_BOX,
                Component.text("Shulker Storage", Race.SHULKER.color()));
        holder.inventory = inv;
        File f = new File(folder, owner + ".yml");
        if (f.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
            for (int i = 0; i < inv.getSize(); i++) {
                String b64 = yaml.getString("slot." + i);
                if (b64 == null) continue;
                try {
                    inv.setItem(i, ItemStack.deserializeBytes(Base64.getDecoder().decode(b64)));
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not read shulker slot " + i + " for " + owner + ": " + e.getMessage());
                }
            }
        }
        return inv;
    }

    public void save(UUID owner) {
        Inventory inv = cache.get(owner);
        if (inv == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            yaml.set("slot." + i, Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        try {
            folder.mkdirs();
            yaml.save(new File(folder, owner + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save shulker storage for " + owner + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (UUID id : new ArrayList<>(cache.keySet())) save(id);
    }

    public void unload(UUID owner) {
        save(owner);
        Inventory inv = cache.get(owner);
        if (inv != null && inv.getViewers().isEmpty()) cache.remove(owner);
    }

    public void open(Player p) {
        p.openInventory(get(p.getUniqueId()));
        p.playSound(p.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 1f);
    }

    /**
     * On death: every stack has a 10% chance to stay in the storage, the rest is dropped with the
     * player's other death drops.
     */
    public List<ItemStack> rollDeath(Player p) {
        Inventory inv = get(p.getUniqueId());
        if (p.getOpenInventory().getTopInventory().equals(inv)) p.closeInventory();
        double keep = plugin.getConfig().getDouble("shulker.keep-chance", 0.10);
        Random random = new Random();
        List<ItemStack> dropped = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            if (random.nextDouble() < keep) continue;
            dropped.add(it);
            inv.setItem(i, null);
        }
        save(p.getUniqueId());
        return dropped;
    }
}
