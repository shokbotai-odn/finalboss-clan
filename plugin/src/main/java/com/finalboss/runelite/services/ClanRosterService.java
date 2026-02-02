package com.finalboss.runelite.services;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for accessing the current clan channel roster.
 *
 * This is a thin wrapper around RuneLite's Client API that provides:
 * - Access to online clan members in the current clan channel
 * - Clan channel state checks (is player in a clan?)
 * - Clan name retrieval
 *
 * Note: This service only sees players who are ONLINE and in the same
 * clan chat channel. It does not provide access to the full clan member
 * list or offline members. For full membership, use the Wise Old Man API.
 *
 * The clan channel is the in-game "Clan Chat" feature, not to be confused
 * with the newer "Clan" system (which this service does not currently use).
 */
@Slf4j
public class ClanRosterService
{
    // RuneLite game client for clan channel access
    private final Client client;

    public ClanRosterService(Client client)
    {
        this.client = client;
    }

    // ========== Member Access ==========

    /**
     * Get all online members in the current clan channel.
     * Returns a defensive copy to prevent modification of internal state.
     *
     * @return List of clan channel members, or empty list if not in a clan
     */
    public List<ClanChannelMember> getMembers()
    {
        ClanChannel channel = client.getClanChannel();

        if (channel == null)
        {
            log.debug("No clan channel available");
            return Collections.emptyList();
        }

        List<ClanChannelMember> members = channel.getMembers();

        if (members == null)
        {
            log.debug("Clan channel has no members");
            return Collections.emptyList();
        }

        // Return a copy to avoid external modification
        return new ArrayList<>(members);
    }

    /**
     * Get the count of online members in the clan channel.
     *
     * @return Number of online clan members
     */
    public int getMemberCount()
    {
        return getMembers().size();
    }

    // ========== State Checks ==========

    /**
     * Check if the player is currently in a clan channel.
     *
     * @return true if in a clan channel, false otherwise
     */
    public boolean isInClan()
    {
        return client.getClanChannel() != null;
    }

    /**
     * Get the name of the current clan channel.
     *
     * @return Clan name, or null if not in a clan
     */
    public String getClanName()
    {
        ClanChannel channel = client.getClanChannel();
        if (channel == null)
        {
            return null;
        }
        return channel.getName();
    }
}
