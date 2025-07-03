package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.model.Result;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.github.asm0dey.git_mcp_spring.model.Result.failure;
import static com.github.asm0dey.git_mcp_spring.model.Result.success;

/**
 * Service for Git interactive rebase operations.
 * Provides methods for interactive rebase functionality including commit manipulation.
 */
@Service
public class GitInteractiveRebaseService {
    private static final Logger logger = LoggerFactory.getLogger(GitInteractiveRebaseService.class);

    private final GitRepositoryService repository;

    /**
     * Represents a commit in the rebase plan.
     */
    public record RebaseCommit(
            String commitId,
            String message,
            String author,
            String authorEmail,
            LocalDateTime date,
            String action
    ) {
    }

    /**
     * Represents display settings for commit information.
     */
    public record CommitDisplaySettings(
            boolean useFullCommitId,
            boolean useFullMessage
    ) {
        public static CommitDisplaySettings shortFormat() {
            return new CommitDisplaySettings(false, false);
        }

        public static CommitDisplaySettings fullFormat() {
            return new CommitDisplaySettings(true, true);
        }

        public static CommitDisplaySettings custom(boolean useFullCommitId, boolean useFullMessage) {
            return new CommitDisplaySettings(useFullCommitId, useFullMessage);
        }
    }

    /**
     * Represents a commit with a numeric ID for easy reference.
     */
    public record NumberedCommit(
            int id,
            String commitId,
            String message,
            String author
    ) {
    }

    /**
     * Represents a rebase instruction.
     */
    public record RebaseInstruction(
            String action,  // pick, squash, drop, reword, edit, fixup
            int commitId,   // numeric ID of the commit
            String newMessage  // for reword action
    ) {
    }

    /**
     * Represents the result of a rebase operation.
     */
    public record RebaseExecutionResult(
            boolean success,
            boolean hasConflicts,
            String message,
            List<String> conflictedFiles
    ) {
    }

    /**
     * Represents the current state of an interactive rebase.
     */
    public record RebaseStatus(
            boolean isInProgress,
            int currentStep,
            int totalSteps,
            List<RebaseCommit> commits,
            boolean conflicted
    ) {
    }

    /**
     * Options for starting an interactive rebase.
     */
    public record RebaseOptions(
            String baseCommit,
            boolean preserve
    ) {
        public static RebaseOptions defaults(String baseCommit) {
            return new RebaseOptions(baseCommit, false);
        }
    }

    /**
     * Creates a new Git interactive rebase service.
     */
    public GitInteractiveRebaseService(GitRepositoryService repository) {
        this.repository = repository;
    }

    /**
     * Gets the current status of an interactive rebase.
     */
    @Tool(name = "git_rebase_status", description = "Gets the current status of an interactive rebase, including whether one is in progress, current step, and list of commits involved.")
    public Result<RebaseStatus> getRebaseStatus() {
        if (!repository.hasRepo) {
            return failure("Repository is not open");
        }

        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();

            boolean inProgress = repo.getRepositoryState().isRebasing();

            if (!inProgress) {
                return success(new RebaseStatus(false, 0, 0, List.of(), false));
            }

            List<RebaseCommit> commits = new ArrayList<>();
            boolean conflicted = repo.getRepositoryState().equals(org.eclipse.jgit.lib.RepositoryState.REBASING_MERGE);

            return success(new RebaseStatus(true, 0, 0, commits, conflicted));

        } catch (Exception e) {
            logger.error("Error getting rebase status", e);
            return failure("Error getting rebase status: " + e.getMessage());
        }
    }

    /**
     * Starts an interactive rebase.
     */
    @Tool(name = "git_rebase_start", description = "Starts an interactive rebase from the specified base commit. Use commit hashes, branch names, or relative references like HEAD~3. This allows you to modify commit history.")
    public Result<String> startInteractiveRebase(RebaseOptions options) {
        if (!repository.hasRepo) {
            return failure("Repository is not open");
        }

        if (options.baseCommit == null || options.baseCommit.trim().isEmpty()) {
            return failure("Base commit is required");
        }

        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();

            if (repo.getRepositoryState().isRebasing()) {
                return failure("A rebase is already in progress. Use git_rebase_continue, git_rebase_abort, or git_rebase_skip.");
            }

            ObjectId baseCommitId;
            try {
                baseCommitId = repo.resolve(options.baseCommit);
                if (baseCommitId == null) {
                    return failure("Could not resolve base commit: " + options.baseCommit);
                }
            } catch (IOException e) {
                return failure("Invalid base commit: " + options.baseCommit);
            }

            RebaseCommand rebaseCommand = git.rebase()
                    .setUpstream(baseCommitId)
                    .runInteractively(new org.eclipse.jgit.api.RebaseCommand.InteractiveHandler() {
                        @Override
                        public void prepareSteps(List<org.eclipse.jgit.lib.RebaseTodoLine> steps) {
                            // Default behavior - keep all steps as "pick"
                        }

                        @Override
                        public String modifyCommitMessage(String commit) {
                            return commit;
                        }
                    });

            if (options.preserve) {
                rebaseCommand.setPreserveMerges(true);
            }

            RebaseResult result = rebaseCommand.call();

            return switch (result.getStatus()) {
                case OK -> success("Interactive rebase completed successfully");
                case STOPPED -> success("Rebase stopped for editing. Use git_rebase_continue when ready.");
                case CONFLICTS -> success("Rebase stopped due to conflicts. Resolve conflicts and use git_rebase_continue.");
                case UNCOMMITTED_CHANGES -> failure("Cannot start rebase with uncommitted changes");
                case FAILED -> failure("Rebase failed: " + (result.getFailingPaths() != null ? 
                        String.join(", ", result.getFailingPaths().keySet()) : "Unknown error"));
                default -> failure("Rebase ended with status: " + result.getStatus());
            };

        } catch (GitAPIException e) {
            logger.error("Error starting interactive rebase", e);
            return failure("Error starting interactive rebase: " + e.getMessage());
        }
    }

    /**
     * Continues an interactive rebase after resolving conflicts or making edits.
     */
    @Tool(name = "git_rebase_continue", description = "Continues an interactive rebase after resolving conflicts or completing edits. Use this after staging resolved conflicts or making required changes.")
    public Result<String> continueRebase() {
        if (!repository.hasRepo) {
            return failure("Repository is not open");
        }

        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();

            if (!repo.getRepositoryState().isRebasing()) {
                return failure("No rebase in progress");
            }

            RebaseResult result = git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call();

            return switch (result.getStatus()) {
                case OK -> success("Rebase completed successfully");
                case STOPPED -> success("Rebase stopped for editing. Use git_rebase_continue when ready.");
                case CONFLICTS -> success("Rebase stopped due to conflicts. Resolve conflicts and use git_rebase_continue.");
                case FAILED -> failure("Rebase failed: " + (result.getFailingPaths() != null ? 
                        String.join(", ", result.getFailingPaths().keySet()) : "Unknown error"));
                default -> failure("Rebase ended with status: " + result.getStatus());
            };

        } catch (GitAPIException e) {
            logger.error("Error continuing rebase", e);
            return failure("Error continuing rebase: " + e.getMessage());
        }
    }

    /**
     * Skips the current commit in an interactive rebase.
     */
    @Tool(name = "git_rebase_skip", description = "Skips the current commit in an interactive rebase. Use this when you want to exclude the current commit from the rebase.")
    public Result<String> skipRebase() {
        if (!repository.hasRepo) {
            return failure("Repository is not open");
        }

        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();

            if (!repo.getRepositoryState().isRebasing()) {
                return failure("No rebase in progress");
            }

            RebaseResult result = git.rebase().setOperation(RebaseCommand.Operation.SKIP).call();

            return switch (result.getStatus()) {
                case OK -> success("Rebase completed successfully");
                case STOPPED -> success("Rebase stopped for editing. Use git_rebase_continue when ready.");
                case CONFLICTS -> success("Rebase stopped due to conflicts. Resolve conflicts and use git_rebase_continue.");
                case FAILED -> failure("Rebase failed: " + (result.getFailingPaths() != null ? 
                        String.join(", ", result.getFailingPaths().keySet()) : "Unknown error"));
                default -> failure("Rebase ended with status: " + result.getStatus());
            };

        } catch (GitAPIException e) {
            logger.error("Error skipping rebase", e);
            return failure("Error skipping rebase: " + e.getMessage());
        }
    }

    /**
     * Aborts an interactive rebase and returns to the original state.
     */
    @Tool(name = "git_rebase_abort", description = "Aborts an interactive rebase and returns the repository to its original state before the rebase started.")
    public Result<String> abortRebase() {
        if (!repository.hasRepo) {
            return failure("Repository is not open");
        }

        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();

            if (!repo.getRepositoryState().isRebasing()) {
                return failure("No rebase in progress");
            }

            RebaseResult result = git.rebase().setOperation(RebaseCommand.Operation.ABORT).call();

            return switch (result.getStatus()) {
                case ABORTED -> success("Rebase aborted successfully. Repository returned to original state.");
                case FAILED -> failure("Failed to abort rebase");
                default -> failure("Unexpected result when aborting rebase: " + result.getStatus());
            };

        } catch (GitAPIException e) {
            logger.error("Error aborting rebase", e);
            return failure("Error aborting rebase: " + e.getMessage());
        }
    }

    /**
     * Gets a list of commits that would be included in an interactive rebase.
     * Uses short format by default.
     */
    @Tool(name = "git_rebase_preview", description = "Previews the commits that would be included in an interactive rebase from the specified base commit. Use this to see what commits will be affected before starting the rebase. Uses short format by default.")
    public Result<List<RebaseCommit>> previewRebase(String baseCommit, int maxCount) {
        return previewRebase(baseCommit, maxCount, false, false);
    }

    /**
     * Gets a list of commits that would be included in an interactive rebase.
     * Display format is configurable.
     */
    public Result<List<RebaseCommit>> previewRebase(String baseCommit, int maxCount, boolean useFullCommitId, boolean useFullMessage) {
        if (!repository.hasRepo) {
            return failure("Repository is not open");
        }

        if (baseCommit == null || baseCommit.trim().isEmpty()) {
            return failure("Base commit is required");
        }

        if (maxCount < 0) {
            maxCount = 0;
        }

        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();

            ObjectId baseCommitId;
            try {
                baseCommitId = repo.resolve(baseCommit);
                if (baseCommitId == null) {
                    return failure("Could not resolve base commit: " + baseCommit);
                }
            } catch (IOException e) {
                return failure("Invalid base commit: " + baseCommit);
            }

            List<RebaseCommit> commits = new ArrayList<>();
            try (RevWalk revWalk = new RevWalk(repo)) {
                ObjectId headId = repo.resolve("HEAD");
                if (headId == null) {
                    return failure("Could not resolve HEAD");
                }

                RevCommit headCommit = revWalk.parseCommit(headId);
                RevCommit baseCommitObj = revWalk.parseCommit(baseCommitId);

                revWalk.markStart(headCommit);
                revWalk.markUninteresting(baseCommitObj);

                int count = 0;
                for (RevCommit commit : revWalk) {
                    if (maxCount > 0 && count >= maxCount) {
                        break;
                    }

                    String commitId = useFullCommitId ? commit.getId().name() : commit.getId().abbreviate(7).name();
                    String message = useFullMessage ? commit.getFullMessage() : commit.getShortMessage();

                    commits.add(new RebaseCommit(
                            commitId,
                            message,
                            commit.getAuthorIdent().getName(),
                            commit.getAuthorIdent().getEmailAddress(),
                            LocalDateTime.ofInstant(
                                    commit.getAuthorIdent().getWhenAsInstant(),
                                    ZoneId.systemDefault()
                            ),
                            "pick"
                    ));
                    count++;
                }
            }

            return success(commits);

        } catch (Exception e) {
            logger.error("Error previewing rebase", e);
            return failure("Error previewing rebase: " + e.getMessage());
        }
    }

    /**
     * Lists commits with numeric IDs for easy reference in rebase operations.
     * Uses short format by default.
     */
    @Tool(name = "git_list_commits", description = "Lists current commits with numeric IDs, commit information (commit ID, commit message, author) for easy reference in rebase operations. Uses short format by default.")
    public Result<List<NumberedCommit>> listCommits(String baseCommit, int maxCount) {
        return listCommits(baseCommit, maxCount, false, false);
    }

    /**
     * Lists commits with numeric IDs for easy reference in rebase operations.
     * Display format is configurable.
     */
    public Result<List<NumberedCommit>> listCommits(String baseCommit, int maxCount, boolean useFullCommitId, boolean useFullMessage) {
        if (!repository.hasRepo) {
            return failure("Repository is not open");
        }

        if (maxCount <= 0) {
            maxCount = 10; // Default to 10 commits
        }

        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();

            ObjectId baseCommitId = null;

            // If no base commit specified, we'll list from the beginning or use maxCount
            if (baseCommit != null && !baseCommit.trim().isEmpty()) {
                try {
                    baseCommitId = repo.resolve(baseCommit);
                    if (baseCommitId == null) {
                        return failure("Could not resolve base commit: " + baseCommit);
                    }
                } catch (IOException e) {
                    return failure("Invalid base commit: " + baseCommit);
                }
            }

            List<NumberedCommit> commits = new ArrayList<>();
            try (RevWalk revWalk = new RevWalk(repo)) {
                ObjectId headId = repo.resolve("HEAD");
                if (headId == null) {
                    return failure("Could not resolve HEAD");
                }

                RevCommit headCommit = revWalk.parseCommit(headId);
                revWalk.markStart(headCommit);

                // If base commit is specified, mark it as uninteresting
                if (baseCommitId != null) {
                    RevCommit baseCommitObj = revWalk.parseCommit(baseCommitId);
                    revWalk.markUninteresting(baseCommitObj);
                }

                int count = 0;
                int id = 1;
                for (RevCommit commit : revWalk) {
                    if (count >= maxCount) {
                        break;
                    }

                    String commitId = useFullCommitId ? commit.getId().name() : commit.getId().abbreviate(7).name();
                    String message = useFullMessage ? commit.getFullMessage() : commit.getShortMessage();

                    commits.add(new NumberedCommit(
                            id++,
                            commitId,
                            message,
                            commit.getAuthorIdent().getName()
                    ));
                    count++;
                }
            }

            return success(commits);

        } catch (Exception e) {
            logger.error("Error listing commits", e);
            return failure("Error listing commits: " + e.getMessage());
        }
    }

    /**
     * Performs a simplified interactive rebase based on instructions.
     */
    @Tool(name = "git_simple_rebase", description = "Performs an interactive rebase with simple instructions. Actions: 'pick' (keep), 'squash' (combine with previous), 'drop' (remove), 'reword' (change message), 'edit' (stop for editing), 'fixup' (like squash but discard message). Specify commit order and actions.")
    public Result<RebaseExecutionResult> performSimpleRebase(String baseCommit, List<RebaseInstruction> instructions) {
        if (!repository.hasRepo) {
            return failure("Repository is not open");
        }

        if (baseCommit == null || baseCommit.trim().isEmpty()) {
            return failure("Base commit is required");
        }

        if (instructions == null || instructions.isEmpty()) {
            return failure("Rebase instructions are required");
        }

        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();

            if (repo.getRepositoryState().isRebasing()) {
                return failure("A rebase is already in progress. Use git_rebase_continue, git_rebase_abort, or git_rebase_skip.");
            }

            // First, get the list of commits to validate instructions
            Result<List<NumberedCommit>> commitsResult = listCommits(baseCommit, 50, false, false);
            if (commitsResult instanceof Result.Failure<List<NumberedCommit>>(String message)) {
                return failure("Failed to get commits: " + message);
            }

            List<NumberedCommit> availableCommits = ((Result.Success<List<NumberedCommit>>) commitsResult).value();

            // Validate instructions
            for (RebaseInstruction instruction : instructions) {
                boolean found = availableCommits.stream()
                        .anyMatch(commit -> commit.id() == instruction.commitId());
                if (!found) {
                    return failure("Invalid commit ID: " + instruction.commitId());
                }

                String action = instruction.action().toLowerCase();
                if (!List.of("pick", "squash", "drop", "reword", "edit", "fixup").contains(action)) {
                    return failure("Invalid action: " + instruction.action() + ". Valid actions: pick, squash, drop, reword, edit, fixup");
                }
            }

            ObjectId baseCommitId;
            try {
                baseCommitId = repo.resolve(baseCommit);
                if (baseCommitId == null) {
                    return failure("Could not resolve base commit: " + baseCommit);
                }
            } catch (IOException e) {
                return failure("Invalid base commit: " + baseCommit);
            }

            // Create a custom interactive handler that applies our instructions
            RebaseCommand rebaseCommand = git.rebase()
                    .setUpstream(baseCommitId)
                    .runInteractively(new org.eclipse.jgit.api.RebaseCommand.InteractiveHandler() {
                        @Override
                        public void prepareSteps(List<org.eclipse.jgit.lib.RebaseTodoLine> steps) {
                            // Map our instructions to JGit's rebase steps
                            for (org.eclipse.jgit.lib.RebaseTodoLine step : steps) {
                                // Find matching instruction by commit ID
                                String commitId = step.getCommit().name();
                                RebaseInstruction matchingInstruction = null;

                                for (RebaseInstruction instruction : instructions) {
                                    NumberedCommit numberedCommit = availableCommits.stream()
                                            .filter(c -> c.id() == instruction.commitId())
                                            .findFirst()
                                            .orElse(null);

                                    if (numberedCommit != null && numberedCommit.commitId().equals(commitId)) {
                                        matchingInstruction = instruction;
                                        break;
                                    }
                                }

                                if (matchingInstruction != null) {
                                    org.eclipse.jgit.lib.RebaseTodoLine.Action action = switch (matchingInstruction.action().toLowerCase()) {
                                        case "pick" -> org.eclipse.jgit.lib.RebaseTodoLine.Action.PICK;
                                        case "squash" -> org.eclipse.jgit.lib.RebaseTodoLine.Action.SQUASH;
                                        case "drop" ->
                                                org.eclipse.jgit.lib.RebaseTodoLine.Action.COMMENT; // JGit doesn't have DROP, use COMMENT to skip
                                        case "reword" -> org.eclipse.jgit.lib.RebaseTodoLine.Action.REWORD;
                                        case "edit" -> org.eclipse.jgit.lib.RebaseTodoLine.Action.EDIT;
                                        case "fixup" -> org.eclipse.jgit.lib.RebaseTodoLine.Action.FIXUP;
                                        default -> org.eclipse.jgit.lib.RebaseTodoLine.Action.PICK;
                                    };

                                    try {
                                        step.setAction(action);
                                    } catch (org.eclipse.jgit.errors.IllegalTodoFileModification e) {
                                        logger.warn("Could not set action {} for commit {}: {}", action, commitId, e.getMessage());
                                    }
                                }
                            }
                        }

                        @Override
                        public String modifyCommitMessage(String commit) {
                            // Find if there's a reword instruction for this commit
                            for (RebaseInstruction instruction : instructions) {
                                if ("reword".equalsIgnoreCase(instruction.action()) &&
                                    instruction.newMessage() != null && !instruction.newMessage().trim().isEmpty()) {
                                    return instruction.newMessage();
                                }
                            }
                            return commit;
                        }
                    });

            RebaseResult result = rebaseCommand.call();

            return switch (result.getStatus()) {
                case OK -> success(new RebaseExecutionResult(true, false, "Rebase completed successfully", List.of()));
                case STOPPED -> success(new RebaseExecutionResult(false, false, "Rebase stopped for editing. Use git_rebase_continue when ready.", List.of()));
                case CONFLICTS -> {
                    List<String> conflictedFiles = result.getConflicts() != null ? 
                            new ArrayList<>(result.getConflicts()) : List.of();
                    yield success(new RebaseExecutionResult(false, true, 
                            "Rebase stopped due to conflicts. Resolve conflicts and use git_rebase_continue.", 
                            conflictedFiles));
                }
                case UNCOMMITTED_CHANGES -> failure("Cannot start rebase with uncommitted changes");
                case FAILED -> failure("Rebase failed: " + (result.getFailingPaths() != null ? 
                        String.join(", ", result.getFailingPaths().keySet()) : "Unknown error"));
                default -> failure("Rebase ended with status: " + result.getStatus());
            };

        } catch (GitAPIException e) {
            logger.error("Error performing simple rebase", e);
            return failure("Error performing simple rebase: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during simple rebase", e);
            return failure("Unexpected error during simple rebase: " + e.getMessage());
        }
    }
}
