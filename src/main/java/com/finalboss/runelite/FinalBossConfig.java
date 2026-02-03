package com.finalboss.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Configuration interface for the FinalBoss Clan plugin.
 *
 * This plugin comes PRE-CONFIGURED for FinalBoss clan members - no setup required!
 * Just install and it works out of the box.
 *
 * User-configurable settings:
 * - Status: Activity status sharing options (timeout, sync toggle)
 * - Drops: Drop logging thresholds and toggles
 *
 * Backend settings (WOM group ID, Supabase URL/key) are hardcoded constants
 * and not exposed in the UI. To fork this plugin for a different clan,
 * modify the constants in womGroupId(), apiUrl(), and apiKey().
 */
@ConfigGroup("finalbossclan")
public interface FinalBossConfig extends Config
{
    // ========== Configuration Sections ==========

    @ConfigSection(
        name = "Status",
        description = "Activity status settings",
        position = 0
    )
    String statusSection = "status";

    @ConfigSection(
        name = "Drops",
        description = "Drop logging settings",
        position = 1
    )
    String dropsSection = "drops";

    // ========== Status Settings ==========

    /**
     * Master toggle for status synchronization.
     * When enabled, your activity status is shared with other clan members.
     */
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

    /**
     * Time-to-live for status records in minutes.
     * After this duration, status auto-expires and is removed from the roster.
     * Set to 0 to disable expiration (status persists until manually changed).
     */
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

    // ========== Drop Logging Settings ==========

    /**
     * Master toggle for drop logging.
     * When enabled, notable drops are sent to the backend and announced in Discord.
     */
    @ConfigItem(
        keyName = "logDrops",
        name = "Log Drops",
        description = "Log notable drops to Discord",
        section = dropsSection,
        position = 0
    )
    default boolean logDrops()
    {
        return true;
    }

    /**
     * Minimum GP value required for a drop to be logged.
     * Drops below this threshold are silently ignored.
     * Default: 1,000,000 GP (1M) to capture only significant drops.
     * Set to 0 to log all drops regardless of value.
     */
    @ConfigItem(
        keyName = "dropThreshold",
        name = "Drop Value Threshold",
        description = "Minimum GP value to log a drop (0 = log all)",
        section = dropsSection,
        position = 1
    )
    default int dropThreshold()
    {
        return 1000000;
    }

    // ========== Hidden Backend Constants ==========
    // These are pre-configured for FinalBoss clan and hidden from the UI.
    // To fork for a different clan, modify these default values.

    /**
     * Wise Old Man group ID for membership verification.
     * FinalBoss clan's WOM group: https://wiseoldman.net/groups/1055
     *
     * To fork for a different clan, change this to your clan's WOM group ID.
     */
    @ConfigItem(
        keyName = "womGroupId",
        name = "WOM Group ID",
        description = "Wise Old Man group ID for membership verification",
        hidden = true
    )
    default int womGroupId()
    {
        return 1055;
    }

    /**
     * Supabase project URL for backend API communication.
     * FinalBoss clan's Supabase project URL.
     *
     * To fork for a different clan, change this to your Supabase project URL.
     */
    @ConfigItem(
        keyName = "apiUrl",
        name = "API URL",
        description = "Supabase project URL",
        hidden = true
    )
    default String apiUrl()
    {
        return "https://snpfnlvfxpfqryvzksrp.supabase.co";
    }

    /**
     * Supabase anonymous key for authenticating API requests.
     * This is the public "anon" key (safe to expose - RLS controls access).
     *
     * To fork for a different clan, change this to your Supabase anon key.
     */
    @ConfigItem(
        keyName = "apiKey",
        name = "API Key",
        description = "Supabase anon key",
        hidden = true
    )
    default String apiKey()
    {
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNucGZubHZmeHBmcXJ5dnprc3JwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk5MjQzNTEsImV4cCI6MjA4NTUwMDM1MX0.jxGTGTe__l_7Z-w-ZuuHAGcMyj_9FImRZrV_V5gd40M";
    }
}
