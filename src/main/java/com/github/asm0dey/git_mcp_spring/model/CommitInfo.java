package com.github.asm0dey.git_mcp_spring.model;

import java.util.Date;

/**
 * Represents information about a Git commit.
 * This class is used to provide structured commit data instead of using maps.
 */
public record CommitInfo(
    String id,
    String shortId,
    String message,
    String authorName,
    String authorEmail,
    Date commitTime
) {
}