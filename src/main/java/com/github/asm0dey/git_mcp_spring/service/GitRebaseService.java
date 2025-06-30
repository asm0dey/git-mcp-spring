package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.model.GitResponse;
import com.github.asm0dey.git_mcp_spring.model.GitResponse.*;
import com.github.asm0dey.git_mcp_spring.model.RebasePlanEntry;
import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RebaseTodoLine;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.eclipse.jgit.api.RebaseCommand.Operation.*;
import static org.eclipse.jgit.api.RebaseResult.Status.*;
import static org.eclipse.jgit.lib.RebaseTodoLine.Action.PICK;

/**
 * Service for Git rebase operations.
 * Provides methods for interactive rebase functionality.
 */
@Service
public class GitRebaseService {
    private static final Logger logger = LoggerFactory.getLogger(GitRebaseService.class);

    private final GitRepositoryResource repository;

    /**
     * Creates a new Git rebase service.
     * 
     * @param repository Git repository resource
     */
    public GitRebaseService(GitRepositoryResource repository) {
        this.repository = repository;
    }

    /**
     * Starts an interactive rebase operation.
     * 
     * @param upstream Upstream branch or commit to rebase onto
     * @param branch Branch to rebase (null for current branch)
     * @return Result of the operation with rebase plan
     */
    @Tool(name = "git_rebase_start", description = "Starts an interactive rebase operation")
    public GitResponse startInteractiveRebase(String upstream, String branch) {
        logger.debug("Starting interactive rebase onto {} for branch {}", upstream, branch);

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Resolve upstream commit
            ObjectId upstreamCommit = repo.resolve(upstream);
            if (upstreamCommit == null) {
                logger.error("Invalid upstream reference: {}", upstream);
                return new ErrorResponse("Invalid upstream reference: " + upstream);
            }

            // Get the rebase plan (commits to be rebased)
            List<RebasePlanEntry> rebasePlan = getRebasePlan(upstream, branch);

            String branchName = branch != null ? branch : repo.getBranch();

            return new RebasePlanResponse(
                rebasePlan,
                upstreamCommit.getName(),
                branchName,
                "Interactive rebase plan created"
            );
        } catch (IOException | GitAPIException e) {
            logger.error("Error starting interactive rebase: {}", e.getMessage(), e);
            return new ErrorResponse(e.getMessage());
        }
    }

    /**
     * Gets the plan for a rebase operation (commits to be rebased).
     * 
     * @param upstream Upstream branch or commit to rebase onto
     * @param branch Branch to rebase (null for current branch)
     * @return List of commits to be rebased as RebasePlanEntry records
     */
    @Tool(name = "git_rebase_plan", description = "Gets the plan for a rebase operation")
    public List<RebasePlanEntry> getRebasePlan(String upstream, String branch) 
            throws IOException, GitAPIException {
        Repository repo = repository.getRepository();

        // Resolve branch and upstream commits
        ObjectId upstreamCommit = repo.resolve(upstream);
        ObjectId branchCommit = repo.resolve(Objects.requireNonNullElse(branch, "HEAD"));

        if (upstreamCommit == null || branchCommit == null) {
            throw new IllegalArgumentException("Invalid branch or upstream reference");
        }

        // Get commits between upstream and branch
        List<RevCommit> commits = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repo)) {
            walk.markStart(walk.parseCommit(branchCommit));
            walk.markUninteresting(walk.parseCommit(upstreamCommit));

            for (RevCommit commit : walk) {
                commits.add(commit);
            }
        }

        // Convert commits to rebase plan entries

        return getRebasePlanEntries(commits);
    }

    private static List<RebasePlanEntry> getRebasePlanEntries(List<RevCommit> commits) {
        List<RebasePlanEntry> rebasePlan = new ArrayList<>();
        for (RevCommit commit : commits) {
            String id = commit.getName();
            String shortId = commit.getName().substring(0, 7);
            String message = commit.getShortMessage();
            String fullMessage = commit.getFullMessage();

            // Create and add the entry
            RebasePlanEntry entry = new RebasePlanEntry(
                id, shortId, message, fullMessage, PICK
            );
            rebasePlan.add(entry);
        }
        return rebasePlan;
    }

    /**
     * Executes a rebase operation with the specified plan.
     * 
     * @param upstream Upstream branch or commit to rebase onto
     * @param rebasePlan List of rebase operations to perform
     * @return Result of the rebase operation
     */
    @Tool(name = "git_rebase_execute", description = "Executes a rebase operation with the specified plan")
    public GitResponse executeRebase(String upstream, List<RebasePlanEntry> rebasePlan) {
        logger.debug("Executing rebase onto {} with {} operations", upstream, rebasePlan.size());

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Resolve upstream commit
            ObjectId upstreamCommit = repo.resolve(upstream);
            if (upstreamCommit == null) {
                logger.error("Invalid upstream reference: {}", upstream);
                return new ErrorResponse("Invalid upstream reference: " + upstream);
            }

            // Create rebase todo lines from the plan
            List<RebaseTodoLine> todoLines = new ArrayList<>();
            for (RebasePlanEntry entry : rebasePlan) {
                String commitId = entry.id();
                RebaseTodoLine.Action action = entry.action();
                String message = entry.message();

                ObjectId commitObjectId = ObjectId.fromString(commitId);

                // Convert ObjectId to AbbreviatedObjectId
                RebaseTodoLine todoLine = new RebaseTodoLine(action, commitObjectId.abbreviate(7), message);
                todoLines.add(todoLine);
            }

            // Create an interactive handler with our custom todo lines
            GitRebaseInteractiveHandler interactiveHandler = new GitRebaseInteractiveHandler(todoLines);

            // Start the rebase operation with the interactive handler
            RebaseResult rebaseResult = git.rebase()
                    .setUpstream(upstreamCommit)
                    .setOperation(BEGIN)
                    .runInteractively(interactiveHandler)
                    .call();

            // If the rebase started successfully, continue the rebase
            if (rebaseResult.getStatus() == EDIT ||
                rebaseResult.getStatus() == STOPPED) {

                // Continue the rebase to completion
                rebaseResult = git.rebase()
                        .setOperation(CONTINUE)
                        .call();
            }

            // Process the result
            String status = rebaseResult.getStatus().name();
            String message;
            List<String> conflictingFiles = null;
            String currentCommit = null;

            if (rebaseResult.getStatus() == FAILED) {
                message = "Rebase failed";
                conflictingFiles = rebaseResult.getConflicts();
            } else if (rebaseResult.getStatus() == STOPPED) {
                message = "Rebase stopped for editing";
                currentCommit = rebaseResult.getCurrentCommit().getName();
            } else if (rebaseResult.getStatus() == OK) {
                message = "Rebase completed successfully";
            } else {
                message = "Rebase status: " + status;
            }

            return new RebaseExecuteResponse(status, message, conflictingFiles, currentCommit);
        } catch (IOException | GitAPIException e) {
            logger.error("Error executing rebase: {}", e.getMessage(), e);
            return new ErrorResponse(e.getMessage());
        }
    }

    /**
     * Continues a stopped rebase operation.
     * 
     * @return Result of the continue operation
     */
    @Tool(name = "git_rebase_continue", description = "Continues a stopped rebase operation")
    public GitResponse continueRebase() {
        logger.debug("Continuing rebase operation");

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Continue the rebase
            RebaseResult rebaseResult = git.rebase()
                    .setOperation(CONTINUE)
                    .call();

            // Process the result
            String status = rebaseResult.getStatus().name();
            String message;
            List<String> conflictingFiles = null;
            String currentCommit = null;

            if (rebaseResult.getStatus() == FAILED) {
                message = "Rebase failed";
                conflictingFiles = rebaseResult.getConflicts();
            } else if (rebaseResult.getStatus() == STOPPED) {
                message = "Rebase stopped for editing";
                currentCommit = rebaseResult.getCurrentCommit().getName();
            } else if (rebaseResult.getStatus() == OK) {
                message = "Rebase completed successfully";
            } else {
                message = "Rebase status: " + status;
            }

            return new RebaseContinueResponse(status, message, conflictingFiles, currentCommit);
        } catch (GitAPIException e) {
            logger.error("Error continuing rebase: {}", e.getMessage(), e);
            return new ErrorResponse(e.getMessage());
        }
    }

    /**
     * Aborts a rebase operation in progress.
     * 
     * @return Result of the abort operation
     */
    @Tool(name = "git_rebase_abort", description = "Aborts a rebase operation in progress")
    public GitResponse abortRebase() {
        logger.debug("Aborting rebase operation");

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Abort the rebase
            RebaseResult rebaseResult = git.rebase()
                    .setOperation(ABORT)
                    .call();

            // Process the result
            String status = rebaseResult.getStatus().name();
            return new RebaseAbortResponse(status, "Rebase aborted");
        } catch (GitAPIException e) {
            logger.error("Error aborting rebase: {}", e.getMessage(), e);
            return new ErrorResponse(e.getMessage());
        }
    }

    /**
     * Skips the current commit in a rebase operation.
     * 
     * @return Result of the skip operation
     */
    @Tool(name = "git_rebase_skip", description = "Skips the current commit in a rebase operation")
    public GitResponse skipRebaseCommit() {
        logger.debug("Skipping current commit in rebase operation");

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Skip the current commit
            RebaseResult rebaseResult = git.rebase()
                    .setOperation(SKIP)
                    .call();

            // Process the result
            String status = rebaseResult.getStatus().name();
            String message;
            List<String> conflictingFiles = null;
            String currentCommit = null;

            if (rebaseResult.getStatus() == FAILED) {
                message = "Rebase failed";
                conflictingFiles = rebaseResult.getConflicts();
            } else if (rebaseResult.getStatus() == STOPPED) {
                message = "Rebase stopped for editing";
                currentCommit = rebaseResult.getCurrentCommit().getName();
            } else if (rebaseResult.getStatus() == OK) {
                message = "Commit skipped successfully";
            } else {
                message = "Rebase status: " + status;
            }

            return new RebaseContinueResponse(status, message, conflictingFiles, currentCommit);
        } catch (GitAPIException e) {
            logger.error("Error skipping commit: {}", e.getMessage(), e);
            return new ErrorResponse(e.getMessage());
        }
    }
}
