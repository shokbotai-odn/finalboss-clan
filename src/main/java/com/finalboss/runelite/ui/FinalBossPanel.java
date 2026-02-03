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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sidebar panel UI for the FinalBoss Clan plugin.
 *
 * This panel provides:
 * - Header with plugin title and status selector dropdown
 * - Scrollable list of online clan members with their activity statuses
 * - Color-coded status indicators (e.g., TOB = red, COX = blue)
 * - Authentication status display (WOM member verification)
 * - Automatic status refresh every 30 seconds
 *
 * Thread Safety:
 * - All UI updates must happen on the Event Dispatch Thread (EDT)
 * - Member statuses are stored in a ConcurrentHashMap for thread-safe access
 * - Status refresh runs in a background daemon thread
 *
 * Layout Structure:
 * - NORTH: Header with title and status dropdown
 * - CENTER: Scrollable member list with statuses
 * - SOUTH: Authentication status indicator
 *
 * @see FinalBossPlugin#startUp() where this panel is created
 */
@Slf4j
public class FinalBossPanel extends PluginPanel
{
    // ========== UI Constants ==========

    // Color palette for panel sections
    private static final Color HEADER_COLOR = new Color(30, 30, 30);
    private static final Color SECTION_COLOR = new Color(40, 40, 40);
    private static final Color SUCCESS_COLOR = new Color(67, 181, 129);  // Green for verified status
    private static final Color ERROR_COLOR = new Color(237, 66, 69);    // Red for errors/not verified

    // Status refresh timing (fetches statuses from Supabase periodically)
    private static final int STATUS_REFRESH_INITIAL_DELAY_SECONDS = 5;
    private static final int STATUS_REFRESH_INTERVAL_SECONDS = 30;

    // Color mapping for each activity status (matches Discord-style colors)
    private static final Map<String, Color> STATUS_COLORS = Map.of(
        "Available", new Color(67, 181, 129),     // Green
        "Bossing", new Color(250, 166, 26),       // Orange
        "TOB", new Color(237, 66, 69),            // Red (Theatre of Blood)
        "COX", new Color(88, 101, 242),           // Blue (Chambers of Xeric)
        "TOA", new Color(235, 69, 158),           // Pink (Tombs of Amascut)
        "Skilling", new Color(87, 242, 135),      // Light green
        "AFK", new Color(153, 170, 181),          // Gray
        "Do Not Disturb", new Color(237, 66, 69)  // Red
    );

    // Status options shown in the dropdown selector
    private static final String[] STATUS_OPTIONS = {
        "Available", "Bossing", "TOB", "COX", "TOA", "Skilling", "AFK", "Do Not Disturb"
    };

    // ========== Dependencies ==========

    private final FinalBossPlugin plugin;

    // ========== UI Components ==========

    private JLabel headerLabel;              // "FinalBoss Clan" title
    private JComboBox<String> statusSelector; // Dropdown for status selection
    private JLabel onlineCountLabel;          // "Online (N)" header
    private JPanel memberListPanel;           // Container for member rows
    private JLabel authStatusLabel;           // "Verified WOM Member" / "Not a clan member"

    // ========== Thread-Safe State ==========

    // Current list of online clan members (updated from game thread)
    private volatile List<ClanChannelMember> currentMembers = Collections.emptyList();

    // Map of RSN (lowercase) -> status string (e.g., "TOB", "Available")
    // ConcurrentHashMap for thread-safe access from refresh thread and EDT
    private final Map<String, String> memberStatuses = new ConcurrentHashMap<>();

    // Background executor for periodic status refresh
    private volatile ScheduledExecutorService statusRefresher;

    // ========== Constructor ==========

    /**
     * Creates the panel and starts the background status refresh task.
     *
     * @param plugin Reference to the main plugin for API access and config
     */
    public FinalBossPanel(FinalBossPlugin plugin)
    {
        super(false); // Don't wrap content - use full panel area
        this.plugin = plugin;

        buildPanel();
        startStatusRefresh();

        log.debug("FinalBossPanel created");
    }

    // ========== UI Construction ==========

    /**
     * Builds the panel layout with header, member list, and auth status sections.
     * Uses BorderLayout with NORTH (header), CENTER (members), and SOUTH (auth).
     */
    private void buildPanel()
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setBackground(HEADER_COLOR);
        northPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

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

        statusSelector = new JComboBox<>(STATUS_OPTIONS);
        statusSelector.setEnabled(false);
        statusSelector.addActionListener(e -> onStatusChanged());
        statusPanel.add(statusSelector, BorderLayout.CENTER);

        northPanel.add(statusPanel);
        add(northPanel, BorderLayout.NORTH);

        // Member list
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        centerPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

        JPanel sectionHeader = new JPanel(new BorderLayout());
        sectionHeader.setBackground(SECTION_COLOR);
        sectionHeader.setBorder(new EmptyBorder(5, 10, 5, 10));

        onlineCountLabel = new JLabel("Online (0)");
        onlineCountLabel.setForeground(Color.WHITE);
        sectionHeader.add(onlineCountLabel, BorderLayout.WEST);

        JButton refreshBtn = new JButton("↻");
        refreshBtn.setToolTipText("Refresh statuses");
        refreshBtn.addActionListener(e -> refreshStatuses());
        sectionHeader.add(refreshBtn, BorderLayout.EAST);

        centerPanel.add(sectionHeader, BorderLayout.NORTH);

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

        // Auth status panel
        JPanel authPanel = new JPanel();
        authPanel.setLayout(new BoxLayout(authPanel, BoxLayout.Y_AXIS));
        authPanel.setBackground(HEADER_COLOR);
        authPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        authStatusLabel = new JLabel("Checking membership...");
        authStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        authStatusLabel.setForeground(Color.GRAY);
        authPanel.add(authStatusLabel);

        add(authPanel, BorderLayout.SOUTH);
    }

    // ========== Public API ==========

    /**
     * Update the clan member list. Called from game thread via SwingUtilities.
     * Updates the member count header and rebuilds the member list display.
     *
     * @param members List of online clan members, or null/empty if none
     */
    public void updateMembers(List<ClanChannelMember> members)
    {
        this.currentMembers = members != null ? members : Collections.emptyList();
        int count = this.currentMembers.size();
        onlineCountLabel.setText("Online (" + count + ")");
        rebuildMemberList();
        log.debug("Updated member list with {} members", count);
    }

    /**
     * Update the authentication status display.
     * Enables/disables the status selector based on verification status.
     *
     * @param isMember true if user is verified as WOM group member
     */
    public void updateAuthStatus(boolean isMember)
    {
        if (isMember)
        {
            authStatusLabel.setText("✓ Verified WOM Member");
            authStatusLabel.setForeground(SUCCESS_COLOR);
            statusSelector.setEnabled(true);  // Enable status updates
        }
        else
        {
            authStatusLabel.setText("Not a clan member");
            authStatusLabel.setForeground(ERROR_COLOR);
            statusSelector.setEnabled(false);  // Disable status updates for non-members
        }
    }

    /**
     * Clean up resources when the plugin is disabled.
     * Stops the background status refresh executor.
     */
    public void shutdown()
    {
        ScheduledExecutorService executor = statusRefresher;
        if (executor != null)
        {
            executor.shutdownNow();
            statusRefresher = null;
        }
    }

    // ========== Background Tasks ==========

    /**
     * Starts the background task that periodically refreshes member statuses.
     * Uses a daemon thread so it doesn't prevent JVM shutdown.
     * Runs every 30 seconds after an initial 5 second delay.
     */
    private void startStatusRefresh()
    {
        statusRefresher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FinalBoss-StatusRefresh");
            t.setDaemon(true);
            return t;
        });
        statusRefresher.scheduleAtFixedRate(
            this::refreshStatuses,
            STATUS_REFRESH_INITIAL_DELAY_SECONDS,
            STATUS_REFRESH_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    /**
     * Fetches current statuses from Supabase and updates the local cache.
     * Filters out expired statuses and normalizes RSNs to lowercase for lookup.
     * Called both by the scheduled executor and the manual refresh button.
     */
    private void refreshStatuses()
    {
        plugin.getApiClient().getStatuses()
            .thenAccept(statuses -> {
                // Clear and rebuild thread-safe map
                memberStatuses.clear();
                for (StatusRecord record : statuses)
                {
                    // Skip null records and expired statuses
                    if (record != null && !record.isExpired())
                    {
                        String rsn = record.getRsn();
                        String status = record.getStatus();
                        if (rsn != null && status != null)
                        {
                            // Store with lowercase RSN for case-insensitive lookup
                            memberStatuses.put(rsn.toLowerCase(), status);
                        }
                    }
                }

                // Update UI on EDT
                SwingUtilities.invokeLater(this::rebuildMemberList);
            })
            .exceptionally(e -> {
                log.debug("Failed to refresh statuses: {}", e.getMessage());
                return null;
            });
    }

    // ========== UI Builders ==========

    /**
     * Rebuilds the member list panel with current members and their statuses.
     * Shows a placeholder message if no members are online.
     * Must be called on the EDT.
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
     * Creates a single row in the member list displaying the member's name and status.
     * Status is color-coded based on STATUS_COLORS map.
     * Shows a gray bullet point if no status is set for the member.
     *
     * @param member The clan channel member to create a row for
     * @return JPanel containing the formatted member row
     */
    private JPanel createMemberRow(ClanChannelMember member)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        // Member name on the left
        JLabel nameLabel = new JLabel(member.getName());
        nameLabel.setForeground(Color.WHITE);
        row.add(nameLabel, BorderLayout.WEST);

        // Status on the right (color-coded)
        String status = memberStatuses.get(member.getName().toLowerCase());
        JLabel statusLabel;

        if (status != null)
        {
            // Show status text with color
            statusLabel = new JLabel(status);
            Color statusColor = STATUS_COLORS.getOrDefault(status, Color.GRAY);
            statusLabel.setForeground(statusColor);
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 11f));
        }
        else
        {
            // No status set - show subtle bullet point
            statusLabel = new JLabel("•");
            statusLabel.setForeground(Color.DARK_GRAY);
        }

        row.add(statusLabel, BorderLayout.EAST);

        // Wrapper adds spacing between rows
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.add(row, BorderLayout.CENTER);
        wrapper.add(Box.createVerticalStrut(2), BorderLayout.SOUTH);

        return wrapper;
    }

    // ========== Event Handlers ==========

    /**
     * Handles status dropdown selection changes.
     * Sends the new status to the backend and updates the local cache on success.
     * Shows error dialogs on configuration or API errors.
     */
    private void onStatusChanged()
    {
        String selectedStatus = (String) statusSelector.getSelectedItem();
        if (selectedStatus == null) return;

        log.debug("Status changed to: {}", selectedStatus);

        // Check if API is configured
        if (!plugin.getApiClient().isConfigured())
        {
            JOptionPane.showMessageDialog(
                this,
                "API not configured. Please set the API URL and Key in plugin settings.",
                "Configuration Required",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // Get current player's RSN for local update after success
        String localRsn = plugin.getLocalPlayerName();

        int timeout = plugin.getConfig().statusTimeout();
        plugin.getApiClient().setStatus(selectedStatus, null, timeout)
            .thenAccept(success -> {
                SwingUtilities.invokeLater(() -> {
                    if (success)
                    {
                        log.info("Status updated to: {}", selectedStatus);
                        // Update local status map only after successful API sync
                        if (localRsn != null)
                        {
                            memberStatuses.put(localRsn.toLowerCase(), selectedStatus);
                            rebuildMemberList();
                        }
                    }
                    else
                    {
                        log.warn("Failed to update status");
                        JOptionPane.showMessageDialog(
                            this,
                            "Failed to update status. Please try again.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                });
            });
    }

}
