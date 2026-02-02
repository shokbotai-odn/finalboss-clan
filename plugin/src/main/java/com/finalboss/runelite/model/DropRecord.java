package com.finalboss.runelite.model;

import lombok.Data;

/**
 * Data model representing a notable loot drop.
 *
 * Maps to the 'drops' table in Supabase:
 * - rsn: Player's RuneScape name who received the drop
 * - item_id: OSRS item ID (used for wiki lookup/icons)
 * - item_name: Display name of the item
 * - quantity: Number of items received
 * - value: Total value in GP (quantity * price)
 * - source: Where the drop came from (boss name, activity, etc.)
 *
 * Field naming uses snake_case to match Supabase/PostgreSQL conventions
 * for direct Gson serialization. The created_at timestamp is auto-generated
 * by Supabase on insert.
 *
 * Flow: DropService -> ApiClient.logDrop() -> Supabase -> Discord Bot announcement
 *
 * @see DropService#processItem(ItemStack, String) for creation
 * @see ApiClient#logDrop(DropRecord) for submission
 */
@Data
public class DropRecord
{
    /** Player's RuneScape name who received the drop */
    private String rsn;

    /** OSRS item ID (e.g., 11832 for Bandos Chestplate) */
    private int item_id;

    /** Display name of the item (e.g., "Bandos chestplate") */
    private String item_name;

    /** Number of items received (usually 1, but can be more for stackables) */
    private int quantity;

    /** Total value in GP (quantity * item price) */
    private long value;

    /** Source of the drop: boss name, activity, etc. (e.g., "General Graardor") */
    private String source;

    /**
     * Creates a new drop record with all required fields.
     *
     * @param rsn Player's RuneScape name
     * @param itemId OSRS item ID
     * @param itemName Display name of the item
     * @param quantity Number of items
     * @param value Total value in GP
     * @param source Boss/activity name
     */
    public DropRecord(String rsn, int itemId, String itemName, int quantity, long value, String source)
    {
        this.rsn = rsn;
        this.item_id = itemId;
        this.item_name = itemName;
        this.quantity = quantity;
        this.value = value;
        this.source = source;
    }
}
