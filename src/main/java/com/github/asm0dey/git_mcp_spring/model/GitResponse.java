package com.github.asm0dey.git_mcp_spring.model;

import java.util.List;
import java.util.Map;
import com.github.asm0dey.git_mcp_spring.model.RebasePlanEntry;

/**
 * Sealed hierarchy of response records for Git operations.
 * Provides a type-safe alternative to Map<String, Object> for operation results.
 */
public interface GitResponse {

    /**
     * @return Whether the operation was successful
     */
    boolean isSuccess();

    /**
     * Basic success response with a message.
     * 
     * @param message Success message
     */
    record SuccessResponse(String message) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * Error response with an error message.
     * 
     * @param error Error message describing what went wrong
     */
    record ErrorResponse(String error) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * Response for rebase plan creation.
     * 
     * @param plan List of commits to be rebased
     * @param upstreamCommit Upstream commit ID
     * @param branch Branch being rebased
     * @param message Success message
     */
    record RebasePlanResponse(
            List<RebasePlanEntry> plan, 
            String upstreamCommit, 
            String branch, 
            String message) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * Response for rebase execution.
     * 
     * @param status Status of the rebase operation
     * @param message Success or informational message
     * @param conflictingFiles List of conflicting files (if any)
     * @param currentCommit Current commit being processed (if stopped)
     */
    record RebaseExecuteResponse(
            String status, 
            String message, 
            List<String> conflictingFiles, 
            String currentCommit) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return "OK".equals(status);
        }
    }

    /**
     * Response for continuing a rebase operation.
     * 
     * @param status Status of the rebase operation
     * @param message Success or informational message
     * @param conflictingFiles List of conflicting files (if any)
     * @param currentCommit Current commit being processed (if stopped)
     */
    record RebaseContinueResponse(
            String status, 
            String message, 
            List<String> conflictingFiles, 
            String currentCommit) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return "OK".equals(status);
        }
    }

    /**
     * Response for aborting a rebase operation.
     * 
     * @param status Status of the rebase operation
     * @param message Success message
     */
    record RebaseAbortResponse(
            String status, 
            String message) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return "ABORTED".equals(status);
        }
    }

    /**
     * Response for branch operations.
     * 
     * @param message Success message
     * @param branch Branch information
     */
    record BranchResponse(
            String message, 
            Map<String, Object> branch) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * Response for commit operations.
     * 
     * @param message Success message
     * @param commit Commit information
     */
    record CommitResponse(
            String message,
            Map<String, Object> commit) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * Response for file operations.
     * 
     * @param message Success message
     * @param content File content or operation result
     */
    record FileResponse(
            String message,
            Object content) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * Response for repository status.
     * 
     * @param exists Whether the repository exists
     * @param bare Whether the repository is bare
     * @param empty Whether the repository is empty
     * @param branch Current branch name (may be null)
     * @param clean Whether the working directory is clean
     * @param modified Whether there are modified files
     * @param untracked Whether there are untracked files
     * @param added Whether there are added files
     * @param changed Whether there are changed files
     * @param removed Whether there are removed files
     * @param missing Whether there are missing files
     * @param conflicting Whether there are conflicting files
     */
    record RepositoryStatusResponse(
            boolean exists,
            boolean bare,
            boolean empty,
            String branch,
            boolean clean,
            boolean modified,
            boolean untracked,
            boolean added,
            boolean changed,
            boolean removed,
            boolean missing,
            boolean conflicting) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * Response for bisect operations.
     * 
     * @param status Status of the bisect operation (started, in_progress, complete, reset, etc.)
     * @param message Success or informational message
     * @param data Additional data related to the bisect operation
     */
    record BisectResponse(
            String status,
            String message,
            BisectData data) implements GitResponse {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }
}
