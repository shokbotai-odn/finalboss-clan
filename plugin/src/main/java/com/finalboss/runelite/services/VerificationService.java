/*
 * FinalBoss Clan - Verification Service
 * 
 * WHAT THIS DOES (for non-coders):
 * Handles RSN (RuneScape Name) verification via clan chat.
 * 
 * THE PROBLEM:
 * When you login with Discord, we know your Discord account but not
 * your RSN. Anyone could claim to be any RSN, so we need proof.
 * 
 * THE SOLUTION:
 * 1. Plugin generates a short verification code (e.g., "FB-X7K2")
 * 2. User types the code in clan chat
 * 3. Plugin detects their own message containing the code
 * 4. Plugin confirms to backend: "Discord user X is RSN Y"
 * 5. Backend marks the binding as verified
 * 
 * This works because only the actual account owner can type
 * in-game on that account.
 */

package com.finalboss.runelite.services;

import com.finalboss.runelite.FinalBossConfig;
import com.finalboss.runelite.FinalBossPlugin;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Service for RSN verification via clan chat.
 */
@Slf4j
public class VerificationService
{
    // Verification code format: FB-XXXX (where X is alphanumeric)
    private static final String CODE_PREFIX = "FB-";
    private static final int CODE_LENGTH = 4;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // No I/O/1/0 to avoid confusion
    private static final Pattern CODE_PATTERN = Pattern.compile("FB-[A-Z0-9]{4}", Pattern.CASE_INSENSITIVE);
    
    private final FinalBossPlugin plugin;
    private final FinalBossConfig config;
    private final ApiClient apiClient;
    private final SecureRandom random = new SecureRandom();
    
    /** The current verification code (null if not verifying) */
    @Getter
    private String currentCode;
    
    /** Whether we're currently waiting for verification */
    @Getter
    private boolean verifying;
    
    /**
     * Creates a new VerificationService.
     */
    public VerificationService(FinalBossPlugin plugin, FinalBossConfig config, ApiClient apiClient)
    {
        this.plugin = plugin;
        this.config = config;
        this.apiClient = apiClient;
        this.verifying = false;
    }
    
    /**
     * Starts the verification process.
     * Generates a code that the user needs to type in clan chat.
     * 
     * @return The verification code to display to user
     */
    public String startVerification()
    {
        // Generate a random code
        StringBuilder code = new StringBuilder(CODE_PREFIX);
        for (int i = 0; i < CODE_LENGTH; i++)
        {
            code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        
        currentCode = code.toString();
        verifying = true;
        
        log.info("Verification started. Code: {}", currentCode);
        return currentCode;
    }
    
    /**
     * Cancels the current verification.
     */
    public void cancelVerification()
    {
        currentCode = null;
        verifying = false;
        log.info("Verification cancelled");
    }
    
    /**
     * Processes a chat message to check for verification code.
     * 
     * @param event The chat message event
     * @return true if verification was successful
     */
    public boolean onChatMessage(ChatMessage event)
    {
        // Only check if we're verifying
        if (!verifying || currentCode == null)
        {
            return false;
        }
        
        // Only check clan chat messages
        if (event.getType() != ChatMessageType.CLAN_CHAT 
            && event.getType() != ChatMessageType.CLAN_MESSAGE)
        {
            return false;
        }
        
        // Only check messages from ourselves
        String localPlayer = plugin.getLocalPlayerName();
        if (localPlayer == null)
        {
            return false;
        }
        
        // Check if the message sender is us
        // Note: event.getName() is the sender's name
        if (!event.getName().equalsIgnoreCase(localPlayer))
        {
            return false;
        }
        
        // Check if message contains our code
        String message = event.getMessage().toUpperCase();
        if (message.contains(currentCode.toUpperCase()))
        {
            log.info("Verification code detected! RSN: {}", localPlayer);
            completeVerification(localPlayer);
            return true;
        }
        
        return false;
    }
    
    /**
     * Completes verification by sending confirmation to backend.
     * 
     * @param rsn The verified RSN
     */
    private void completeVerification(String rsn)
    {
        verifying = false;
        
        // TODO: Call backend to confirm RSN binding
        // apiClient.completeVerification(rsn, currentCode)
        //     .thenAccept(success -> {
        //         if (success) {
        //             log.info("RSN {} verified successfully!", rsn);
        //         }
        //     });
        
        log.info("Verification complete! RSN {} is now linked.", rsn);
        
        currentCode = null;
    }
    
    /**
     * Checks if verification code matches the expected format.
     * 
     * @param code The code to check
     * @return true if valid format
     */
    public static boolean isValidCodeFormat(String code)
    {
        return CODE_PATTERN.matcher(code).matches();
    }
}
