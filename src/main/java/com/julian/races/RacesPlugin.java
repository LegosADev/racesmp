package com.julian.races;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RacesPlugin extends JavaPlugin {

    private static RacesPlugin instance;

    private Cooldowns cooldowns;
    private RaceManager races;
    private Abilities abilities;
    private ShulkerStorage shulker;
    private PassiveTask passive;
    private Recipes recipes;
    private Menus menus;
    private PiglinLoot piglinLoot;
    private PackHost pack;

    public static RacesPlugin get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Keys.init(this);

        cooldowns = new Cooldowns(this);
        races = new RaceManager(this);
        races.load();
        abilities = new Abilities(this);
        shulker = new ShulkerStorage(this);
        recipes = new Recipes(this);
        recipes.register();
        menus = new Menus(this);
        piglinLoot = new PiglinLoot();
        piglinLoot.load(this);
        pack = new PackHost(this);
        pack.start();

        passive = new PassiveTask(this);
        passive.runTaskTimer(this, 1L, 1L);

        Bukkit.getPluginManager().registerEvents(new RaceListener(this), this);
        new Commands(this).register();

        // /reload or late enable: set up everyone already online
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (races.getRace(p) == null) races.setRace(p, races.randomJoinRace(), true);
            else races.apply(p, false);
            recipes.discover(p);
        }
        getLogger().info("Races enabled with " + Race.joinRaces().size() + " join races and "
                + Race.craftedRaces().size() + " crafted races.");
    }

    @Override
    public void onDisable() {
        if (abilities != null) abilities.unfreezeAll();
        if (shulker != null) shulker.saveAll();
        if (races != null) races.save();
        if (recipes != null) recipes.unregister();
        if (pack != null) pack.stop();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.MenuHolder
                    || p.getOpenInventory().getTopInventory().getHolder() instanceof ShulkerStorage.Holder) {
                p.closeInventory();
            }
        }
    }

    public void reloadEverything() {
        reloadConfig();
        piglinLoot.load(this);
        pack.start();
        races.applyAllOnline();
    }

    public Cooldowns cooldowns() { return cooldowns; }
    public RaceManager races() { return races; }
    public Abilities abilities() { return abilities; }
    public ShulkerStorage shulker() { return shulker; }
    public PassiveTask passive() { return passive; }
    public Recipes recipes() { return recipes; }
    public Menus menus() { return menus; }
    public PiglinLoot piglinLoot() { return piglinLoot; }
    public PackHost pack() { return pack; }
}
