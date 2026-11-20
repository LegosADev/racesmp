package com.julian.races;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Optional resource pack that gives the 9 Lives totem its cat texture. The plugin always writes the
 * pack to plugins/Races/races-pack.zip. It can then either serve it itself (mode: host) or send
 * players a URL you uploaded it to (mode: url).
 */
public final class PackHost {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("races:nine_lives_pack".getBytes(StandardCharsets.UTF_8));

    private final RacesPlugin plugin;
    private byte[] pack;
    private String sha1;
    private HttpServer server;
    private String url;

    public PackHost(RacesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        url = null;
        try (InputStream in = plugin.getResource("races-pack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("races-pack.zip missing from the plugin jar.");
                return;
            }
            pack = in.readAllBytes();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read races-pack.zip: " + e.getMessage());
            return;
        }
        try {
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
            File out = new File(plugin.getDataFolder(), "races-pack.zip");
            plugin.getDataFolder().mkdirs();
            Files.write(out.toPath(), pack);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not export races-pack.zip: " + e.getMessage());
        }

        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) return;
        String mode = plugin.getConfig().getString("resource-pack.mode", "host");
        if ("url".equalsIgnoreCase(mode)) {
            String u = plugin.getConfig().getString("resource-pack.url", "");
            if (u == null || u.isBlank()) {
                plugin.getLogger().info("resource-pack.mode is 'url' but no url is set; the 9 Lives texture pack won't be sent.");
                return;
            }
            url = u;
            plugin.getLogger().info("Sending resource pack from " + url + " (SHA-1 " + sha1 + ")");
            return;
        }

        String address = plugin.getConfig().getString("resource-pack.public-address", "");
        if (address == null || address.isBlank()) address = Bukkit.getIp();
        if (address == null || address.isBlank()) {
            plugin.getLogger().info("Resource pack: set resource-pack.public-address in config.yml (the IP or domain "
                    + "players join with) to enable the 9 Lives texture. The pack was written to plugins/Races/races-pack.zip.");
            return;
        }
        int port = plugin.getConfig().getInt("resource-pack.port", 8164);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/races-pack.zip", ex -> {
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, pack.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(pack);
                }
            });
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Races-PackHost");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            url = "http://" + address + ":" + port + "/races-pack.zip";
            plugin.getLogger().info("Hosting the 9 Lives resource pack at " + url);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not host the resource pack on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void send(Player p) {
        if (url == null || sha1 == null) return;
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), sha1);
            p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .replace(false)
                    .required(plugin.getConfig().getBoolean("resource-pack.required", false))
                    .prompt(Component.text("Adds the cat texture for the 9 Lives totem.", NamedTextColor.GOLD))
                    .build());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not send resource pack to " + p.getName() + ": " + e.getMessage());
        }
    }
}
