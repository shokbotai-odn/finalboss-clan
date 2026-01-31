/*
 * FinalBoss Clan - Clan Roster Service
 * 
 * WHAT THIS DOES (for non-coders):
 * This service is responsible for getting the list of clan members from the game.
 * It's like a middleman between the game's clan system and our plugin's UI.
 * 
 * HOW OSRS CLAN SYSTEM WORKS:
 * - Players join a "Clan Channel" (the online roster)
 * - The channel contains ClanChannelMember objects
 * - We can read this data but NOT modify it (read-only)
 */

package com.finalboss.runelite.services;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for managing the clan roster.
 * 
 * Provides a clean interface to access clan member data from the game client.
 * All methods are safe to call even when not logged in or not in a clan.
 */
@Slf4j
public class ClanRosterService
{
    private final Client client;
    
    /**
     * Creates a new ClanRosterService.
     * 
     * @param client The RuneLite game client
     */
    public ClanRosterService(Client client)
    {
        this.client = client;
    }
    
    /**
     * Gets the current list of clan members.
     * 
     * HOW IT WORKS:
     * 1. Get the clan channel from the client
     * 2. If no channel, return empty list
     * 3. Otherwise, return a copy of the member list
     * 
     * THREAD SAFETY:
     * Returns a new ArrayList, so the caller can modify it safely.
     * 
     * @return List of clan members (empty if not in a clan)
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
        
        // Return a copy so callers can't accidentally modify the original
        return new ArrayList<>(members);
    }
    
    /**
     * Gets the number of online clan members.
     * 
     * @return Number of members in the clan channel
     */
    public int getMemberCount()
    {
        return getMembers().size();
    }
    
    /**
     * Checks if the player is currently in a clan channel.
     * 
     * @return true if in a clan channel, false otherwise
     */
    public boolean isInClan()
    {
        return client.getClanChannel() != null;
    }
    
    /**
     * Gets the clan name if available.
     * 
     * @return The clan name, or null if not in a clan
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
    
    /**
     * Finds a member by their RSN (RuneScape Name).
     * 
     * @param rsn The player's name to search for
     * @return The ClanChannelMember if found, null otherwise
     */
    public ClanChannelMember findMember(String rsn)
    {
        if (rsn == null || rsn.isEmpty())
        {
            return null;
        }
        
        // RSN comparison is case-insensitive
        String searchName = rsn.toLowerCase();
        
        for (ClanChannelMember member : getMembers())
        {
            if (member.getName().toLowerCase().equals(searchName))
            {
                return member;
            }
        }
        
        return null;
    }
}
