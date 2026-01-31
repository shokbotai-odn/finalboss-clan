/*
 * FinalBoss Clan - RuneLite Plugin
 * 
 * Main entry point for the Final Boss clan plugin.
 * 
 * WHAT THIS DOES (for non-coders):
 * This is like the "brain" of the plugin. It:
 * - Registers the sidebar panel when RuneLite loads
 * - Listens for game events (clan members joining/leaving, chat messages)
 * - Coordinates between the UI, backend, and Discord
 * 
 * PLUGIN HUB COMPLIANCE:
 * - No automation or gameplay actions
 * - Uses only official RuneLite APIs
 * - External connections clearly documented
 * - All data collection is opt-in via authentication
 */

package com.finalboss.runelite;

import com.finalboss.runelite.services.ClanRosterService;
import com.finalboss.runelite.ui.FinalBossPanel;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
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
 * FinalBoss Clan Plugin
 * 
 * A Plugin Hub compliant plugin for the Final Boss OSRS clan providing:
 * - Real-time clan roster display
 * - Activity status sharing
 * - Discord integration for auth and notifications
 * - Drop logging and session tracking
 * 
 * @author Final Boss Clan
 * @version 0.1.0
 */
@Slf4j
@PluginDescriptor(
    name = "FinalBoss Clan",
    description = "Clan roster, status sync, drops, and sessions for Final Boss clan",
    tags = {"clan", "social", "utility", "drops", "loot", "tracking"}
)
public class FinalBossPlugin extends Plugin
{
    // =========================================================================
    // INJECTED DEPENDENCIES
    // These are automatically provided by RuneLite's dependency injection
    // =========================================================================
    
    /**
     * The OSRS game client - our window into the game world.
     * Used to read clan channel, player info, etc.
     */
    @Inject
    private Client client;
    
    /**
     * Configuration manager - handles saving/loading user settings.
     */
    @Inject
    private ConfigManager configManager;
    
    /**
     * Client toolbar - the left sidebar where we add our panel button.
     */
    @Inject
    private ClientToolbar clientToolbar;
    
    /**
     * Our plugin configuration - user settings like API endpoints.
     */
    @Inject
    @Getter
    private FinalBossConfig config;
    
    // =========================================================================
    // PLUGIN COMPONENTS
    // =========================================================================
    
    /**
     * The sidebar panel that displays clan members, statuses, etc.
     */
    private FinalBossPanel panel;
    
    /**
     * Navigation button in the sidebar to open/close our panel.
     */
    private NavigationButton navButton;
    
    /**
     * Service that manages the clan roster.
     */
    @Getter
    private ClanRosterService clanRosterService;
    
    // =========================================================================
    // LIFECYCLE METHODS
    // =========================================================================
    
    /**
     * Called when the plugin starts up.
     * 
     * WHAT HAPPENS HERE:
     * 1. Create the sidebar panel
     * 2. Add our icon to the toolbar
     * 3. Initialize services
     * 4. Try to load clan roster if already logged in
     */
    @Override
    protected void startUp() throws Exception
    {
        log.info("FinalBoss Clan plugin starting...");
        
        // Initialize services
        clanRosterService = new ClanRosterService(client);
        
        // Create the panel
        panel = new FinalBossPanel(this);
        
        // Load icon (will use placeholder if not found)
        final BufferedImage icon = loadIcon();
        
        // Create navigation button for sidebar
        navButton = NavigationButton.builder()
            .tooltip("FinalBoss Clan")
            .icon(icon)
            .priority(10)
            .panel(panel)
            .build();
        
        // Add to toolbar
        clientToolbar.addNavigation(navButton);
        
        // If we're already logged in, refresh the roster
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            refreshClanRoster();
        }
        
        log.info("FinalBoss Clan plugin started successfully");
    }
    
    /**
     * Called when the plugin shuts down.
     * 
     * WHAT HAPPENS HERE:
     * - Remove our icon from the toolbar
     * - Clean up any resources
     */
    @Override
    protected void shutDown() throws Exception
    {
        log.info("FinalBoss Clan plugin stopping...");
        
        // Remove from toolbar
        clientToolbar.removeNavigation(navButton);
        
        log.info("FinalBoss Clan plugin stopped");
    }
    
    /**
     * Provides the config to the dependency injection system.
     */
    @Provides
    FinalBossConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FinalBossConfig.class);
    }
    
    // =========================================================================
    // EVENT HANDLERS
    // These methods are called automatically when game events occur
    // =========================================================================
    
    /**
     * Called when the game state changes (logging in, hopping worlds, etc.)
     * 
     * WHY WE CARE:
     * When the player logs in, we need to refresh the clan roster.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            log.debug("Player logged in, refreshing clan roster");
            refreshClanRoster();
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            // Clear roster when logged out
            log.debug("Player logged out, clearing roster");
            updatePanelMembers(Collections.emptyList());
        }
    }
    
    /**
     * Called when the clan channel changes (joining/leaving a clan chat).
     * 
     * WHY WE CARE:
     * The clan roster needs to be refreshed when the channel changes.
     */
    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event)
    {
        log.debug("Clan channel changed, refreshing roster");
        refreshClanRoster();
    }
    
    /**
     * Called when a member joins the clan channel.
     * 
     * WHY WE CARE:
     * We need to add them to our roster display.
     */
    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event)
    {
        log.debug("Clan member joined: {}", event.getClanMember().getName());
        refreshClanRoster();
    }
    
    /**
     * Called when a member leaves the clan channel.
     * 
     * WHY WE CARE:
     * We need to remove them from our roster display.
     */
    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event)
    {
        log.debug("Clan member left: {}", event.getClanMember().getName());
        refreshClanRoster();
    }
    
    /**
     * Called when a chat message is received.
     * 
     * WHY WE CARE:
     * - RSN verification codes are typed in clan chat
     * - Drop messages can be detected here (future feature)
     */
    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Only care about clan chat for verification
        if (event.getType() != ChatMessageType.CLAN_CHAT 
            && event.getType() != ChatMessageType.CLAN_MESSAGE)
        {
            return;
        }
        
        // TODO: Implement verification code detection
        // TODO: Implement drop message detection
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Refreshes the clan roster from the game client.
     * 
     * HOW IT WORKS:
     * 1. Get the current clan channel from the client
     * 2. Extract the member list
     * 3. Update the panel on the Swing thread
     */
    private void refreshClanRoster()
    {
        List<ClanChannelMember> members = clanRosterService.getMembers();
        updatePanelMembers(members);
    }
    
    /**
     * Updates the panel with the new member list.
     * Must be called on the Swing EDT (Event Dispatch Thread).
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
     * Loads the plugin icon from resources.
     * Returns a default icon if the custom icon isn't found.
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
            // Return a simple placeholder (will be replaced with actual icon later)
            return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
    }
    
    // =========================================================================
    // PUBLIC API
    // Methods that other components (panel, services) can call
    // =========================================================================
    
    /**
     * Gets the local player's name if logged in.
     * 
     * @return The player's RSN, or null if not logged in
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
     * Checks if the player is currently logged into the game.
     */
    public boolean isLoggedIn()
    {
        return client.getGameState() == GameState.LOGGED_IN;
    }
}
