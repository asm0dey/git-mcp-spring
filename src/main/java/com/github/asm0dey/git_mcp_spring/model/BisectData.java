package com.github.asm0dey.git_mcp_spring.model;

/**
 * Represents the data returned by a git bisect operation.
 * This class is used to provide structured data in BisectResponse.
 */
public record BisectData(
    String status,
    String message,
    CommitInfo currentCommit,
    Integer remainingCount,
    BisectState bisectState,
    CommitInfo firstBadCommit
) {
    /**
     * Creates a new BisectData for a started bisect session.
     * 
     * @param currentCommit The current commit to test
     * @param remainingCount The number of remaining commits to test
     * @param bisectState The current state of the bisect session
     * @return A new BisectData
     */
    public static BisectData started(CommitInfo currentCommit, int remainingCount, BisectState bisectState) {
        return new BisectData(
            "started",
            "Bisect started. Please test this commit and mark it as good or bad.",
            currentCommit,
            remainingCount,
            bisectState,
            null
        );
    }

    /**
     * Creates a new BisectData for an in-progress bisect session.
     * 
     * @param currentCommit The current commit to test
     * @param remainingCount The number of remaining commits to test
     * @param bisectState The current state of the bisect session
     * @param isGood Whether the previous commit was marked as good
     * @return A new BisectData
     */
    public static BisectData inProgress(CommitInfo currentCommit, int remainingCount, BisectState bisectState, boolean isGood) {
        String message = isGood 
            ? "Commit marked as good. Please test this commit and mark it as good or bad."
            : "Commit marked as bad. Please test this commit and mark it as good or bad.";

        return new BisectData(
            "in_progress",
            message,
            currentCommit,
            remainingCount,
            bisectState,
            null
        );
    }

    /**
     * Creates a new BisectData for a completed bisect session.
     * 
     * @param firstBadCommit The first bad commit found
     * @return A new BisectData
     */
    public static BisectData complete(CommitInfo firstBadCommit) {
        return new BisectData(
            "complete",
            "Bisect complete. The first bad commit is: " + firstBadCommit.id(),
            null,
            0,
            null,
            firstBadCommit
        );
    }

    /**
     * Creates a new BisectData for a reset bisect session.
     * 
     * @param message The reset message
     * @return A new BisectData
     */
    public static BisectData reset(String message) {
        return new BisectData(
            "reset",
            message,
            null,
            0,
            null,
            null
        );
    }
}
