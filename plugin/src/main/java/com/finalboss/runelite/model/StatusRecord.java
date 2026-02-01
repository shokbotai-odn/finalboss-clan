/*
 * FinalBoss Clan - Status Record
 * 
 * WHAT THIS IS (for non-coders):
 * A simple data container that holds information about a player's status.
 * When we get status data from the server, it gets converted into these objects.
 */

package com.finalboss.runelite.model;

import lombok.Data;
import java.util.Date;

/**
 * Represents a user's activity status from the database.
 * 
 * Matches the 'statuses' table structure with joined user data.
 */
@Data
public class StatusRecord
{
    /** Database ID */
    private String id;
    
    /** Reference to the user */
    private String user_id;
    
    /** The status type (e.g., "TOB", "Bossing", "AFK") */
    private String status;
    
    /** Optional note (e.g., "need 1 more") */
    private String note;
    
    /** When the status was last updated */
    private Date updated_at;
    
    /** When the status expires (null = never) */
    private Date expires_at;
    
    /** Nested user data from join */
    private UserInfo users;
    
    /**
     * Gets the RSN (RuneScape Name) for this status.
     * 
     * @return The player's RSN, or "Unknown" if not available
     */
    public String getRsn()
    {
        if (users != null && users.rsn != null)
        {
            return users.rsn;
        }
        return "Unknown";
    }
    
    /**
     * Checks if this status has expired.
     * 
     * @return true if expired, false if still active or no expiration
     */
    public boolean isExpired()
    {
        if (expires_at == null)
        {
            return false;  // No expiration = never expires
        }
        return new Date().after(expires_at);
    }
    
    /**
     * Nested class for user info from the join.
     */
    @Data
    public static class UserInfo
    {
        private String rsn;
    }
}
