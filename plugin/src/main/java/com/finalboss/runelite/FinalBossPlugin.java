package com.finalboss.runelite;

import com.finalboss.runelite.services.ApiClient;
import com.finalboss.runelite.services.ClanRosterService;
import com.finalboss.runelite.services.DropService;
import com.finalboss.runelite.ui.FinalBossPanel;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/**
 * FinalBoss Clan Plugin - Main entry point for the RuneLite plugin.
 *
 * This plugin provides clan management features for the Final Boss OSRS clan:
 * - Real-time clan member roster display
 * - Activity status sharing between clan members (via Supabase backend)
 * - Notable drop logging with Discord announcements
 * - Wise Old Man (WOM) group membership verification
 *
 * Architecture:
 * - ClanRosterService: Reads online clan members from RuneLite's clan channel API
 * - ApiClient: Handles HTTP communication with Supabase and WOM APIs
 * - DropService: Processes loot events and logs valuable drops
 * - FinalBossPanel: Sidebar UI displaying roster and status controls
 *
 * @see <a href="https://wiseoldman.net">Wise Old Man API</a>
 */
@Slf4j
@PluginDescriptor(
    name = "FinalBoss Clan",
    description = "Clan roster, status sync, and drops for Final Boss clan",
    tags = {"clan", "social", "utility", "drops"}
)
public class FinalBossPlugin extends Plugin
{
    // RuneLite game client - provides access to game state, player info, and clan data
    @Inject
    private Client client;

    // Toolbar for adding sidebar navigation buttons
    @Inject
    private ClientToolbar clientToolbar;

    // Provides item prices and metadata for drop value calculation
    @Inject
    private ItemManager itemManager;

    // Plugin configuration (API keys, thresholds, toggles)
    @Inject
    @Getter
    private FinalBossConfig config;

    // Sidebar panel UI component
    private FinalBossPanel panel;

    // Navigation button in the RuneLite sidebar
    private NavigationButton navButton;

    // Service for accessing current clan channel members
    @Getter
    private ClanRosterService clanRosterService;

    // HTTP client for Supabase backend and WOM API communication
    @Getter
    private ApiClient apiClient;

    // Processes loot events and logs notable drops
    private DropService dropService;

    /**
     * Called when the plugin is enabled. Initializes all services and UI components.
     * If already logged in, immediately refreshes the clan roster.
     */
    @Override
    protected void startUp() throws Exception
    {
        log.info("FinalBoss Clan plugin starting...");

        // Initialize core services
        clanRosterService = new ClanRosterService(client);
        apiClient = new ApiClient(config);
        dropService = new DropService(client, itemManager, apiClient, config);

        // Create the sidebar panel
        panel = new FinalBossPanel(this);

        // Load plugin icon and create navigation button
        final BufferedImage icon = loadIcon();

        navButton = NavigationButton.builder()
            .tooltip("FinalBoss Clan")
            .icon(icon)
            .priority(10)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);

        // If player is already logged in when plugin starts, populate the roster
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            refreshClanRoster();
        }

        log.info("FinalBoss Clan plugin started successfully");
    }

    /**
     * Called when the plugin is disabled. Cleans up resources and removes UI components.
     * Ensures HTTP clients and scheduled tasks are properly terminated.
     */
    @Override
    protected void shutDown() throws Exception
    {
        log.info("FinalBoss Clan plugin stopping...");

        // Remove sidebar navigation button
        clientToolbar.removeNavigation(navButton);

        // Stop panel's scheduled refresh task
        if (panel != null)
        {
            panel.shutdown();
        }

        // Shut down HTTP client dispatcher threads
        if (apiClient != null)
        {
            apiClient.shutdown();
        }

        log.info("FinalBoss Clan plugin stopped");
    }

    /**
     * Guice provider for plugin configuration.
     * Allows @Inject of FinalBossConfig in other classes.
     */
    @Provides
    FinalBossConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FinalBossConfig.class);
    }

    // ========== Event Handlers ==========

    /**
     * Handles game state transitions (login/logout).
     * On login: refreshes roster and verifies WOM membership.
     * On logout: clears roster and resets auth status.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            log.debug("Player logged in, refreshing clan roster");
            refreshClanRoster();
            // Check WOM membership to enable plugin features
            checkWomMembership();
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            log.debug("Player logged out, clearing roster");
            updatePanelMembers(Collections.emptyList());
            // Reset auth status on logout
            SwingUtilities.invokeLater(() -> {
                if (panel != null)
                {
                    panel.updateAuthStatus(false);
                }
            });
        }
    }

    /**
     * Check if the current player is a member of the WOM group.
     * If yes, enable plugin features automatically.
     * Uses a small delay to ensure player name is available.
     */
    private void checkWomMembership()
    {
        // Delay slightly to ensure player name is loaded
        new Thread(() -> {
            try
            {
                // Wait a moment for the player to fully load
                Thread.sleep(1000);
            }
            catch (InterruptedException ignored) {}

            String rsn = getLocalPlayerName();
            if (rsn == null)
            {
                log.warn("Could not get player name for WOM check");
                return;
            }

            log.debug("Checking WOM membership for RSN: '{}'", rsn);

            apiClient.isRsnAllowed(rsn)
                .thenAccept(isMember -> {
                    if (isMember)
                    {
                        // Store verified RSN for status updates
                        apiClient.setVerifiedRsn(rsn);
                        log.info("RSN '{}' verified as WOM member - plugin enabled", rsn);
                    }
                    else
                    {
                        apiClient.setVerifiedRsn(null);
                        log.warn("RSN '{}' NOT found in WOM group", rsn);
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (panel != null)
                        {
                            panel.updateAuthStatus(isMember);
                        }
                    });
                })
                .exceptionally(e -> {
                    log.error("Failed to check WOM membership", e);
                    return null;
                });
        }).start();
    }

    /**
     * Handles clan channel changes (joining/leaving a clan chat).
     * Refreshes the roster to reflect the new channel state.
     */
    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event)
    {
        log.debug("Clan channel changed, refreshing roster");
        refreshClanRoster();
    }

    /**
     * Handles a new member joining the clan channel.
     * Refreshes roster to include the new member.
     */
    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event)
    {
        log.debug("Clan member joined: {}", event.getClanMember().getName());
        refreshClanRoster();
    }

    /**
     * Handles a member leaving the clan channel.
     * Refreshes roster to remove the departed member.
     */
    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event)
    {
        log.debug("Clan member left: {}", event.getClanMember().getName());
        refreshClanRoster();
    }

    /**
     * Handles loot received events from RuneLite's LootTracker.
     * Delegates to DropService for filtering and logging.
     */
    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        dropService.processLoot(event);
    }

    // ========== Helper Methods ==========

    /**
     * Fetches current clan members and updates the panel display.
     */
    private void refreshClanRoster()
    {
        List<ClanChannelMember> members = clanRosterService.getMembers();
        updatePanelMembers(members);
    }

    /**
     * Updates the panel with the given member list on the EDT.
     * All Swing updates must happen on the Event Dispatch Thread.
     */
    private void updatePanelMembers(List<ClanChannelMember> members)
    {
        SwingUtilities.invokeLater(() -> {
            if (panel != null)
            {
                panel.updateMembers(members);
            }
        });
    }

    /**
     * Loads the plugin icon from resources, or returns a blank image on failure.
     * Icon is displayed in the RuneLite sidebar.
     */
    private BufferedImage loadIcon()
    {
        try
        {
            return ImageUtil.loadImageResource(getClass(), "/icon.png");
        }
        catch (Exception e)
        {
            log.warn("Could not load custom icon, using default");
            return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
    }

    // ========== Public Accessors ==========

    /**
     * Returns the local player's RSN, or null if not logged in.
     */
    public String getLocalPlayerName()
    {
        if (client.getLocalPlayer() != null)
        {
            return client.getLocalPlayer().getName();
        }
        return null;
    }

    /**
     * Returns true if the player is currently logged into the game.
     */
    public boolean isLoggedIn()
    {
        return client.getGameState() == GameState.LOGGED_IN;
    }

}
