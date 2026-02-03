package com.finalboss.runelite.services;

import com.finalboss.runelite.FinalBossConfig;
import com.finalboss.runelite.model.DropRecord;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;

import java.util.Collection;

/**
 * Processes loot drops and logs notable ones to the Supabase backend.
 *
 * This service listens to RuneLite's LootReceived events and:
 * 1. Filters drops based on the configured GP value threshold
 * 2. Calculates item values using GE prices (fallback to High Alchemy price)
 * 3. Sends notable drops to Supabase via ApiClient
 * 4. The Discord bot picks up new drops and announces them
 *
 * Drop Flow:
 *   LootReceived -> processLoot() -> processItem() -> ApiClient.logDrop() -> Supabase -> Discord Bot
 *
 * Configuration:
 * - logDrops: Master toggle for drop logging
 * - dropThreshold: Minimum GP value to log (default 1M GP)
 *
 * Requirements:
 * - Player must be verified as a WOM member
 * - API must be configured (apiUrl and apiKey set)
 */
@Slf4j
public class DropService
{
    // Fallback values when player name or item name cannot be determined
    private static final String UNKNOWN_RSN = "Unknown";
    private static final String UNKNOWN_ITEM = "Unknown Item";

    // ========== Dependencies ==========

    // RuneLite game client for player info
    private final Client client;

    // Item price and metadata lookup service
    private final ItemManager itemManager;

    // HTTP client for logging drops to Supabase
    private final ApiClient apiClient;

    // Plugin configuration (thresholds, toggles)
    private final FinalBossConfig config;

    public DropService(Client client, ItemManager itemManager, ApiClient apiClient, FinalBossConfig config)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.apiClient = apiClient;
        this.config = config;
    }

    // ========== Public API ==========

    /**
     * Process a loot event and log notable drops to the backend.
     *
     * Pre-conditions checked:
     * - Drop logging is enabled in config
     * - Player is verified as WOM member
     * - Event contains items
     *
     * Each item in the event is processed individually to support
     * multi-item drops (e.g., boss loot piles).
     *
     * @param event The loot received event from RuneLite's LootTracker
     */
    public void processLoot(LootReceived event)
    {
        // Null safety check
        if (event == null)
        {
            return;
        }

        // Check if logging is enabled and player is authorized
        if (!config.logDrops() || !apiClient.isVerified())
        {
            return;
        }

        // The "source" is typically the boss/activity name (e.g., "Vorkath", "Chambers of Xeric")
        String source = event.getName();
        Collection<ItemStack> items = event.getItems();

        if (items == null || items.isEmpty())
        {
            return;
        }

        // Process each item individually (supports multi-item loot drops)
        for (ItemStack item : items)
        {
            if (item != null)
            {
                processItem(item, source);
            }
        }
    }

    // ========== Private Methods ==========

    /**
     * Process a single item from a loot drop.
     *
     * Steps:
     * 1. Get item metadata (name, HA price) from ItemManager
     * 2. Calculate total value (prefer GE price, fallback to HA price)
     * 3. Check against configured threshold
     * 4. If above threshold, create DropRecord and send to backend
     *
     * @param item The item stack from the loot event
     * @param source The source/boss name where the drop occurred
     */
    private void processItem(ItemStack item, String source)
    {
        int itemId = item.getId();
        int quantity = item.getQuantity();

        // Skip invalid quantities (shouldn't happen, but defensive)
        if (quantity <= 0)
        {
            return;
        }

        // Get item metadata from RuneLite's cache
        ItemComposition itemComp = itemManager.getItemComposition(itemId);
        if (itemComp == null)
        {
            log.warn("Could not get item composition for item ID: {}", itemId);
            return;
        }

        String itemName = itemComp.getName();
        if (itemName == null || itemName.isEmpty())
        {
            itemName = UNKNOWN_ITEM;
        }

        // High Alchemy price (base game value)
        int haPrice = itemComp.getHaPrice();

        // Calculate total value: prefer Grand Exchange price, fallback to HA value
        // GE prices are fetched from RuneLite's price service
        long gePrice = (long) itemManager.getItemPrice(itemId) * quantity;
        long value = gePrice > 0 ? gePrice : (long) haPrice * quantity;

        // Check if drop meets the configured value threshold
        int threshold = config.dropThreshold();
        if (threshold > 0 && value < threshold)
        {
            log.debug("Drop below threshold: {} x{} = {} gp (threshold: {})",
                itemName, quantity, value, threshold);
            return;
        }

        // Build and submit the drop record
        String rsn = getPlayerName();

        log.info("Notable drop: {} x{} ({} gp) from {}", itemName, quantity, value, source);

        DropRecord drop = new DropRecord(rsn, itemId, itemName, quantity, value, source);

        // Async submission - fire and forget with logging callbacks
        apiClient.logDrop(drop)
            .thenAccept(success -> {
                if (success)
                {
                    log.debug("Drop logged successfully");
                }
                else
                {
                    log.warn("Failed to log drop");
                }
            })
            .exceptionally(e -> {
                log.error("Error logging drop: {}", e.getMessage());
                return null;
            });
    }

    /**
     * Get the current player's RSN.
     * @return Player RSN or "Unknown" if not available
     */
    private String getPlayerName()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null)
        {
            String name = localPlayer.getName();
            if (name != null && !name.isEmpty())
            {
                return name;
            }
        }
        return UNKNOWN_RSN;
    }
}
