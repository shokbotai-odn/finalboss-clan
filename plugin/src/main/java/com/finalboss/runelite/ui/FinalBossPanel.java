/*
 * FinalBoss Clan - Main Panel
 * 
 * WHAT THIS DOES (for non-coders):
 * This is the sidebar panel that appears when you click the plugin icon.
 * It shows:
 * - Your current status selector at the top
 * - List of online clan members with their statuses
 * - Controls for sessions, drops, etc.
 * 
 * SWING BASICS:
 * - JPanel = a container for other components
 * - JLabel = text display
 * - JButton = clickable button
 * - JScrollPane = makes content scrollable
 * - BorderLayout, BoxLayout = ways to arrange components
 */

package com.finalboss.runelite.ui;

import com.finalboss.runelite.FinalBossPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Main sidebar panel for the FinalBoss Clan plugin.
 * 
 * Layout:
 * ┌────────────────────────┐
 * │ FinalBoss Clan         │ <- Header
 * ├────────────────────────┤
 * │ [Status: Bossing ▼]    │ <- Status selector
 * ├────────────────────────┤
 * │ Online (12)            │ <- Section header
 * │ ┌──────────────────┐   │
 * │ │ Player1 - TOB    │   │ <- Member rows
 * │ │ Player2 - AFK    │   │    (scrollable)
 * │ │ Player3 - Bossing│   │
 * │ └──────────────────┘   │
 * ├────────────────────────┤
 * │ [Login with Discord]   │ <- Auth button (if not logged in)
 * └────────────────────────┘
 */
@Slf4j
public class FinalBossPanel extends PluginPanel
{
    // =========================================================================
    // CONSTANTS
    // =========================================================================
    
    private static final Color HEADER_COLOR = new Color(30, 30, 30);
    private static final Color SECTION_COLOR = new Color(40, 40, 40);
    
    // =========================================================================
    // COMPONENTS
    // =========================================================================
    
    private final FinalBossPlugin plugin;
    
    /** Header showing plugin name */
    private JLabel headerLabel;
    
    /** Status dropdown for the local player */
    private JComboBox<String> statusSelector;
    
    /** Label showing "Online (X)" */
    private JLabel onlineCountLabel;
    
    /** Panel containing member rows */
    private JPanel memberListPanel;
    
    /** Login button (shown when not authenticated) */
    private JButton loginButton;
    
    /** Panel for auth status */
    private JPanel authPanel;
    
    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================
    
    /**
     * Creates the main panel.
     * 
     * @param plugin Reference to the main plugin (for accessing config, services)
     */
    public FinalBossPanel(FinalBossPlugin plugin)
    {
        super(false); // false = don't wrap in scroll pane (we handle it ourselves)
        this.plugin = plugin;
        
        buildPanel();
        
        log.debug("FinalBossPanel created");
    }
    
    // =========================================================================
    // PANEL CONSTRUCTION
    // =========================================================================
    
    /**
     * Builds the complete panel layout.
     */
    private void buildPanel()
    {
        // Use BorderLayout for main structure
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
     * Updates the member list display.
     * 
     * MUST be called on the Swing EDT (Event Dispatch Thread).
     * The plugin handles this via SwingUtilities.invokeLater().
     * 
     * @param members List of clan members to display
     */
    public void updateMembers(List<ClanChannelMember> members)
    {
        // Update count label
        onlineCountLabel.setText("Online (" + members.size() + ")");
        
        // Clear existing rows
        memberListPanel.removeAll();
        
        if (members.isEmpty())
        {
            // Show placeholder when no members
            JLabel placeholder = new JLabel("No clan members online");
            placeholder.setForeground(Color.GRAY);
            placeholder.setBorder(new EmptyBorder(20, 10, 20, 10));
            placeholder.setAlignmentX(Component.CENTER_ALIGNMENT);
            memberListPanel.add(placeholder);
        }
        else
        {
            // Add a row for each member
            for (ClanChannelMember member : members)
            {
                JPanel row = createMemberRow(member);
                memberListPanel.add(row);
            }
        }
        
        // Refresh the display
        memberListPanel.revalidate();
        memberListPanel.repaint();
        
        log.debug("Updated member list with {} members", members.size());
    }
    
    /**
     * Updates the authentication status display.
     * 
     * @param authenticated true if user is verified
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
    
    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================
    
    /**
     * Creates a row component for a single clan member.
     * 
     * @param member The clan member to display
     * @return A JPanel containing the member info
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
        
        // Status chip (placeholder - will be populated from backend later)
        JLabel statusLabel = new JLabel("•");
        statusLabel.setForeground(Color.GRAY);
        row.add(statusLabel, BorderLayout.EAST);
        
        // Add some spacing between rows
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
        log.debug("Status changed to: {}", selectedStatus);
        
        // TODO: Send status update to backend
    }
    
    /**
     * Called when the login button is clicked.
     */
    private void onLoginClicked()
    {
        log.debug("Login button clicked");
        
        // TODO: Open Discord OAuth2 flow
        JOptionPane.showMessageDialog(
            this,
            "Discord login coming soon!\n\nThis will open your browser to authenticate with Discord.",
            "Login with Discord",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
