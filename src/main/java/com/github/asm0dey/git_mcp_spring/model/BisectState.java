package com.github.asm0dey.git_mcp_spring.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the state of a git bisect session.
 * This class is used to track the progress of a bisect operation.
 */
public record BisectState(
    String goodCommit,
    String badCommit,
    List<String> remainingCommits,
    List<String> testedCommits,
    String status
) {
    /**
     * Creates a new BisectState with default values.
     * 
     * @param goodCommit The commit known to be good
     * @param badCommit The commit known to be bad
     * @param remainingCommits List of commits to test
     * @return A new BisectState
     */
    public static BisectState create(String goodCommit, String badCommit, List<String> remainingCommits) {
        return new BisectState(
            goodCommit,
            badCommit,
            remainingCommits,
            new ArrayList<>(),
            "in_progress"
        );
    }
    
    /**
     * Creates a new BisectState with the current commit marked as good.
     * 
     * @param currentCommit The commit to mark as good
     * @return A new BisectState with updated values
     */
    public BisectState withGoodCommit(String currentCommit) {
        List<String> newTestedCommits = new ArrayList<>(testedCommits);
        newTestedCommits.add(currentCommit);
        
        List<String> newRemainingCommits = new ArrayList<>(remainingCommits);
        newRemainingCommits.removeIf(commit -> commit.equals(currentCommit));
        
        return new BisectState(
            currentCommit,
            badCommit,
            newRemainingCommits,
            newTestedCommits,
            remainingCommits.isEmpty() ? "complete" : "in_progress"
        );
    }
    
    /**
     * Creates a new BisectState with the current commit marked as bad.
     * 
     * @param currentCommit The commit to mark as bad
     * @return A new BisectState with updated values
     */
    public BisectState withBadCommit(String currentCommit) {
        List<String> newTestedCommits = new ArrayList<>(testedCommits);
        newTestedCommits.add(currentCommit);
        
        List<String> newRemainingCommits = new ArrayList<>(remainingCommits);
        newRemainingCommits.removeIf(commit -> commit.equals(currentCommit));
        
        return new BisectState(
            goodCommit,
            currentCommit,
            newRemainingCommits,
            newTestedCommits,
            remainingCommits.isEmpty() ? "complete" : "in_progress"
        );
    }
    
    /**
     * Checks if the bisect session is complete.
     * 
     * @return true if the bisect session is complete, false otherwise
     */
    public boolean isComplete() {
        return "complete".equals(status) || remainingCommits.isEmpty();
    }
}