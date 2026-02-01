/*
 * FinalBoss Clan - API Client
 * 
 * WHAT THIS DOES (for non-coders):
 * This is how the plugin talks to the Supabase backend.
 * It handles all HTTP requests - sending data (like status updates)
 * and receiving data (like other players' statuses).
 * 
 * Think of it like a messenger that carries notes between the plugin
 * and the server, making sure they're formatted correctly.
 */

package com.finalboss.runelite.services;

import com.finalboss.runelite.FinalBossConfig;
import com.finalboss.runelite.model.StatusRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for communicating with the Supabase backend.
 * 
 * All methods are async (return CompletableFuture) so they don't
 * block the game thread.
 */
@Slf4j
public class ApiClient
{
    // =========================================================================
    // CONSTANTS
    // =========================================================================
    
    private static final String SUPABASE_URL = "https://snpfnlvfxpfqryvzksrp.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNucGZubHZmeHBmcXJ5dnprc3JwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk5MjQzNTEsImV4cCI6MjA4NTUwMDM1MX0.jxGTGTe__l_7Z-w-ZuuHAGcMyj_9FImRZrV_V5gd40M";
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    // =========================================================================
    // FIELDS
    // =========================================================================
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final FinalBossConfig config;
    
    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================
    
    /**
     * Creates a new API client.
     * 
     * @param config Plugin configuration (for auth token, etc.)
     */
    public ApiClient(FinalBossConfig config)
    {
        this.config = config;
        
        // Configure HTTP client with reasonable timeouts
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        
        // Configure Gson for JSON parsing
        this.gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create();
        
        log.debug("ApiClient initialized for {}", SUPABASE_URL);
    }
    
    // =========================================================================
    // AUTHENTICATION
    // =========================================================================
    
    /**
     * Gets the Discord OAuth2 login URL.
     * User should be redirected to this URL to authenticate.
     * 
     * @return The OAuth2 authorization URL
     */
    public String getDiscordLoginUrl()
    {
        return SUPABASE_URL + "/auth/v1/authorize?provider=discord";
    }
    
    /**
     * Checks if the user is currently authenticated.
     * 
     * @return true if we have a valid session token
     */
    public boolean isAuthenticated()
    {
        String token = config.discordToken();
        return token != null && !token.isEmpty();
    }
    
    // =========================================================================
    // STATUS ENDPOINTS
    // =========================================================================
    
    /**
     * Gets all current statuses.
     * 
     * HOW IT WORKS:
     * 1. Makes GET request to /rest/v1/statuses
     * 2. Supabase returns JSON array
     * 3. We parse it into StatusRecord objects
     * 
     * @return Future containing list of statuses
     */
    public CompletableFuture<List<StatusRecord>> getStatuses()
    {
        Request request = new Request.Builder()
            .url(SUPABASE_URL + "/rest/v1/statuses?select=*,users(rsn)")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Authorization", "Bearer " + ANON_KEY)
            .get()
            .build();
        
        return executeAsync(request).thenApply(json -> {
            if (json == null || json.isEmpty())
            {
                return Collections.emptyList();
            }
            
            Type listType = new TypeToken<List<StatusRecord>>(){}.getType();
            return gson.fromJson(json, listType);
        });
    }
    
    /**
     * Updates the current user's status.
     * 
     * @param status The status to set (e.g., "TOB", "Bossing", "AFK")
     * @param note Optional note (e.g., "need 1 more")
     * @param ttlMinutes How long until status expires (0 = never)
     * @return Future that completes when update is done
     */
    public CompletableFuture<Boolean> setStatus(String status, String note, int ttlMinutes)
    {
        if (!isAuthenticated())
        {
            log.warn("Cannot set status: not authenticated");
            return CompletableFuture.completedFuture(false);
        }
        
        // Build the status payload
        String json = gson.toJson(new StatusPayload(status, note, ttlMinutes));
        
        Request request = new Request.Builder()
            .url(SUPABASE_URL + "/rest/v1/statuses")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Authorization", "Bearer " + config.discordToken())
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")  // Upsert
            .post(RequestBody.create(json, JSON))
            .build();
        
        return executeAsync(request).thenApply(response -> {
            log.debug("Status update response: {}", response);
            return true;
        }).exceptionally(e -> {
            log.error("Failed to set status", e);
            return false;
        });
    }
    
    // =========================================================================
    // DROP ENDPOINTS
    // =========================================================================
    
    /**
     * Logs a drop to the backend.
     * 
     * @param itemId The OSRS item ID
     * @param itemName The item name
     * @param quantity How many
     * @param value GP value
     * @param source Where it dropped (e.g., "Zulrah")
     * @return Future that completes when logged
     */
    public CompletableFuture<Boolean> logDrop(int itemId, String itemName, int quantity, long value, String source)
    {
        if (!isAuthenticated())
        {
            log.warn("Cannot log drop: not authenticated");
            return CompletableFuture.completedFuture(false);
        }
        
        String json = gson.toJson(new DropPayload(itemId, itemName, quantity, value, source));
        
        Request request = new Request.Builder()
            .url(SUPABASE_URL + "/rest/v1/drops")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Authorization", "Bearer " + config.discordToken())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(json, JSON))
            .build();
        
        return executeAsync(request).thenApply(response -> {
            log.info("Drop logged: {} x{} from {}", itemName, quantity, source);
            return true;
        }).exceptionally(e -> {
            log.error("Failed to log drop", e);
            return false;
        });
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Executes an HTTP request asynchronously.
     * 
     * @param request The request to execute
     * @return Future containing the response body as string
     */
    private CompletableFuture<String> executeAsync(Request request)
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.error("HTTP request failed: {}", e.getMessage());
                future.completeExceptionally(e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try (ResponseBody body = response.body())
                {
                    if (!response.isSuccessful())
                    {
                        String error = body != null ? body.string() : "Unknown error";
                        log.error("HTTP {} {}: {}", response.code(), response.message(), error);
                        future.completeExceptionally(new IOException("HTTP " + response.code()));
                        return;
                    }
                    
                    String responseBody = body != null ? body.string() : "";
                    future.complete(responseBody);
                }
            }
        });
        
        return future;
    }
    
    /**
     * Cleans up resources when plugin shuts down.
     */
    public void shutdown()
    {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
    
    // =========================================================================
    // PAYLOAD CLASSES
    // These are just data containers for JSON serialization
    // =========================================================================
    
    private static class StatusPayload
    {
        final String status;
        final String note;
        final String expires_at;
        
        StatusPayload(String status, String note, int ttlMinutes)
        {
            this.status = status;
            this.note = note;
            
            if (ttlMinutes > 0)
            {
                // Calculate expiration time
                long expiresMs = System.currentTimeMillis() + (ttlMinutes * 60 * 1000L);
                this.expires_at = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .format(new java.util.Date(expiresMs));
            }
            else
            {
                this.expires_at = null;
            }
        }
    }
    
    private static class DropPayload
    {
        final int item_id;
        final String item_name;
        final int quantity;
        final long value;
        final String source;
        
        DropPayload(int itemId, String itemName, int quantity, long value, String source)
        {
            this.item_id = itemId;
            this.item_name = itemName;
            this.quantity = quantity;
            this.value = value;
            this.source = source;
        }
    }
}
