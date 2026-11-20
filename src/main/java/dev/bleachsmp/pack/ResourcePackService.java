package dev.bleachsmp.pack;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Zips the plugin's resource pack, serves it over HTTP (or points players at an external copy), sends it
 * on join, and tracks which players have it loaded so models can fall back for everyone else.
 */
public final class ResourcePackService implements Listener {
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("bleachsmp:resource-pack".getBytes(StandardCharsets.UTF_8));
    public static final String FILE_NAME = "BleachSMP-resources.zip";

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final List<BiConsumer<Player, Boolean>> listeners = new ArrayList<>();
    private byte[] zip;
    private byte[] sha1;
    private String hash;
    private String externalUrl;
    private String publicHost;
    private int port;
    private boolean required;
    private Component prompt;
    private HttpServer server;
    private ExecutorService executor;

    public ResourcePackService(JavaPlugin plugin, Map<String, byte[]> files) {
        this.plugin = plugin;
        var config = plugin.getConfig();
        enabled = config.getBoolean("resource-pack.enabled", true);
        if (!enabled) {
            return;
        }
        externalUrl = config.getString("resource-pack.url", "").trim();
        publicHost = config.getString("resource-pack.public-host", "").trim();
        port = config.getInt("resource-pack.port", 8163);
        required = config.getBoolean("resource-pack.required", false);
        prompt = Component.text(config.getString("resource-pack.prompt",
                "BleachSMP uses a resource pack for its Hollow models."), NamedTextColor.GRAY);
        zip = zip(files);
        try {
            sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        hash = HexFormat.of().formatHex(sha1);
        File copy = new File(plugin.getDataFolder(), FILE_NAME);
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.write(copy.toPath(), zip);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write " + copy + ": " + exception.getMessage());
        }
        if (externalUrl.isEmpty()) {
            startServer();
        } else {
            plugin.getLogger().info("Sending the resource pack from " + externalUrl + " (SHA-1 " + hash
                    + "). Upload " + copy + " there whenever the plugin is updated.");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(this::send);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Whether this player has the pack loaded, and so sees item models. */
    public boolean hasPack(Player player) {
        return enabled && loaded.contains(player.getUniqueId());
    }

    /** Called on the main thread with the player and whether they now have the pack. */
    public void onChange(BiConsumer<Player, Boolean> listener) {
        listeners.add(listener);
    }

    public void shutdown() {
        if (server != null) {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not serve the resource pack on port " + port + ": " + exception.getMessage()
                    + ". Players will see the block-built Hollows. Free the port, or set resource-pack.url.");
            return;
        }
        server.createContext("/", exchange -> {
            try (exchange) {
                if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                boolean head = "HEAD".equals(exchange.getRequestMethod());
                exchange.sendResponseHeaders(200, head ? -1 : zip.length);
                if (!head) {
                    try (OutputStream body = exchange.getResponseBody()) {
                        body.write(zip);
                    }
                }
            }
        });
        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "BleachSMP resource pack");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        plugin.getLogger().info("Serving the resource pack on port " + port + " (SHA-1 " + hash + ").");
    }

    private String url(Player player) {
        if (!externalUrl.isEmpty()) {
            return externalUrl;
        }
        if (server == null) {
            return null;
        }
        String host = publicHost;
        if (host.isEmpty()) {
            // The address the player typed reaches this machine, so the pack server is there too.
            InetSocketAddress virtual = player.getVirtualHost();
            host = virtual != null ? virtual.getHostString() : Bukkit.getIp();
        }
        if (host == null || host.isEmpty()) {
            return null;
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + port + "/" + hash + ".zip";
    }

    private void send(Player player) {
        String url = url(player);
        if (url == null) {
            plugin.getLogger().warning("No address to send the resource pack to " + player.getName()
                    + " from. Set resource-pack.public-host.");
            return;
        }
        try {
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), hash))
                    .required(required)
                    .prompt(prompt)
                    .replace(false));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid resource pack URL " + url + ": " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        Player player = event.getPlayer();
        Boolean has = switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> true;
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> false;
            default -> null;
        };
        if (has == null) {
            return;
        }
        if (!has && event.getStatus() != PlayerResourcePackStatusEvent.Status.DECLINED) {
            plugin.getLogger().warning(player.getName() + " could not load the resource pack: " + event.getStatus());
        }
        boolean changed = has ? loaded.add(player.getUniqueId()) : loaded.remove(player.getUniqueId());
        if (changed) {
            listeners.forEach(listener -> listener.accept(player, has));
        }
    }

    /** A zip with sorted entries and fixed timestamps, so the same files always hash the same. */
    private static byte[] zip(Map<String, byte[]> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : new TreeMap<>(files).entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(315532800000L);
                out.putNextEntry(entry);
                out.write(file.getValue());
                out.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return bytes.toByteArray();
    }
}
