/*
 * FinalBoss Clan - Drop Service
 * 
 * WHAT THIS DOES (for non-coders):
 * Watches for valuable drops and logs them to the backend.
 * Uses RuneLite's loot tracking events to detect drops.
 * Filters based on value threshold so we don't spam with junk drops.
 * 
 * HOW DROP DETECTION WORKS:
 * RuneLite fires LootReceived events when you get loot from:
 * - NPC kills (bosses, monsters)
 * - Raid chests (COX, TOB, TOA)
 * - Other sources (barrows, clues, etc.)
 * 
 * We check each item against the value threshold and log
 * qualifying drops to Supabase.
 */

package com.finalboss.runelite.services;

import com.finalboss.runelite.FinalBossConfig;
import com.finalboss.runelite.FinalBossPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.plugins.loottracker.LootReceived;

import javax.inject.Inject;
import java.util.Collection;

/**
 * Service for detecting and logging valuable drops.
 * 
 * Listens to RuneLite's loot tracking events and logs drops
 * that meet the value threshold to the backend.
 */
@Slf4j
public class DropService
{
    private final FinalBossPlugin plugin;
    private final FinalBossConfig config;
    private final ItemManager itemManager;
    private final ApiClient apiClient;
    
    /**
     * Creates a new DropService.
     * 
     * @param plugin The main plugin instance
     * @param config Plugin configuration
     * @param itemManager RuneLite's item manager for prices
     * @param apiClient API client for backend calls
     */
    @Inject
    public DropService(
        FinalBossPlugin plugin,
        FinalBossConfig config,
        ItemManager itemManager,
        ApiClient apiClient)
    {
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.apiClient = apiClient;
    }
    
    /**
     * Processes a loot received event.
     * 
     * Called by the plugin when LootReceived fires.
     * Checks each item against threshold and logs qualifying drops.
     * 
     * @param event The loot event from RuneLite
     */
    public void onLootReceived(LootReceived event)
    {
        // Only log if user has opted in
        if (!config.logDrops())
        {
            return;
        }
        
        // Only log if authenticated
        if (!apiClient.isAuthenticated())
        {
            log.debug("Skipping drop logging - not authenticated");
            return;
        }
        
        String source = event.getName();  // Boss/activity name
        int threshold = config.dropThreshold();
        
        // Check each item in the loot
        for (var item : event.getItems())
        {
            int itemId = item.getId();
            int quantity = item.getQuantity();
            
            // Get item info and price
            ItemComposition itemComp = itemManager.getItemComposition(itemId);
            String itemName = itemComp.getName();
            int gePrice = itemManager.getItemPrice(itemId);
            long totalValue = (long) gePrice * quantity;
            
            // Check threshold
            if (totalValue < threshold)
            {
                log.debug("Drop below threshold: {} x{} = {}gp (threshold: {})", 
                    itemName, quantity, totalValue, threshold);
                continue;
            }
            
            // Log the drop!
            log.info("Notable drop detected: {} x{} = {}gp from {}", 
                itemName, quantity, totalValue, source);
            
            apiClient.logDrop(itemId, itemName, quantity, totalValue, source)
                .thenAccept(success -> {
                    if (success)
                    {
                        log.debug("Drop logged successfully");
                    }
                    else
                    {
                        log.warn("Failed to log drop");
                    }
                });
        }
    }
    
    /**
     * Formats a GP value for display.
     * 
     * @param value The GP value
     * @return Formatted string (e.g., "1.5M", "500K")
     */
    public static String formatValue(long value)
    {
        if (value >= 1_000_000_000)
        {
            return String.format("%.2fB", value / 1_000_000_000.0);
        }
        else if (value >= 1_000_000)
        {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        else if (value >= 1_000)
        {
            return String.format("%.0fK", value / 1_000.0);
        }
        return String.valueOf(value) + "gp";
    }
}
