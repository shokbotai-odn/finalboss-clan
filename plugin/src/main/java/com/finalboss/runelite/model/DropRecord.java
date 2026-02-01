/*
 * FinalBoss Clan - Drop Record
 * 
 * WHAT THIS IS (for non-coders):
 * A data container for loot drops. When someone gets a notable drop,
 * the information gets stored in one of these objects.
 */

package com.finalboss.runelite.model;

import lombok.Data;
import java.util.Date;

/**
 * Represents a logged drop from the database.
 * 
 * Matches the 'drops' table structure.
 */
@Data
public class DropRecord
{
    /** Database ID */
    private String id;
    
    /** Reference to the user who got the drop */
    private String user_id;
    
    /** The player's RSN at time of drop */
    private String rsn;
    
    /** OSRS item ID */
    private int item_id;
    
    /** Item name */
    private String item_name;
    
    /** Quantity received */
    private int quantity;
    
    /** GP value (can be very large for expensive items) */
    private long value;
    
    /** Where the drop came from (e.g., "Theatre of Blood") */
    private String source;
    
    /** Session ID if part of a group session */
    private String session_id;
    
    /** When the drop was logged */
    private Date created_at;
    
    /**
     * Gets a formatted string for the drop value.
     * 
     * @return Value formatted with K/M/B suffixes
     */
    public String getFormattedValue()
    {
        if (value >= 1_000_000_000)
        {
            return String.format("%.2fB", value / 1_000_000_000.0);
        }
        else if (value >= 1_000_000)
        {
            return String.format("%.2fM", value / 1_000_000.0);
        }
        else if (value >= 1_000)
        {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
    
    /**
     * Gets total value (value * quantity).
     * 
     * @return Total GP value
     */
    public long getTotalValue()
    {
        return value * quantity;
    }
}
