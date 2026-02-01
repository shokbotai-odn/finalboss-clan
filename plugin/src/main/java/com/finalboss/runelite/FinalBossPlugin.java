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
 * - Tracks drops and logs them to the database
 * 
 * PLUGIN HUB COMPLIANCE:
 * - No automation or gameplay actions
 * - Uses only official RuneLite APIs
 * - External connections clearly documented
 * - All data collection is opt-in via authentication
 */

package com.finalboss.runelite;

import com.finalboss.runelite.services.ApiClient;
import com.finalboss.runelite.services.ClanRosterService;
import com.finalboss.runelite.services.VerificationService;
import com.finalboss.runelite.ui.FinalBossPanel;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
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
    // =========================================================================
    
    @Inject
    private Client client;
    
    @Inject
    private ConfigManager configManager;
    
    @Inject
    private ClientToolbar clientToolbar;
    
    @Inject
    private ItemManager itemManager;
    
    @Inject
    @Getter
    private FinalBossConfig config;
    
    // =========================================================================
    // PLUGIN COMPONENTS
    // =========================================================================
    
    private FinalBossPanel panel;
    private NavigationButton navButton;
    
    @Getter
    private ClanRosterService clanRosterService;
    
    @Getter
    private ApiClient apiClient;
    
    @Getter
    private VerificationService verificationService;
    
    // =========================================================================
    // LIFECYCLE METHODS
    // =========================================================================
    
    @Override
    protected void startUp() throws Exception
    {
        log.info("FinalBoss Clan plugin starting...");
        
        // Initialize services
        clanRosterService = new ClanRosterService(client);
        apiClient = new ApiClient(config);
        verificationService = new VerificationService(this, config, apiClient);
        
        // Create the panel
        panel = new FinalBossPanel(this);
        
        // Load icon
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
    
    @Override
    protected void shutDown() throws Exception
    {
        log.info("FinalBoss Clan plugin stopping...");
        
        // Remove from toolbar
        clientToolbar.removeNavigation(navButton);
        
        // Cleanup API client
        if (apiClient != null)
        {
            apiClient.shutdown();
        }
        
        log.info("FinalBoss Clan plugin stopped");
    }
    
    @Provides
    FinalBossConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FinalBossConfig.class);
    }
    
    // =========================================================================
    // EVENT HANDLERS
    // =========================================================================
    
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
            log.debug("Player logged out, clearing roster");
            updatePanelMembers(Collections.emptyList());
        }
    }
    
    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event)
    {
        log.debug("Clan channel changed, refreshing roster");
        refreshClanRoster();
    }
    
    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event)
    {
        log.debug("Clan member joined: {}", event.getClanMember().getName());
        refreshClanRoster();
    }
    
    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event)
    {
        log.debug("Clan member left: {}", event.getClanMember().getName());
        refreshClanRoster();
    }
    
    /**
     * Called when a chat message is received.
     * Handles RSN verification via clan chat.
     */
    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Check for verification code in clan chat
        if (event.getType() == ChatMessageType.CLAN_CHAT 
            || event.getType() == ChatMessageType.CLAN_MESSAGE)
        {
            if (verificationService.onChatMessage(event))
            {
                log.info("RSN verification successful!");
                // Refresh panel to show verified status
                SwingUtilities.invokeLater(() -> panel.updateAuthStatus(true));
            }
        }
    }
    
    /**
     * Called when loot is received.
     * Logs notable drops to the backend.
     */
    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        // Only log if user has opted in and is authenticated
        if (!config.logDrops() || !apiClient.isAuthenticated())
        {
            return;
        }
        
        String source = event.getName();
        int threshold = config.dropThreshold();
        
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
                continue;
            }
            
            // Log the drop
            log.info("Notable drop: {} x{} = {}gp from {}", 
                itemName, quantity, totalValue, source);
            
            apiClient.logDrop(itemId, itemName, quantity, totalValue, source)
                .thenAccept(success -> {
                    if (success && config.announceDrops())
                    {
                        log.info("Drop logged and announced!");
                    }
                });
        }
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private void refreshClanRoster()
    {
        List<ClanChannelMember> members = clanRosterService.getMembers();
        updatePanelMembers(members);
    }
    
    private void updatePanelMembers(List<ClanChannelMember> members)
    {
        SwingUtilities.invokeLater(() -> {
            if (panel != null)
            {
                panel.updateMembers(members);
            }
        });
    }
    
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
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    public String getLocalPlayerName()
    {
        if (client.getLocalPlayer() != null)
        {
            return client.getLocalPlayer().getName();
        }
        return null;
    }
    
    public boolean isLoggedIn()
    {
        return client.getGameState() == GameState.LOGGED_IN;
    }
    
    /**
     * Starts the RSN verification process.
     * @return The verification code to display to user
     */
    public String startVerification()
    {
        return verificationService.startVerification();
    }
}
