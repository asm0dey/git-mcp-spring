package com.github.asm0dey.git_mcp_spring.model;

import org.eclipse.jgit.lib.RebaseTodoLine;

/**
 * Represents an entry in a rebase plan.
 * Contains information about a commit to be rebased.
 * 
 * @param id Full commit ID
 * @param shortId Short commit ID (7 characters)
 * @param message Short commit message
 * @param fullMessage Full commit message
 * @param action Rebase action to perform (PICK, EDIT, etc.)
 */
public record RebasePlanEntry(
    String id,
    String shortId,
    String message,
    String fullMessage,
    RebaseTodoLine.Action action
) {
    /**
     * Represents author information for a commit.
     * 
     * @param name Author's name
     * @param email Author's email address
     * @param time Commit timestamp
     */
    public record Author(
        String name,
        String email,
        long time
    ) {}
}