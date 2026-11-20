package com.julian.races;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/** /race, /trusted, /recipes, /recipe, /ability */
public final class Commands {

    private final RacesPlugin plugin;

    public Commands(RacesPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.registerCommand("race", "Show or manage races", List.of("races"), new RaceCommand());
        plugin.registerCommand("trusted", "Players your abilities won't hit", List.of("trust"), new TrustedCommand());
        plugin.registerCommand("recipes", "View the crafted race recipes", new RecipesCommand());
        plugin.registerCommand("recipe", "Operator menu: toggle crafted races", new RecipeAdminCommand());
        plugin.registerCommand("ability", "Use your race ability", new AbilityCommand());
    }

    private static List<String> online(String prefix) {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(o);
        return out;
    }

    private static void send(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Util.PREFIX.append(Component.text(text, color)));
    }

    // ------------------------------------------------------------------ /race

    private final class RaceCommand implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            CommandSender s = src.getSender();
            boolean admin = s.hasPermission("races.admin");
            if (args.length == 0) {
                if (!(s instanceof Player p)) {
                    send(s, "Usage: /race <list|info|set|reroll|reload>", NamedTextColor.YELLOW);
                    return;
                }
                Race r = plugin.races().getRace(p);
                if (r == null) send(s, "You don't have a race yet.", NamedTextColor.YELLOW);
                else plugin.races().sendInfo(p, r);
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "list" -> {
                    s.sendMessage(Component.text("Join races (random, equal chance):", NamedTextColor.GOLD));
                    for (Race r : Race.joinRaces()) {
                        s.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY).append(Component.text(r.displayName(), r.color())));
                    }
                    s.sendMessage(Component.text("Crafted races (see /recipes):", NamedTextColor.GOLD));
                    for (Race r : Race.craftedRaces()) {
                        s.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY).append(Component.text(r.displayName(), r.color()))
                                .append(Component.text(plugin.recipes().isEnabled(r) ? "" : " (disabled)", NamedTextColor.RED)));
                    }
                }
                case "info" -> {
                    if (args.length < 2) {
                        send(s, "Usage: /race info <race>", NamedTextColor.YELLOW);
                        return;
                    }
                    Race r = Race.fromId(args[1]);
                    if (r == null) {
                        send(s, "Unknown race. Try /race list", NamedTextColor.RED);
                        return;
                    }
                    if (s instanceof Player p) plugin.races().sendInfo(p, r);
                    else r.description().forEach(l -> s.sendMessage(Component.text(l)));
                }
                case "set" -> {
                    if (!admin) {
                        send(s, "You don't have permission.", NamedTextColor.RED);
                        return;
                    }
                    if (args.length < 3) {
                        send(s, "Usage: /race set <player> <race>", NamedTextColor.YELLOW);
                        return;
                    }
                    Player t = Bukkit.getPlayerExact(args[1]);
                    Race r = Race.fromId(args[2]);
                    if (t == null || r == null) {
                        send(s, "Player must be online and the race must exist.", NamedTextColor.RED);
                        return;
                    }
                    plugin.races().setRace(t, r, true);
                    send(s, t.getName() + " is now a " + r.displayName() + ".", NamedTextColor.GREEN);
                }
                case "reroll" -> {
                    if (!admin) {
                        send(s, "You don't have permission.", NamedTextColor.RED);
                        return;
                    }
                    Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (s instanceof Player p ? p : null);
                    if (t == null) {
                        send(s, "Usage: /race reroll <player>", NamedTextColor.YELLOW);
                        return;
                    }
                    Race r = plugin.races().randomJoinRace();
                    plugin.races().setRace(t, r, true);
                    send(s, t.getName() + " rolled " + r.displayName() + ".", NamedTextColor.GREEN);
                }
                case "reload" -> {
                    if (!admin) {
                        send(s, "You don't have permission.", NamedTextColor.RED);
                        return;
                    }
                    plugin.reloadEverything();
                    send(s, "Config reloaded.", NamedTextColor.GREEN);
                }
                default -> send(s, "Usage: /race [list|info <race>" + (admin ? "|set|reroll|reload" : "") + "]", NamedTextColor.YELLOW);
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack src, String[] args) {
            boolean admin = src.getSender().hasPermission("races.admin");
            List<String> subs = admin ? List.of("list", "info", "set", "reroll", "reload") : List.of("list", "info");
            if (args.length <= 1) return filter(subs, args.length == 0 ? "" : args[0]);
            List<String> ids = Arrays.stream(Race.values()).map(Race::id).toList();
            if (args[0].equalsIgnoreCase("info") && args.length == 2) return filter(ids, args[1]);
            if (admin && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("reroll")) && args.length == 2) {
                return online(args[1]);
            }
            if (admin && args[0].equalsIgnoreCase("set") && args.length == 3) return filter(ids, args[2]);
            return List.of();
        }
    }

    // ------------------------------------------------------------------ /trusted

    private final class TrustedCommand implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            if (!(src.getSender() instanceof Player p)) {
                send(src.getSender(), "Only players can trust others.", NamedTextColor.RED);
                return;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                Set<UUID> t = plugin.races().trustedOf(p.getUniqueId());
                if (t.isEmpty()) {
                    send(p, "You haven't trusted anyone. Use /trusted add <player>", NamedTextColor.YELLOW);
                    return;
                }
                List<String> names = new ArrayList<>();
                for (UUID id : t) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                    names.add(op.getName() == null ? id.toString() : op.getName());
                }
                send(p, "Trusted (your abilities won't hit them): " + String.join(", ", names), NamedTextColor.GREEN);
                return;
            }
            if (args.length < 2) {
                send(p, "Usage: /trusted <add|remove|list> <player>", NamedTextColor.YELLOW);
                return;
            }
            Player online = Bukkit.getPlayerExact(args[1]);
            OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                send(p, "Couldn't find a player called " + args[1] + ".", NamedTextColor.RED);
                return;
            }
            if (target.getUniqueId().equals(p.getUniqueId())) {
                send(p, "You can't trust yourself; your abilities never hit you anyway.", NamedTextColor.YELLOW);
                return;
            }
            String name = target.getName() == null ? args[1] : target.getName();
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    if (plugin.races().trust(p.getUniqueId(), target.getUniqueId())) {
                        send(p, "Your abilities will no longer hit " + name + ".", NamedTextColor.GREEN);
                        if (online != null) send(online, p.getName() + " has trusted you.", NamedTextColor.GREEN);
                    } else send(p, name + " is already trusted.", NamedTextColor.YELLOW);
                }
                case "remove" -> {
                    if (plugin.races().untrust(p.getUniqueId(), target.getUniqueId())) {
                        send(p, name + " is no longer trusted.", NamedTextColor.YELLOW);
                    } else send(p, name + " wasn't trusted.", NamedTextColor.YELLOW);
                }
                default -> send(p, "Usage: /trusted <add|remove|list> <player>", NamedTextColor.YELLOW);
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack src, String[] args) {
            if (args.length <= 1) return filter(List.of("add", "remove", "list"), args.length == 0 ? "" : args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("remove") && src.getSender() instanceof Player p) {
                List<String> names = new ArrayList<>();
                for (UUID id : plugin.races().trustedOf(p.getUniqueId())) {
                    String n = Bukkit.getOfflinePlayer(id).getName();
                    if (n != null) names.add(n);
                }
                return filter(names, args[1]);
            }
            if (args.length == 2) return online(args[1]);
            return List.of();
        }
    }

    // ------------------------------------------------------------------ menus

    private final class RecipesCommand implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            if (src.getSender() instanceof Player p) plugin.menus().openRecipes(p);
            else send(src.getSender(), "Only players can open menus.", NamedTextColor.RED);
        }
    }

    private final class RecipeAdminCommand implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            if (args.length > 0) {
                // keep vanilla "/recipe give|take ..." working
                Bukkit.dispatchCommand(src.getSender(), "minecraft:recipe " + String.join(" ", args));
                return;
            }
            if (src.getSender() instanceof Player p) plugin.menus().openAdmin(p);
            else send(src.getSender(), "Only players can open menus.", NamedTextColor.RED);
        }

        @Override
        public String permission() {
            return "races.admin";
        }
    }

    private final class AbilityCommand implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            if (src.getSender() instanceof Player p) plugin.abilities().activate(p);
        }
    }
}
