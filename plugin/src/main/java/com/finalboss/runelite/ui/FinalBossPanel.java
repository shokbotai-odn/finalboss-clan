/*
 * FinalBoss Clan - Main Panel
 * 
 * WHAT THIS DOES (for non-coders):
 * This is the sidebar panel that appears when you click the plugin icon.
 * It shows:
 * - Your current status selector at the top
 * - List of online clan members with their statuses
 * - Controls for authentication
 * 
 * STATUS SYNC:
 * - When you change your status, it's sent to the backend
 * - Other players' statuses are fetched periodically
 * - Status shows next to each member's name
 */

package com.finalboss.runelite.ui;

import com.finalboss.runelite.FinalBossPlugin;
import com.finalboss.runelite.model.StatusRecord;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main sidebar panel for the FinalBoss Clan plugin.
 */
@Slf4j
public class FinalBossPanel extends PluginPanel
{
    // =========================================================================
    // CONSTANTS
    // =========================================================================
    
    private static final Color HEADER_COLOR = new Color(30, 30, 30);
    private static final Color SECTION_COLOR = new Color(40, 40, 40);
    
    // Status colors for visual distinction
    private static final Map<String, Color> STATUS_COLORS = Map.of(
        "Available", new Color(67, 181, 129),    // Green
        "Bossing", new Color(250, 166, 26),      // Orange
        "TOB", new Color(237, 66, 69),           // Red
        "COX", new Color(88, 101, 242),          // Blurple
        "TOA", new Color(235, 69, 158),          // Pink
        "Skilling", new Color(87, 242, 135),     // Light green
        "AFK", new Color(153, 170, 181),         // Gray
        "Do Not Disturb", new Color(237, 66, 69) // Red
    );
    
    // =========================================================================
    // COMPONENTS
    // =========================================================================
    
    private final FinalBossPlugin plugin;
    
    private JLabel headerLabel;
    private JComboBox<String> statusSelector;
    private JLabel onlineCountLabel;
    private JPanel memberListPanel;
    private JButton loginButton;
    private JPanel authPanel;
    
    /** Current member list */
    private List<ClanChannelMember> currentMembers;
    
    /** Cached statuses from backend (RSN -> status) */
    private Map<String, String> memberStatuses = new HashMap<>();
    
    /** Scheduler for periodic status refresh */
    private ScheduledExecutorService statusRefresher;
    
    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================
    
    public FinalBossPanel(FinalBossPlugin plugin)
    {
        super(false);
        this.plugin = plugin;
        
        buildPanel();
        startStatusRefresh();
        
        log.debug("FinalBossPanel created");
    }
    
    // =========================================================================
    // PANEL CONSTRUCTION
    // =========================================================================
    
    private void buildPanel()
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // === NORTH: Header + Status ===
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setBackground(HEADER_COLOR);
        northPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header
        headerLabel = new JLabel("FinalBoss Clan");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        northPanel.add(headerLabel);
        
        northPanel.add(Box.createVerticalStrut(10));
        
        // Status selector
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(HEADER_COLOR);
        statusPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        JLabel statusLabel = new JLabel("Status: ");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        statusSelector = new JComboBox<>(new String[]{
            "Available",
            "Bossing",
            "TOB",
            "COX",
            "TOA",
            "Skilling",
            "AFK",
            "Do Not Disturb"
        });
        statusSelector.setEnabled(false); // Disabled until authenticated
        statusSelector.addActionListener(e -> onStatusChanged());
        statusPanel.add(statusSelector, BorderLayout.CENTER);
        
        northPanel.add(statusPanel);
        
        add(northPanel, BorderLayout.NORTH);
        
        // === CENTER: Member list ===
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        centerPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        
        // Section header
        JPanel sectionHeader = new JPanel(new BorderLayout());
        sectionHeader.setBackground(SECTION_COLOR);
        sectionHeader.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        onlineCountLabel = new JLabel("Online (0)");
        onlineCountLabel.setForeground(Color.WHITE);
        sectionHeader.add(onlineCountLabel, BorderLayout.WEST);
        
        // Refresh button
        JButton refreshBtn = new JButton("↻");
        refreshBtn.setToolTipText("Refresh statuses");
        refreshBtn.addActionListener(e -> refreshStatuses());
        sectionHeader.add(refreshBtn, BorderLayout.EAST);
        
        centerPanel.add(sectionHeader, BorderLayout.NORTH);
        
        // Member list (scrollable)
        memberListPanel = new JPanel();
        memberListPanel.setLayout(new BoxLayout(memberListPanel, BoxLayout.Y_AXIS));
        memberListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        JScrollPane scrollPane = new JScrollPane(memberListPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        add(centerPanel, BorderLayout.CENTER);
        
        // === SOUTH: Auth panel ===
        authPanel = new JPanel();
        authPanel.setLayout(new BoxLayout(authPanel, BoxLayout.Y_AXIS));
        authPanel.setBackground(HEADER_COLOR);
        authPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        loginButton = new JButton("Login with Discord");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.addActionListener(e -> onLoginClicked());
        authPanel.add(loginButton);
        
        add(authPanel, BorderLayout.SOUTH);
    }
    
    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================
    
    /**
     * Updates the member list display with statuses.
     */
    public void updateMembers(List<ClanChannelMember> members)
    {
        this.currentMembers = members;
        
        // Update count label
        onlineCountLabel.setText("Online (" + members.size() + ")");
        
        // Rebuild member list
        rebuildMemberList();
        
        log.debug("Updated member list with {} members", members.size());
    }
    
    /**
     * Updates the authentication status display.
     */
    public void updateAuthStatus(boolean authenticated)
    {
        if (authenticated)
        {
            loginButton.setText("✓ Verified");
            loginButton.setEnabled(false);
            statusSelector.setEnabled(true);
        }
        else
        {
            loginButton.setText("Login with Discord");
            loginButton.setEnabled(true);
            statusSelector.setEnabled(false);
        }
    }
    
    /**
     * Cleans up resources when panel is destroyed.
     */
    public void shutdown()
    {
        if (statusRefresher != null)
        {
            statusRefresher.shutdown();
        }
    }
    
    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================
    
    /**
     * Starts periodic status refresh from backend.
     */
    private void startStatusRefresh()
    {
        statusRefresher = Executors.newSingleThreadScheduledExecutor();
        statusRefresher.scheduleAtFixedRate(
            this::refreshStatuses,
            5,  // Initial delay
            30, // Refresh every 30 seconds
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Fetches latest statuses from backend.
     */
    private void refreshStatuses()
    {
        plugin.getApiClient().getStatuses()
            .thenAccept(statuses -> {
                // Build RSN -> status map
                Map<String, String> newStatuses = new HashMap<>();
                for (StatusRecord record : statuses)
                {
                    if (!record.isExpired())
                    {
                        newStatuses.put(record.getRsn().toLowerCase(), record.getStatus());
                    }
                }
                
                // Update on EDT
                SwingUtilities.invokeLater(() -> {
                    memberStatuses = newStatuses;
                    rebuildMemberList();
                });
            })
            .exceptionally(e -> {
                log.debug("Failed to refresh statuses: {}", e.getMessage());
                return null;
            });
    }
    
    /**
     * Rebuilds the member list with current statuses.
     */
    private void rebuildMemberList()
    {
        memberListPanel.removeAll();
        
        if (currentMembers == null || currentMembers.isEmpty())
        {
            JLabel placeholder = new JLabel("No clan members online");
            placeholder.setForeground(Color.GRAY);
            placeholder.setBorder(new EmptyBorder(20, 10, 20, 10));
            placeholder.setAlignmentX(Component.CENTER_ALIGNMENT);
            memberListPanel.add(placeholder);
        }
        else
        {
            for (ClanChannelMember member : currentMembers)
            {
                JPanel row = createMemberRow(member);
                memberListPanel.add(row);
            }
        }
        
        memberListPanel.revalidate();
        memberListPanel.repaint();
    }
    
    /**
     * Creates a row for a single clan member with status.
     */
    private JPanel createMemberRow(ClanChannelMember member)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        
        // Member name
        JLabel nameLabel = new JLabel(member.getName());
        nameLabel.setForeground(Color.WHITE);
        row.add(nameLabel, BorderLayout.WEST);
        
        // Status chip
        String status = memberStatuses.get(member.getName().toLowerCase());
        JLabel statusLabel;
        
        if (status != null)
        {
            statusLabel = new JLabel(status);
            Color statusColor = STATUS_COLORS.getOrDefault(status, Color.GRAY);
            statusLabel.setForeground(statusColor);
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 11f));
        }
        else
        {
            statusLabel = new JLabel("•");
            statusLabel.setForeground(Color.DARK_GRAY);
        }
        
        row.add(statusLabel, BorderLayout.EAST);
        
        // Wrapper for spacing
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.add(row, BorderLayout.CENTER);
        wrapper.add(Box.createVerticalStrut(2), BorderLayout.SOUTH);
        
        return wrapper;
    }
    
    /**
     * Called when the status selector changes.
     */
    private void onStatusChanged()
    {
        String selectedStatus = (String) statusSelector.getSelectedItem();
        if (selectedStatus == null) return;
        
        log.debug("Status changed to: {}", selectedStatus);
        
        // Send to backend
        int timeout = plugin.getConfig().statusTimeout();
        plugin.getApiClient().setStatus(selectedStatus, null, timeout)
            .thenAccept(success -> {
                if (success)
                {
                    log.info("Status updated to: {}", selectedStatus);
                }
                else
                {
                    log.warn("Failed to update status");
                }
            });
    }
    
    /**
     * Called when the login button is clicked.
     */
    private void onLoginClicked()
    {
        log.debug("Login button clicked");
        
        // Get the OAuth URL and open in browser
        String loginUrl = plugin.getApiClient().getDiscordLoginUrl();
        
        // Show verification instructions
        String code = plugin.startVerification();
        
        JOptionPane.showMessageDialog(
            this,
            "1. Click OK to open Discord login\n" +
            "2. Authorize the app\n" +
            "3. Type this code in clan chat: " + code + "\n\n" +
            "This verifies your RSN.",
            "Login with Discord",
            JOptionPane.INFORMATION_MESSAGE
        );
        
        // Open browser
        try
        {
            Desktop.getDesktop().browse(java.net.URI.create(loginUrl));
        }
        catch (Exception e)
        {
            log.error("Failed to open browser", e);
            JOptionPane.showMessageDialog(
                this,
                "Could not open browser.\nPlease go to: " + loginUrl,
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
