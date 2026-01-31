/*
 * FinalBoss Clan - Configuration
 * 
 * WHAT THIS DOES (for non-coders):
 * This file defines all the settings users can change in RuneLite's config panel.
 * Each setting becomes a UI element (toggle, dropdown, text field, etc.) automatically.
 * 
 * Settings are organized into sections for easier navigation.
 */

package com.finalboss.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Configuration interface for FinalBoss Clan plugin.
 * 
 * HOW THIS WORKS:
 * - RuneLite reads this interface and generates a settings UI
 * - Values are automatically saved/loaded
 * - Default values are specified in the default methods
 */
@ConfigGroup("finalbossclan")
public interface FinalBossConfig extends Config
{
    // =========================================================================
    // SECTIONS
    // =========================================================================
    
    @ConfigSection(
        name = "General",
        description = "General plugin settings",
        position = 0
    )
    String generalSection = "general";
    
    @ConfigSection(
        name = "Status",
        description = "Activity status settings",
        position = 1
    )
    String statusSection = "status";
    
    @ConfigSection(
        name = "Drops",
        description = "Drop logging settings",
        position = 2
    )
    String dropsSection = "drops";
    
    @ConfigSection(
        name = "Discord",
        description = "Discord integration settings",
        position = 3
    )
    String discordSection = "discord";
    
    @ConfigSection(
        name = "Advanced",
        description = "Advanced settings (usually don't need to change)",
        position = 99,
        closedByDefault = true
    )
    String advancedSection = "advanced";
    
    // =========================================================================
    // GENERAL SETTINGS
    // =========================================================================
    
    @ConfigItem(
        keyName = "showOfflineMembers",
        name = "Show Offline Members",
        description = "Show clan members who are offline (requires backend connection)",
        section = generalSection,
        position = 0
    )
    default boolean showOfflineMembers()
    {
        return false;
    }
    
    @ConfigItem(
        keyName = "compactMode",
        name = "Compact Mode",
        description = "Use a more compact layout for the member list",
        section = generalSection,
        position = 1
    )
    default boolean compactMode()
    {
        return false;
    }
    
    // =========================================================================
    // STATUS SETTINGS
    // =========================================================================
    
    @ConfigItem(
        keyName = "syncStatus",
        name = "Sync Status",
        description = "Share your activity status with other clan members",
        section = statusSection,
        position = 0
    )
    default boolean syncStatus()
    {
        return true;
    }
    
    @ConfigItem(
        keyName = "statusTimeout",
        name = "Status Timeout (minutes)",
        description = "How long before your status expires (0 = never)",
        section = statusSection,
        position = 1
    )
    default int statusTimeout()
    {
        return 30;
    }
    
    // =========================================================================
    // DROPS SETTINGS
    // =========================================================================
    
    @ConfigItem(
        keyName = "logDrops",
        name = "Log Drops",
        description = "Automatically log notable drops to the backend",
        section = dropsSection,
        position = 0
    )
    default boolean logDrops()
    {
        return true;
    }
    
    @ConfigItem(
        keyName = "dropThreshold",
        name = "Drop Value Threshold",
        description = "Minimum GP value to log a drop (0 = log all)",
        section = dropsSection,
        position = 1
    )
    default int dropThreshold()
    {
        return 1000000; // 1M default
    }
    
    @ConfigItem(
        keyName = "announceDrops",
        name = "Announce Drops to Discord",
        description = "Post notable drops to the Discord channel",
        section = dropsSection,
        position = 2
    )
    default boolean announceDrops()
    {
        return true;
    }
    
    // =========================================================================
    // DISCORD SETTINGS
    // =========================================================================
    
    @ConfigItem(
        keyName = "discordToken",
        name = "Session Token",
        description = "Your authentication token (set automatically after Discord login)",
        section = discordSection,
        position = 0,
        secret = true
    )
    default String discordToken()
    {
        return "";
    }
    
    @ConfigItem(
        keyName = "allowPings",
        name = "Allow Pings",
        description = "Allow other clan members to ping you via Discord DM",
        section = discordSection,
        position = 1
    )
    default boolean allowPings()
    {
        return true;
    }
    
    // =========================================================================
    // ADVANCED SETTINGS
    // =========================================================================
    
    @ConfigItem(
        keyName = "apiUrl",
        name = "API URL",
        description = "Backend API URL (leave default unless testing)",
        section = advancedSection,
        position = 0
    )
    default String apiUrl()
    {
        // TODO: Replace with actual Supabase URL after setup
        return "https://your-project.supabase.co";
    }
    
    @ConfigItem(
        keyName = "debugMode",
        name = "Debug Mode",
        description = "Enable verbose logging for troubleshooting",
        section = advancedSection,
        position = 1
    )
    default boolean debugMode()
    {
        return false;
    }
}
