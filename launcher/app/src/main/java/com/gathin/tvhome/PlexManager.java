package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Plex account, server and recent-library access. Network work is kept off the UI thread. */
final class PlexManager {
    static final String PREFS = "plex";
    static final String PLEX_PACKAGE = "com.plexapp.android";
    private static final String CLOUD = "https://clients.plex.tv";
    private static final String USER_API = "https://plex.tv/api/v2/user";
    private static final String RESOURCE_API = CLOUD + "/api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=1";
    private static final String PRODUCT = "TV Home";
    private static final String KEY_ALIAS = "tvhome_plex_rsa";
    private static final String AES_ALIAS = "tvhome_plex_aes";
    private static final int MAX_ITEMS = 20;
    private static final int EPISODE_SCAN_LIMIT = 200;
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final AtomicInteger SESSION_GENERATION = new AtomicInteger();
    private static final LruCache<String, android.graphics.Bitmap> POSTERS =
            new LruCache<String, android.graphics.Bitmap>(12 * 1024 * 1024) {
                @Override protected int sizeOf(String key, android.graphics.Bitmap value) {
                    return value.getByteCount();
                }
            };

    interface Result<T> { void done(T value, Exception error); }

    static final class Pin {
        final String id, code, deviceJwt;
        Pin(String id, String code, String deviceJwt) {
            this.id = id;
            this.code = code;
            this.deviceJwt = deviceJwt;
        }
    }

    static final class Server {
        final String id, name, accessToken;
        final List<String> urls;
        Server(String id, String name, String accessToken, List<String> urls) {
            this.id = id;
            this.name = name;
            this.accessToken = accessToken;
            this.urls = urls;
        }
        JSONObject toJson() throws JSONException {
            JSONObject out = new JSONObject();
            out.put("id", id);
            out.put("name", name);
            JSONArray values = new JSONArray();
            for (String url : urls) values.put(url);
            out.put("urls", values);
            return out;
        }
        static Server fromJson(JSONObject value, String accessToken) throws JSONException {
            List<String> urls = new ArrayList<>();
            JSONArray array = value.optJSONArray("urls");
            if (array != null) for (int i = 0; i < array.length(); i++) urls.add(array.getString(i));
            return new Server(value.getString("id"), value.optString("name", "Plex"), accessToken, urls);
        }
    }

    static final class Library {
        final String serverId, serverName, id, title, type;
        Library(String serverId, String serverName, String id, String title, String type) {
            this.serverId = serverId;
            this.serverName = serverName;
            this.id = id;
            this.title = title;
            this.type = type;
        }
        String key() { return serverId + "|" + id; }
        @Override public String toString() { return serverName + " · " + title; }
    }

    static final class MediaItem {
        final String serverId, serverName, libraryId, ratingKey, key, title, thumb, type, episode, accessToken;
        final long addedAt;
        final String baseUrl;
        MediaItem(String serverId, String serverName, String libraryId, String ratingKey, String key,
                  String title, String thumb, String type, String episode, long addedAt,
                  String baseUrl, String accessToken) {
            this.serverId = serverId;
            this.serverName = serverName;
            this.libraryId = libraryId;
            this.ratingKey = ratingKey;
            this.key = key;
            this.title = title;
            this.thumb = thumb;
            this.type = type;
            this.episode = episode;
            this.addedAt = addedAt;
            this.baseUrl = baseUrl;
            this.accessToken = accessToken;
        }
        JSONObject toJson() throws JSONException {
            JSONObject value = new JSONObject();
            value.put("serverId", serverId);
            value.put("serverName", serverName);
            value.put("libraryId", libraryId);
            value.put("ratingKey", ratingKey);
            value.put("key", key);
            value.put("title", title);
            value.put("thumb", thumb);
            value.put("type", type);
            value.put("episode", episode);
            value.put("addedAt", addedAt);
            return value;
        }
        static MediaItem fromJson(JSONObject value) {
            return new MediaItem(value.optString("serverId"), value.optString("serverName"),
                    value.optString("libraryId"), value.optString("ratingKey"), value.optString("key"), value.optString("title"),
                    value.optString("thumb"), value.optString("type"), value.optString("episode"),
                    value.optLong("addedAt"), null, null);
        }
    }

    static final class FetchResult {
        final List<MediaItem> items;
        final Set<String> offlineServers;
        FetchResult(List<MediaItem> items, Set<String> offlineServers) {
            this.items = items;
            this.offlineServers = offlineServers;
        }
    }

    private static final class PlexHttpException extends IOException {
        final int status;
        PlexHttpException(int status) {
            super("Plex respondió HTTP " + status + ".");
            this.status = status;
        }
    }

    private final SharedPreferences prefs;
    private volatile List<Server> posterServerCache;

    PlexManager(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static <T> void execute(Callable<T> task, Result<T> result) {
        IO.execute(() -> {
            try { result.done(task.call(), null); }
            catch (Exception e) { result.done(null, e); }
        });
    }

    boolean linked() {
        try { return !readToken().isEmpty(); }
        catch (Exception ignored) { return false; }
    }
    String username() { return prefs.getString("username", ""); }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    void setEnabled(boolean value) { prefs.edit().putBoolean("enabled", value).apply(); }
    boolean allLibraries() { return prefs.getBoolean("all_libraries", true); }
    Set<String> selectedServers() { return readSet("servers"); }
    Set<String> selectedLibraries() { return readSet("libraries"); }
    boolean configured() {
        return linked() && !selectedServers().isEmpty()
                && (allLibraries() || !selectedLibraries().isEmpty());
    }
    void setSelectedServers(Set<String> ids, List<Server> servers) throws Exception {
        synchronized (PlexManager.class) {
            saveServerTokens(servers, ids);
            writeSet("servers", ids);
        }
    }
    void setSelectedLibraries(Set<String> ids, boolean all) {
        writeSet("libraries", ids);
        prefs.edit().putBoolean("all_libraries", all).apply();
    }

    private Set<String> readSet(String key) {
        Set<String> result = new HashSet<>();
        try {
            JSONArray values = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < values.length(); i++) result.add(values.getString(i));
        } catch (JSONException ignored) {
        }
        return result;
    }

    private void writeSet(String key, Set<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        prefs.edit().putString(key, array.toString()).apply();
    }

    private String clientId() {
        String value = prefs.getString("client_id", null);
        if (value == null) {
            value = UUID.randomUUID().toString();
            prefs.edit().putString("client_id", value).apply();
        }
        return value;
    }

    Pin beginPin() throws Exception {
        KeyPair keyPair = signingKey();
        JSONObject body = new JSONObject();
        body.put("jwk", jwk((RSAPublicKey) keyPair.getPublic()));
        body.put("strong", false);
        JSONObject response = requestJson(new URL(CLOUD + "/api/v2/pins"), null, body, "POST");
        String id = response.optString("id"), code = response.optString("code");
        if (id.isEmpty() || code.isEmpty()) throw new IOException("Plex no devolvió un código de vinculación.");
        return new Pin(id, code, deviceJwt(null));
    }

    String pollPin(Pin pin) throws Exception {
        String url = CLOUD + "/api/v2/pins/" + Uri.encode(pin.id)
                + "?deviceJWT=" + URLEncoder.encode(pin.deviceJwt, "UTF-8");
        JSONObject response = requestJson(new URL(url), null, null, "GET");
        String token = response.optString("authToken");
        return token.isEmpty() || token.equals("null") ? null : token;
    }

    List<Server> completeLink(String token) throws Exception {
        int generation = SESSION_GENERATION.incrementAndGet();
        JSONObject user = requestJson(new URL(USER_API), token, null, "GET");
        String name = user.optString("username");
        if (name.isEmpty()) name = user.optString("title", "Plex");
        JSONObject resources = requestJson(new URL(RESOURCE_API), token, null, "GET");
        List<Server> servers = parseServers(resources);
        synchronized (PlexManager.class) {
            if (generation != SESSION_GENERATION.get()) return new ArrayList<>();
            saveToken(token);
            saveServerTokens(servers, selectedServers());
            prefs.edit().putString("username", name).putString("resources", serializeServers(servers))
                    .remove("cache").remove("cache_account").apply();
        }
        return servers;
    }

    List<Server> discoverServers() throws Exception {
        int generation = SESSION_GENERATION.get();
        try {
            String token = currentToken();
            JSONObject resources = requestJson(new URL(RESOURCE_API), token, null, "GET");
            List<Server> servers = parseServers(resources);
            synchronized (PlexManager.class) {
                if (generation == SESSION_GENERATION.get())
                    {
                        saveServerTokens(servers, selectedServers());
                        prefs.edit().putString("resources", serializeServers(servers)).apply();
                    }
            }
            return servers;
        } catch (Exception e) {
            List<Server> cached = cachedServers();
            if (!cached.isEmpty()) return cached;
            throw e;
        }
    }

    private List<Server> cachedServers() {
        List<Server> result = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(prefs.getString("resources", "[]"));
            JSONObject tokens = new JSONObject();
            try {
                String saved = prefs.getString("server_tokens", "");
                if (!saved.isEmpty()) tokens = new JSONObject(decryptText(saved));
            } catch (Exception ignored) {
            }
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.getJSONObject(i);
                result.add(Server.fromJson(value, tokens.optString(value.optString("id"), "")));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private List<Server> posterServers() {
        List<Server> cached = posterServerCache;
        if (cached != null) return cached;
        synchronized (this) {
            if (posterServerCache == null) posterServerCache = cachedServers();
            return posterServerCache;
        }
    }

    List<Library> libraries(List<Server> servers) throws Exception {
        if (!linked()) throw new IOException("Vincula primero una cuenta Plex.");
        List<Library> result = new ArrayList<>();
        for (Server server : servers) {
            JSONObject json = requestServer(server, "/library/sections");
            JSONObject container = json.optJSONObject("MediaContainer");
            if (container == null) continue;
            for (JSONObject section : objects(container, "Directory")) {
                String type = section.optString("type");
                if (!type.equals("movie") && !type.equals("show")) continue;
                result.add(new Library(server.id, server.name, section.optString("key"),
                        section.optString("title", "Biblioteca"), type));
            }
        }
        return result;
    }

    FetchResult loadCache() {
        if (!username().equals(prefs.getString("cache_account", "")))
            return new FetchResult(new ArrayList<>(), new HashSet<>());
        List<MediaItem> items = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(prefs.getString("cache", "[]"));
            for (int i = 0; i < values.length(); i++) items.add(MediaItem.fromJson(values.getJSONObject(i)));
            items = PlexCatalog.filterSelection(items, selectedServers(), selectedLibraries(), allLibraries());
        } catch (Exception ignored) {
        }
        return new FetchResult(items, new HashSet<>());
    }

    long cacheAgeMs() { return System.currentTimeMillis() - prefs.getLong("cache_time", 0); }

    synchronized FetchResult refreshRecent() throws Exception {
        if (!configured()) return new FetchResult(new ArrayList<>(), new HashSet<>());
        int generation = SESSION_GENERATION.get();
        String accountToken = null;
        Exception accountError = null;
        try { accountToken = currentToken(); }
        catch (Exception e) { accountError = e; }
        if (generation != SESSION_GENERATION.get()) throw new IOException("La cuenta Plex se ha desvinculado.");
        List<Server> servers = cachedServers();
        if (accountToken != null) {
            try {
                List<Server> refreshed = parseServers(requestJson(new URL(RESOURCE_API), accountToken, null, "GET"));
                Set<String> found = new HashSet<>();
                for (Server server : refreshed) found.add(server.id);
                for (Server cached : servers) {
                    if (selectedServers().contains(cached.id) && !found.contains(cached.id))
                        refreshed.add(new Server(cached.id, cached.name, "", cached.urls));
                }
                servers = refreshed;
                synchronized (PlexManager.class) {
                    if (generation == SESSION_GENERATION.get()) {
                        saveServerTokens(servers, selectedServers());
                        prefs.edit().putString("resources", serializeServers(servers)).apply();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (servers.isEmpty() && accountError != null) throw accountError;

        Set<String> selectedServerIds = selectedServers(), selectedLibraryIds = selectedLibraries();
        Map<String, Server> serverById = new HashMap<>();
        for (Server server : servers) serverById.put(server.id, server);
        List<MediaItem> fresh = new ArrayList<>();
        Set<String> offline = new HashSet<>();
        for (String serverId : selectedServerIds) {
            Server server = serverById.get(serverId);
            if (server == null) { offline.add(serverId); continue; }
            boolean loaded = false;
            Set<String> denied = new HashSet<>();
            for (String base : server.urls) {
                try {
                    fresh.addAll(loadServerRecent(server, base, allLibraries(), selectedLibraryIds, denied));
                    loaded = true;
                    break;
                } catch (Exception ignored) {
                }
            }
            offline.addAll(denied);
            if (!loaded) {
                offline.add(serverId);
                for (MediaItem item : loadCache().items) {
                    if (item.serverId.equals(serverId)) fresh.add(item);
                }
            }
        }
        fresh = PlexCatalog.mergeAndLimit(fresh, MAX_ITEMS);
        saveCache(fresh, generation);
        return new FetchResult(fresh, offline);
    }

    private List<MediaItem> loadServerRecent(Server server, String base,
                                             boolean all, Set<String> selectedLibraryIds,
                                             Set<String> denied) throws Exception {
        JSONObject sectionJson;
        try {
            sectionJson = requestServer(server, base, "/library/sections");
        } catch (PlexHttpException e) {
            if (e.status == 401 || e.status == 403) { denied.add(server.id); return new ArrayList<>(); }
            throw e;
        }
        JSONObject container = sectionJson.optJSONObject("MediaContainer");
        if (container == null) throw new IOException("Plex no devolvió las bibliotecas.");
        List<MediaItem> result = new ArrayList<>();
        for (JSONObject section : objects(container, "Directory")) {
            String type = section.optString("type"), sectionId = section.optString("key");
            if (!type.equals("movie") && !type.equals("show")) continue;
            if (!all && !selectedLibraryIds.contains(server.id + "|" + sectionId)) continue;
            try {
                if (type.equals("movie")) result.addAll(loadMovies(server, base, sectionId));
                else result.addAll(loadShows(server, base, sectionId));
            } catch (PlexHttpException e) {
                if (e.status == 401 || e.status == 403) denied.add(server.id);
                else throw e;
            }
        }
        return result;
    }

    private List<MediaItem> loadMovies(Server server, String base, String sectionId) throws Exception {
        String path = "/library/sections/" + Uri.encode(sectionId) + "/all?type=1&sort=addedAt%3Adesc"
                + "&X-Plex-Container-Start=0&X-Plex-Container-Size=" + MAX_ITEMS;
        JSONObject container = requestServer(server, base, path).optJSONObject("MediaContainer");
        List<MediaItem> result = new ArrayList<>();
        if (container == null) return result;
        for (JSONObject value : objects(container, "Metadata")) {
            String rating = value.optString("ratingKey");
            if (rating.isEmpty()) continue;
            String key = value.optString("key", "/library/metadata/" + rating);
            result.add(new MediaItem(server.id, server.name, sectionId, rating, key,
                    value.optString("title", "Película"), value.optString("thumb"), "movie", "",
                    value.optLong("addedAt"), base, server.accessToken));
        }
        return result;
    }

    private List<MediaItem> loadShows(Server server, String base, String sectionId) throws Exception {
        Map<String, MediaItem> shows = new LinkedHashMap<>();
        int total = Integer.MAX_VALUE;
        for (int start = 0; start < EPISODE_SCAN_LIMIT && shows.size() < MAX_ITEMS && start < total; start += 50) {
            String path = "/library/sections/" + Uri.encode(sectionId)
                    + "/all?type=4&sort=addedAt%3Adesc&X-Plex-Container-Start=" + start
                    + "&X-Plex-Container-Size=50";
            JSONObject container = requestServer(server, base, path).optJSONObject("MediaContainer");
            if (container == null) break;
            total = container.optInt("totalSize", container.optInt("size", 0));
            List<JSONObject> episodes = objects(container, "Metadata");
            for (JSONObject episode : episodes) {
                String showId = episode.optString("grandparentRatingKey");
                if (showId.isEmpty() || shows.containsKey(showId)) continue;
                int season = episode.optInt("parentIndex", 0), number = episode.optInt("index", 0);
                String episodeLabel = season > 0 && number > 0
                        ? String.format(java.util.Locale.getDefault(), "T%02d · E%02d", season, number)
                        : episode.optString("title", "");
                shows.put(showId, new MediaItem(server.id, server.name, sectionId, showId,
                        "/library/metadata/" + showId, episode.optString("grandparentTitle", "Serie"),
                        episode.optString("grandparentThumb"), "show", episodeLabel,
                        episode.optLong("addedAt"), base, server.accessToken));
            }
            if (episodes.isEmpty()) break;
        }
        return new ArrayList<>(shows.values());
    }

    private void saveCache(List<MediaItem> items, int generation) {
        JSONArray array = new JSONArray();
        try {
            for (MediaItem item : items) array.put(item.toJson());
            synchronized (PlexManager.class) {
                if (generation == SESSION_GENERATION.get())
                    prefs.edit().putString("cache", array.toString()).putString("cache_account", username())
                            .putLong("cache_time", System.currentTimeMillis()).apply();
            }
        } catch (JSONException ignored) {
        }
    }

    android.graphics.Bitmap poster(MediaItem item) throws Exception {
        int generation = SESSION_GENERATION.get();
        if (item == null || item.thumb == null
                || !item.thumb.startsWith("/") || item.thumb.startsWith("//")) return null;
        String cacheKey = item.serverId + "|" + item.thumb;
        android.graphics.Bitmap cached = POSTERS.get(cacheKey);
        if (cached != null) return cached;
        Server server = null;
        for (Server candidate : posterServers()) {
            if (candidate.id.equals(item.serverId)) { server = candidate; break; }
        }
        String token = item.accessToken;
        if ((token == null || token.isEmpty()) && server != null) token = server.accessToken;
        if (token == null || token.isEmpty()) return null;
        List<String> urls = PlexPosterSources.ordered(item.baseUrl,
                server == null ? Collections.emptyList() : server.urls);
        String path = "/photo/:/transcode?url=" + URLEncoder.encode(item.thumb, "UTF-8")
                + "&width=240&height=360&minSize=1&upscale=0";
        Exception failure = null;
        for (String base : urls) {
            try {
                byte[] bytes = requestBytes(new URL(base + path), token, "image/jpeg");
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                int sample = 1;
                while (options.outWidth / sample > 480 || options.outHeight / sample > 720) sample *= 2;
                options.inJustDecodeBounds = false;
                options.inSampleSize = sample;
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap != null) synchronized (PlexManager.class) {
                    if (generation == SESSION_GENERATION.get()) POSTERS.put(cacheKey, bitmap);
                    return bitmap;
                }
            } catch (Exception e) {
                failure = e;
            }
        }
        if (failure != null) throw failure;
        return null;
    }

    static void open(Context context, MediaItem item) {
        if (item == null) return;
        String route = "https://app.plex.tv/desktop/#!/server/" + Uri.encode(item.serverId)
                + "/details?key=" + Uri.encode(item.key);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(route))
                .setPackage(PLEX_PACKAGE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); }
        catch (Exception ignored) {
            android.content.Intent launch = context.getPackageManager().getLaunchIntentForPackage(PLEX_PACKAGE);
            if (launch == null) {
                android.widget.Toast.makeText(context, R.string.plex_app_missing,
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                android.widget.Toast.makeText(context, R.string.plex_opening_app,
                        android.widget.Toast.LENGTH_SHORT).show();
            } catch (Exception ignoredAgain) {
                android.widget.Toast.makeText(context, R.string.plex_app_missing,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    void unlink() {
        synchronized (PlexManager.class) {
            SESSION_GENERATION.incrementAndGet();
            prefs.edit().clear().apply();
            posterServerCache = null;
            POSTERS.evictAll();
            try {
                KeyStore store = KeyStore.getInstance("AndroidKeyStore");
                store.load(null);
                store.deleteEntry(KEY_ALIAS);
                store.deleteEntry(AES_ALIAS);
            } catch (Exception ignored) {
            }
        }
    }

    private String currentToken() throws Exception {
        int generation = SESSION_GENERATION.get();
        String token = readToken();
        if (token.isEmpty()) throw new IOException("Vincula primero una cuenta Plex.");
        if (tokenExpiry(token) > System.currentTimeMillis() / 1000L + 24 * 60 * 60) return token;
        try {
            JSONObject nonce = requestJson(new URL(CLOUD + "/api/v2/auth/nonce"), null, null, "GET");
            JSONObject body = new JSONObject();
            body.put("jwt", deviceJwt(nonce.optString("nonce")));
            JSONObject refreshed = requestJson(new URL(CLOUD + "/api/v2/auth/token"), null, body, "POST");
            String value = refreshed.optString("auth_token", refreshed.optString("authToken"));
            if (value.isEmpty()) throw new IOException("Plex no renovó la sesión.");
            synchronized (PlexManager.class) {
                if (generation != SESSION_GENERATION.get()) throw new IOException("La cuenta Plex se ha desvinculado.");
                saveToken(value);
            }
            return value;
        } catch (Exception e) {
            if (tokenExpiry(token) > System.currentTimeMillis() / 1000L) return token;
            throw e;
        }
    }

    private long tokenExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            JSONObject payload = new JSONObject(new String(Base64.decode(parts[1],
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8));
            return payload.optLong("exp", 0);
        } catch (Exception ignored) { return 0; }
    }

    private String readToken() throws Exception {
        String saved = prefs.getString("token", "");
        return saved.isEmpty() ? "" : decryptText(saved);
    }

    private String encryptText(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] packed = new byte[cipher.getIV().length + encrypted.length];
        System.arraycopy(cipher.getIV(), 0, packed, 0, cipher.getIV().length);
        System.arraycopy(encrypted, 0, packed, cipher.getIV().length, encrypted.length);
        return Base64.encodeToString(packed, Base64.NO_WRAP);
    }

    private String decryptText(String saved) throws Exception {
        byte[] packed = Base64.decode(saved, Base64.NO_WRAP);
        if (packed.length <= 12) throw new IOException("La sesión Plex guardada no se puede leer.");
        byte[] iv = new byte[12], encrypted = new byte[packed.length - 12];
        System.arraycopy(packed, 0, iv, 0, iv.length);
        System.arraycopy(packed, iv.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private void saveToken(String token) throws Exception {
        prefs.edit().putString("token", encryptText(token)).apply();
    }

    private SecretKey aesKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(AES_ALIAS)) return (SecretKey) store.getKey(AES_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(AES_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setKeySize(256).build());
        return generator.generateKey();
    }

    private KeyPair signingKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            Key privateKey = store.getKey(KEY_ALIAS, null);
            return new KeyPair(store.getCertificate(KEY_ALIAS).getPublicKey(), (java.security.PrivateKey) privateKey);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).build());
        return generator.generateKeyPair();
    }

    private String keyId(RSAPublicKey key) throws Exception {
        String exponent = base64Url(unsigned(key.getPublicExponent().toByteArray()));
        String modulus = base64Url(unsigned(key.getModulus().toByteArray()));
        String thumbprint = "{\"e\":\"" + exponent + "\",\"kty\":\"RSA\",\"n\":\"" + modulus + "\"}";
        return base64Url(MessageDigest.getInstance("SHA-256")
                .digest(thumbprint.getBytes(StandardCharsets.UTF_8)));
    }

    private JSONObject jwk(RSAPublicKey key) throws Exception {
        JSONObject value = new JSONObject();
        value.put("kty", "RSA");
        value.put("n", base64Url(unsigned(key.getModulus().toByteArray())));
        value.put("e", base64Url(unsigned(key.getPublicExponent().toByteArray())));
        value.put("kid", keyId(key));
        value.put("use", "sig");
        value.put("alg", "RS256");
        return value;
    }

    private String deviceJwt(String nonce) throws Exception {
        KeyPair pair = signingKey();
        JSONObject header = new JSONObject();
        header.put("alg", "RS256");
        header.put("kid", keyId((RSAPublicKey) pair.getPublic()));
        header.put("typ", "JWT");
        JSONObject claims = new JSONObject();
        if (nonce != null && !nonce.isEmpty()) {
            claims.put("nonce", nonce);
            claims.put("scope", "username,email,friendly_name,restricted");
        }
        long now = System.currentTimeMillis() / 1000L;
        claims.put("aud", "plex.tv");
        claims.put("iss", clientId());
        claims.put("iat", now);
        claims.put("exp", now + 300);
        String unsigned = base64Url(header.toString().getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(claims.toString().getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update(unsigned.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + base64Url(signature.sign());
    }

    private static byte[] unsigned(byte[] value) {
        if (value.length > 1 && value[0] == 0) {
            byte[] trimmed = new byte[value.length - 1];
            System.arraycopy(value, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return value;
    }
    private static String base64Url(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private JSONObject requestServer(Server server, String path) throws Exception {
        Exception failure = null;
        for (String base : server.urls) {
            try { return requestServer(server, base, path); }
            catch (Exception e) { failure = e; }
        }
        if (failure != null) throw failure;
        return requestServer(server, null, path);
    }

    private JSONObject requestServer(Server server, String base, String path) throws Exception {
        if (base == null || !base.startsWith("https://") || !path.startsWith("/")
                || server.accessToken == null || server.accessToken.isEmpty())
            throw new IOException("Servidor Plex sin conexión HTTPS o credencial disponible.");
        return requestJson(new URL(base + path), server.accessToken, null, "GET");
    }

    private JSONObject requestJson(URL url, String token, JSONObject body, String method) throws Exception {
        String raw = new String(requestBytes(url, token, "application/json", body, method), StandardCharsets.UTF_8);
        try { return new JSONObject(raw); }
        catch (JSONException notObject) {
            JSONObject wrapped = new JSONObject();
            wrapped.put("resources", new JSONArray(raw));
            return wrapped;
        }
    }

    private byte[] requestBytes(URL url, String token, String accept) throws Exception {
        return requestBytes(url, token, accept, null, "GET");
    }

    private byte[] requestBytes(URL url, String token, String accept, JSONObject body, String method) throws Exception {
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Plex requiere una conexión HTTPS.");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(9000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("X-Plex-Product", PRODUCT);
        connection.setRequestProperty("X-Plex-Version", "2.3");
        connection.setRequestProperty("X-Plex-Client-Identifier", clientId());
        connection.setRequestProperty("X-Plex-Device", Build.MODEL == null ? "Android TV" : Build.MODEL);
        connection.setRequestProperty("X-Plex-Platform", "Android TV");
        connection.setRequestProperty("X-Plex-Platform-Version", Build.VERSION.RELEASE);
        if (token != null && !token.isEmpty()) connection.setRequestProperty("X-Plex-Token", token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                if (status == 301 || status == 302 || status == 307 || status == 308)
                    throw new IOException("Plex rechazó una redirección HTTPS.");
                throw new PlexHttpException(status);
            }
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 5 * 1024 * 1024) throw new IOException("Respuesta Plex demasiado grande.");
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } finally { connection.disconnect(); }
    }

    private List<Server> parseServers(JSONObject response) throws JSONException {
        JSONArray resources = response.optJSONArray("resources");
        if (resources == null && response.has("MediaContainer")) {
            JSONObject container = response.optJSONObject("MediaContainer");
            if (container != null) resources = container.optJSONArray("Device");
        }
        if (resources == null) return new ArrayList<>();
        List<Server> result = new ArrayList<>();
        for (int i = 0; i < resources.length(); i++) {
            JSONObject resource = resources.optJSONObject(i);
            if (resource == null || !resource.optString("provides", "").contains("server")) continue;
            String id = resource.optString("clientIdentifier");
            if (id.isEmpty()) id = resource.optString("machineIdentifier");
            if (id.isEmpty()) continue;
            List<JSONObject> connections = objects(resource, "connections");
            connections.sort((a, b) -> {
                int local = Boolean.compare(b.optBoolean("local"), a.optBoolean("local"));
                if (local != 0) return local;
                return Boolean.compare(a.optBoolean("relay"), b.optBoolean("relay"));
            });
            List<String> urls = new ArrayList<>();
            for (JSONObject connection : connections) {
                String uri = connection.optString("uri");
                if (!uri.startsWith("https://") || uri.contains("@")) continue;
                try {
                    URL parsed = new URL(uri);
                    if (parsed.getHost().isEmpty()) continue;
                    String normalized = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
                    if (!urls.contains(normalized)) urls.add(normalized);
                } catch (Exception ignored) {
                }
            }
            if (!urls.isEmpty()) {
                String accessToken = resource.optString("accessToken", "");
                if (accessToken.equals("null")) accessToken = "";
                result.add(new Server(id, resource.optString("name", "Plex"), accessToken, urls));
            }
        }
        return result;
    }

    private String serializeServers(List<Server> servers) throws JSONException {
        JSONArray values = new JSONArray();
        for (Server server : servers) values.put(server.toJson());
        return values.toString();
    }

    private void saveServerTokens(List<Server> servers, Set<String> selected) throws Exception {
        JSONObject tokens = new JSONObject();
        for (Server server : servers)
            if (selected.contains(server.id) && server.accessToken != null && !server.accessToken.isEmpty())
                tokens.put(server.id, server.accessToken);
        prefs.edit().putString("server_tokens", encryptText(tokens.toString())).apply();
        posterServerCache = null;
    }

    private static List<JSONObject> objects(JSONObject parent, String key) {
        List<JSONObject> result = new ArrayList<>();
        Object value = parent.opt(key);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) result.add(item);
            }
        } else if (value instanceof JSONObject) result.add((JSONObject) value);
        return result;
    }
}
