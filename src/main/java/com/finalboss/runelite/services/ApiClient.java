package com.finalboss.runelite.services;

import com.finalboss.runelite.FinalBossConfig;
import com.finalboss.runelite.model.DropRecord;
import com.finalboss.runelite.model.StatusRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP client for communicating with Supabase backend and Wise Old Man API.
 *
 * This client provides:
 * - WOM (Wise Old Man) membership verification with 5-minute caching
 * - Status updates to Supabase for clan activity sharing
 * - Drop logging to Supabase for Discord announcements
 * - RSN normalization to handle OSRS name variations
 *
 * Thread Safety:
 * - All public methods are thread-safe
 * - Uses AtomicReference and volatile for cache management
 * - HTTP calls are async using CompletableFuture
 *
 * Error Handling:
 * - Network failures return empty collections or false
 * - Cached data is used as fallback when WOM API fails
 * - All errors are logged with context
 *
 * @see <a href="https://api.wiseoldman.net/v2/docs">WOM API Documentation</a>
 * @see <a href="https://supabase.com/docs/reference/javascript/select">Supabase REST API</a>
 */
@Slf4j
public class ApiClient
{
    // ========== Constants ==========

    // Wise Old Man API base URL (v2)
    private static final String WOM_API_URL = "https://api.wiseoldman.net/v2";

    // JSON content type for HTTP requests
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    // HTTP timeout configuration (seconds)
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final int WRITE_TIMEOUT_SECONDS = 30;

    // WOM member cache duration (5 minutes to reduce API calls)
    private static final long CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(5);

    // ========== Thread-Safe State ==========

    // Cached list of WOM group member RSNs (AtomicReference for lock-free reads)
    private final AtomicReference<List<String>> cachedWomMembers = new AtomicReference<>();

    // Cache expiration timestamp (volatile for visibility across threads)
    private volatile long cacheExpiry = 0;

    // Lock object for synchronized cache updates
    private final Object cacheLock = new Object();

    // The player's RSN after successful WOM verification (null if not verified)
    private volatile String verifiedRsn = null;

    // ========== Dependencies ==========

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final FinalBossConfig config;

    // ========== Constructor ==========

    /**
     * Creates a new ApiClient with the given configuration.
     * Initializes the HTTP client with configured timeouts and a Gson instance
     * for JSON serialization with ISO 8601 date format.
     */
    public ApiClient(FinalBossConfig config)
    {
        this.config = config;

        // Configure OkHttp client with generous timeouts for slow connections
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

        // Configure Gson with ISO 8601 date format for Supabase compatibility
        this.gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create();

        log.debug("ApiClient initialized");
    }

    // ========== Configuration & Verification ==========

    /**
     * Check if the Supabase API is configured with URL and key.
     * Required before making any backend API calls.
     */
    public boolean isConfigured()
    {
        String url = config.apiUrl();
        String key = config.apiKey();
        return url != null && !url.isEmpty() && key != null && !key.isEmpty();
    }

    private String getApiUrl()
    {
        return config.apiUrl();
    }

    private String getApiKey()
    {
        return config.apiKey();
    }

    /**
     * Set the verified RSN after WOM membership check passes.
     * Thread-safe.
     */
    public void setVerifiedRsn(String rsn)
    {
        this.verifiedRsn = rsn;
        log.debug("Verified RSN set to: {}", rsn != null ? rsn : "(null)");
    }

    /**
     * Check if the current user is verified as a WOM member.
     */
    public boolean isVerified()
    {
        return verifiedRsn != null && !verifiedRsn.isEmpty();
    }

    /**
     * Get the verified RSN.
     */
    public String getVerifiedRsn()
    {
        return verifiedRsn;
    }

    // ========== Supabase API Methods ==========

    /**
     * Fetch all clan member statuses from the Supabase backend.
     * Returns an empty list if the API is not configured or on error.
     *
     * @return CompletableFuture containing list of StatusRecord objects
     */
    public CompletableFuture<List<StatusRecord>> getStatuses()
    {
        if (!isConfigured())
        {
            log.debug("API not configured, returning empty statuses");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        Request request = new Request.Builder()
            .url(getApiUrl() + "/rest/v1/statuses?select=*")
            .addHeader("apikey", getApiKey())
            .addHeader("Authorization", "Bearer " + getApiKey())
            .get()
            .build();

        return executeAsync(request).thenApply(json -> {
            if (json == null || json.isEmpty() || json.equals("[]"))
            {
                return Collections.<StatusRecord>emptyList();
            }

            try
            {
                Type listType = new TypeToken<List<StatusRecord>>(){}.getType();
                List<StatusRecord> result = gson.fromJson(json, listType);
                return result != null ? result : Collections.<StatusRecord>emptyList();
            }
            catch (Exception e)
            {
                log.error("Failed to parse statuses response", e);
                return Collections.<StatusRecord>emptyList();
            }
        }).exceptionally(e -> {
            log.error("Failed to fetch statuses", e);
            return Collections.emptyList();
        });
    }

    // ========== Wise Old Man API Methods ==========

    /**
     * Normalize an RSN for comparison.
     * RuneScape treats spaces, underscores, hyphens, and non-breaking spaces as equivalent.
     * Example: "Iron Man" == "Iron_Man" == "Iron-Man" == "iron man"
     *
     * @param rsn The RuneScape name to normalize
     * @return Lowercase RSN with all separators converted to spaces
     */
    private String normalizeRsn(String rsn)
    {
        if (rsn == null)
        {
            return "";
        }
        return rsn.replace('\u00A0', ' ')
                  .replace('_', ' ')
                  .replace('-', ' ')
                  .toLowerCase()
                  .trim();
    }

    /**
     * Check if an RSN is a member of the Wise Old Man group.
     * Uses a 5-minute cached member list to minimize API calls.
     *
     * Flow:
     * 1. Check if cached member list is still valid
     * 2. If valid, check membership against cache
     * 3. If expired, fetch fresh list from WOM API
     *
     * Thread-safe: uses atomic reference for cache reads.
     *
     * @param rsn The RuneScape name to check
     * @return CompletableFuture<Boolean> true if member, false otherwise
     */
    public CompletableFuture<Boolean> isRsnAllowed(String rsn)
    {
        if (rsn == null || rsn.isEmpty())
        {
            log.warn("isRsnAllowed called with null/empty RSN");
            return CompletableFuture.completedFuture(false);
        }

        String normalizedRsn = normalizeRsn(rsn);
        log.debug("Checking RSN '{}' (normalized: '{}')", rsn, normalizedRsn);

        // Check cache first (thread-safe read via AtomicReference)
        List<String> cached = cachedWomMembers.get();
        if (cached != null && System.currentTimeMillis() < cacheExpiry)
        {
            boolean isMember = cached.stream()
                .anyMatch(member -> normalizeRsn(member).equals(normalizedRsn));
            log.debug("RSN '{}' WOM membership (cached): {}", rsn, isMember);
            return CompletableFuture.completedFuture(isMember);
        }

        // Cache expired or empty - fetch fresh member list from WOM
        log.debug("Cache miss - fetching WOM members from API");
        return fetchWomMembers().thenApply(members -> {
            boolean isMember = members.stream()
                .anyMatch(member -> normalizeRsn(member).equals(normalizedRsn));

            log.debug("RSN '{}' WOM membership result: {}", rsn, isMember);
            return isMember;
        });
    }

    /**
     * Fetch all members from the Wise Old Man group.
     * Updates the thread-safe cache on success.
     */
    private CompletableFuture<List<String>> fetchWomMembers()
    {
        int groupId = config.womGroupId();
        String url = WOM_API_URL + "/groups/" + groupId;

        Request request = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "FinalBoss-RuneLite-Plugin")
            .get()
            .build();

        return executeAsync(request).thenApply(json -> {
            List<String> members = parseWomMembers(json);

            // Update cache (thread-safe)
            synchronized (cacheLock)
            {
                cachedWomMembers.set(members);
                cacheExpiry = System.currentTimeMillis() + CACHE_DURATION_MS;
            }

            log.info("Fetched {} members from WOM group {}", members.size(), groupId);
            return members;
        }).exceptionally(e -> {
            log.error("Failed to fetch WOM members: {}", e.getMessage());
            // Return cached data if available, otherwise empty
            List<String> cached = cachedWomMembers.get();
            return cached != null ? cached : Collections.emptyList();
        });
    }

    /**
     * Parse the WOM API response to extract member RSNs.
     */
    private List<String> parseWomMembers(String json)
    {
        if (json == null || json.isEmpty())
        {
            log.warn("Empty response from WOM API");
            return Collections.emptyList();
        }

        try
        {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null)
            {
                log.warn("Failed to parse WOM response as JSON object");
                return Collections.emptyList();
            }

            JsonArray memberships = root.getAsJsonArray("memberships");
            if (memberships == null)
            {
                log.warn("No memberships array in WOM response");
                return Collections.emptyList();
            }

            List<String> members = new ArrayList<>();
            for (JsonElement element : memberships)
            {
                JsonObject membership = element.getAsJsonObject();
                JsonObject player = membership.getAsJsonObject("player");
                if (player != null && player.has("displayName"))
                {
                    members.add(player.get("displayName").getAsString());
                }
            }
            return members;
        }
        catch (Exception e)
        {
            log.error("Failed to parse WOM response", e);
            return Collections.emptyList();
        }
    }

    /**
     * Force refresh the WOM member cache.
     */
    public CompletableFuture<Integer> refreshWomCache()
    {
        synchronized (cacheLock)
        {
            cacheExpiry = 0;
        }
        return fetchWomMembers().thenApply(List::size);
    }

    // ========== Status & Drop API Methods ==========

    /**
     * Update the player's activity status in the Supabase backend.
     * Uses upsert (insert or update) based on RSN as the unique key.
     *
     * @param status The activity status (e.g., "Available", "Bossing", "TOB")
     * @param note Optional note/details about the activity
     * @param ttlMinutes Minutes until status expires (0 = never expires)
     * @return CompletableFuture<Boolean> true on success, false on failure
     */
    public CompletableFuture<Boolean> setStatus(String status, String note, int ttlMinutes)
    {
        if (!isVerified())
        {
            log.warn("Cannot set status: not verified as WOM member");
            return CompletableFuture.completedFuture(false);
        }

        if (!isConfigured())
        {
            log.warn("Cannot set status: API not configured");
            return CompletableFuture.completedFuture(false);
        }

        String rsn = verifiedRsn;
        log.info("Setting status for RSN '{}': status={}, ttl={}", rsn, status, ttlMinutes);

        String jsonPayload = gson.toJson(new StatusPayload(rsn, status, note, ttlMinutes));

        Request request = new Request.Builder()
            .url(getApiUrl() + "/rest/v1/statuses?on_conflict=rsn")
            .addHeader("apikey", getApiKey())
            .addHeader("Authorization", "Bearer " + getApiKey())
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(RequestBody.create(jsonPayload, JSON_MEDIA_TYPE))
            .build();

        return executeAsync(request).thenApply(response -> {
            log.info("Status update successful");
            return true;
        }).exceptionally(e -> {
            log.error("Failed to set status: {}", e.getMessage());
            return false;
        });
    }

    /**
     * Log a notable drop to the Supabase backend for Discord announcement.
     * The Discord bot listens to real-time changes on the drops table
     * and posts an embed message for each new record.
     *
     * @param drop The drop record containing item info, value, and source
     * @return CompletableFuture<Boolean> true on success, false on failure
     */
    public CompletableFuture<Boolean> logDrop(DropRecord drop)
    {
        if (!isVerified())
        {
            log.warn("Cannot log drop: not verified as WOM member");
            return CompletableFuture.completedFuture(false);
        }

        if (!isConfigured())
        {
            log.warn("Cannot log drop: API not configured");
            return CompletableFuture.completedFuture(false);
        }

        if (drop == null)
        {
            log.warn("Cannot log drop: drop is null");
            return CompletableFuture.completedFuture(false);
        }

        log.info("Logging drop: {} x{} ({} gp) from {}",
            drop.getItem_name(), drop.getQuantity(), drop.getValue(), drop.getSource());

        String jsonPayload = gson.toJson(drop);

        Request request = new Request.Builder()
            .url(getApiUrl() + "/rest/v1/drops")
            .addHeader("apikey", getApiKey())
            .addHeader("Authorization", "Bearer " + getApiKey())
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .post(RequestBody.create(jsonPayload, JSON_MEDIA_TYPE))
            .build();

        return executeAsync(request).thenApply(response -> {
            log.info("Drop logged successfully");
            return true;
        }).exceptionally(e -> {
            log.error("Failed to log drop: {}", e.getMessage());
            return false;
        });
    }

    // ========== HTTP Infrastructure ==========

    /**
     * Execute an HTTP request asynchronously using OkHttp's enqueue mechanism.
     * Returns the response body as a string on success.
     * Completes exceptionally on network errors or non-2xx status codes.
     *
     * @param request The OkHttp Request to execute
     * @return CompletableFuture<String> containing the response body
     */
    private CompletableFuture<String> executeAsync(Request request)
    {
        CompletableFuture<String> future = new CompletableFuture<>();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.error("HTTP request failed to {}: {}", call.request().url().host(), e.getMessage());
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (ResponseBody body = response.body())
                {
                    String responseBody = body != null ? body.string() : "";

                    if (!response.isSuccessful())
                    {
                        log.error("HTTP {} {} for {}: {}",
                            response.code(),
                            response.message(),
                            call.request().url(),
                            responseBody);
                        future.completeExceptionally(new IOException(
                            "HTTP " + response.code() + ": " + responseBody));
                        return;
                    }

                    future.complete(responseBody);
                }
                catch (IOException e)
                {
                    log.error("Failed to read response body", e);
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    /**
     * Shutdown the HTTP client and release resources.
     * Should be called when the plugin is disabled.
     */
    public void shutdown()
    {
        try
        {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
            if (httpClient.cache() != null)
            {
                httpClient.cache().close();
            }
        }
        catch (Exception e)
        {
            log.warn("Error during ApiClient shutdown", e);
        }
    }

    // ========== Internal Data Classes ==========

    /**
     * JSON payload for status update requests to Supabase.
     * Calculates expires_at timestamp based on TTL at construction time.
     */
    private static class StatusPayload
    {
        final String rsn;
        final String status;
        final String note;
        final String expires_at;

        StatusPayload(String rsn, String status, String note, int ttlMinutes)
        {
            this.rsn = rsn;
            this.status = status;
            this.note = note;

            if (ttlMinutes > 0)
            {
                Instant expiry = Instant.now().plusSeconds(ttlMinutes * 60L);
                this.expires_at = expiry.toString();
            }
            else
            {
                this.expires_at = null;
            }
        }
    }
}
