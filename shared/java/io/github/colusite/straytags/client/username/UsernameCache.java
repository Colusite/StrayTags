package io.github.colusite.straytags.client.username;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

// Persistent UUID cache
public class UsernameCache {

    public static final class Entry {
        public String name;
        public long fetchedAt;

        public Entry(String name, long fetchedAt) {
            this.name = name;
            this.fetchedAt = fetchedAt;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("StrayTags/UsernameCache");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>(){}.getType();

    private static final long STALE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long SAVE_DEBOUNCE_MS = 5000L;

    private static final Pattern DASHED_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern UNDASHED_UUID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern USERNAME_CHARS = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");

    private static UsernameCache INSTANCE;

    public static synchronized UsernameCache getInstance() {
        if (INSTANCE == null) INSTANCE = new UsernameCache();
        return INSTANCE;
    }

    private final ConcurrentHashMap<String, Entry> byUuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> uuidByLowerName = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final HttpClient http;
    private final Executor async = ForkJoinPool.commonPool();

    private UsernameCache() {
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "StrayTags-UsernameCache");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        load();
        this.scheduler.scheduleWithFixedDelay(this::flushIfDirty,
                SAVE_DEBOUNCE_MS, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::flushIfDirty, "StrayTags-UsernameCache-Shutdown"));
    }

    public static String normalizeUuid(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (DASHED_UUID.matcher(s).matches()) return s.toLowerCase(Locale.ROOT);
        if (UNDASHED_UUID.matcher(s).matches()) {
            String l = s.toLowerCase(Locale.ROOT);
            return l.substring(0, 8) + "-" + l.substring(8, 12) + "-"
                    + l.substring(12, 16) + "-" + l.substring(16, 20) + "-" + l.substring(20);
        }
        return null;
    }

    public static boolean looksLikeUsername(String raw) {
        return raw != null && USERNAME_CHARS.matcher(raw).matches();
    }

    public Optional<String> getUsername(String uuid) {
        String norm = normalizeUuid(uuid);
        if (norm == null) return Optional.empty();
        Entry e = byUuid.get(norm);
        return (e == null || e.name == null) ? Optional.empty() : Optional.of(e.name);
    }

    public Optional<String> getUuid(String username) {
        if (username == null) return Optional.empty();
        return Optional.ofNullable(uuidByLowerName.get(username.toLowerCase(Locale.ROOT)));
    }

    public boolean isStale(String uuid) {
        String norm = normalizeUuid(uuid);
        if (norm == null) return true;
        Entry e = byUuid.get(norm);
        if (e == null) return true;
        return System.currentTimeMillis() - e.fetchedAt > STALE_MS;
    }

    public boolean isCached(String uuid) {
        String norm = normalizeUuid(uuid);
        return norm != null && byUuid.containsKey(norm);
    }

    public void observePlayer(UUID playerUuid, String playerName) {
        if (playerUuid == null || playerName == null || playerName.isEmpty()) return;
        String norm = playerUuid.toString().toLowerCase(Locale.ROOT);
        Entry prev = byUuid.get(norm);
        if (prev != null && playerName.equals(prev.name)) {
            // Refresh timestamp so it doesn't go stale while player is visible
            if (System.currentTimeMillis() - prev.fetchedAt > TimeUnit.HOURS.toMillis(1)) {
                put(norm, playerName);
            }
            return;
        }
        put(norm, playerName);
    }

    public CompletableFuture<Optional<String>> resolveUsername(String uuid) {
        String norm = normalizeUuid(uuid);
        if (norm == null) return CompletableFuture.completedFuture(Optional.empty());
        String undashed = norm.replace("-", "");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + undashed))
                .timeout(Duration.ofSeconds(6))
                .GET().build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200 || resp.body() == null || resp.body().isEmpty()) {
                        return Optional.<String>empty();
                    }
                    try {
                        Map<String, Object> body = GSON.fromJson(resp.body(), new TypeToken<Map<String, Object>>(){}.getType());
                        Object n = body == null ? null : body.get("name");
                        if (n instanceof String s && !s.isEmpty()) {
                            put(norm, s);
                            return Optional.of(s);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("resolveUsername parse failed for {}: {}", norm, e.getMessage());
                    }
                    return Optional.<String>empty();
                })
                .exceptionally(t -> {
                    LOGGER.debug("resolveUsername network failed for {}: {}", norm, t.getMessage());
                    return Optional.empty();
                });
    }

    public void resolveUuid(String username) {
        if (!looksLikeUsername(username)) {
            CompletableFuture.completedFuture(Optional.empty());
            return;
        }
        String body = GSON.toJson(List.of(username));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/profiles/minecraft"))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200 || resp.body() == null || resp.body().isEmpty()) {
                        return Optional.<String>empty();
                    }
                    try {
                        List<Map<String, Object>> list = GSON.fromJson(resp.body(),
                                new TypeToken<List<Map<String, Object>>>() {
                                }.getType());
                        if (list == null || list.isEmpty()) return Optional.<String>empty();
                        Map<String, Object> first = list.getFirst();
                        Object idObj = first.get("id");
                        Object nameObj = first.get("name");
                        if (idObj instanceof String id && nameObj instanceof String name) {
                            String norm = normalizeUuid(id);
                            if (norm != null) {
                                put(norm, name);
                                return Optional.of(norm);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("resolveUuid parse failed for {}: {}", username, e.getMessage());
                    }
                    return Optional.<String>empty();
                })
                .exceptionally(t -> {
                    LOGGER.debug("resolveUuid network failed for {}: {}", username, t.getMessage());
                    return Optional.empty();
                });
    }

    public CompletableFuture<Void> resolveBulk(List<String> uuids) {
        if (uuids == null || uuids.isEmpty()) return CompletableFuture.completedFuture(null);
        List<CompletableFuture<?>> futures = new ArrayList<>(uuids.size());
        for (String u : uuids) {
            String norm = normalizeUuid(u);
            if (norm == null) continue;
            futures.add(resolveUsername(norm));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void put(String normUuid, String name) {
        Entry e = new Entry(name, System.currentTimeMillis());
        Entry prev = byUuid.put(normUuid, e);
        if (prev != null && prev.name != null && !prev.name.equals(name)) {
            uuidByLowerName.remove(prev.name.toLowerCase(Locale.ROOT), normUuid);
        }
        uuidByLowerName.put(name.toLowerCase(Locale.ROOT), normUuid);
        dirty.set(true);
    }

    private Path cachePath() {
        return FabricLoader.getInstance().getGameDir().resolve("straytags").resolve("usernames.json");
    }

    private void load() {
        Path path = cachePath();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            Map<String, Entry> loaded = GSON.fromJson(json, MAP_TYPE);
            if (loaded == null) return;
            for (Map.Entry<String, Entry> e : loaded.entrySet()) {
                String norm = normalizeUuid(e.getKey());
                if (norm == null || e.getValue() == null || e.getValue().name == null) continue;
                byUuid.put(norm, e.getValue());
                uuidByLowerName.put(e.getValue().name.toLowerCase(Locale.ROOT), norm);
            }
            LOGGER.info("[StrayTags] Loaded {} cached usernames from {}", byUuid.size(), path);
        } catch (Exception e) {
            LOGGER.warn("[StrayTags] Failed to load username cache: {}", e.getMessage());
        }
    }

    private void flushIfDirty() {
        if (!dirty.compareAndSet(true, false)) return;
        Path path = cachePath();
        try {
            Files.createDirectories(path.getParent());
            Map<String, Entry> snapshot = new LinkedHashMap<>(byUuid);
            String json = GSON.toJson(snapshot, MAP_TYPE);
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(tmp, json);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("[StrayTags] Failed to save username cache: {}", e.getMessage());
            dirty.set(true);
        } catch (Exception e) {
            LOGGER.warn("[StrayTags] Unexpected cache save error: {}", e.getMessage());
        }
    }
}
