package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.model.*;
import com.github.asm0dey.git_mcp_spring.model.GitResponse.*;
import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.Git;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for Git bisect operations.
 * Provides methods for finding the commit that introduced a bug using binary search.
 * This service is stateless - all state is passed through method parameters and return values.
 */
@Service
public class GitBisectService {
    private static final Logger logger = LoggerFactory.getLogger(GitBisectService.class);

    private final GitRepositoryResource repository;

    public GitBisectService(GitRepositoryResource repository) {
        this.repository = repository;
    }

    /**
     * Starts a git bisect session.
     *
     * @param goodCommit A commit known to be good (without the bug)
     * @param badCommit A commit known to be bad (with the bug)
     * @return Result of the operation with the first commit to test and bisect state
     */
    @Tool(name = "git_bisect_start", description = "Starts a git bisect session to find the commit that introduced a bug")
    public GitResponse bisectStart(String goodCommit, String badCommit) {
        logger.debug("Starting bisect with good: {} and bad: {}", goodCommit, badCommit);

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Resolve commit references
            ObjectId goodCommitId = repo.resolve(goodCommit);
            ObjectId badCommitId = repo.resolve(badCommit);

            if (goodCommitId == null) {
                logger.error("Invalid good commit reference: {}", goodCommit);
                return new ErrorResponse("Invalid good commit reference: " + goodCommit);
            }

            if (badCommitId == null) {
                logger.error("Invalid bad commit reference: {}", badCommit);
                return new ErrorResponse("Invalid bad commit reference: " + badCommit);
            }

            // Get the commit history between good and bad
            List<RevCommit> commitsBetween = getCommitsBetween(repo, goodCommitId, badCommitId);

            if (commitsBetween.isEmpty()) {
                logger.error("No commits found between good and bad references");
                return new ErrorResponse("No commits found between good and bad references");
            }

            // Create bisect state
            BisectState bisectState = BisectState.create(
                goodCommitId.getName(),
                badCommitId.getName(),
                serializeCommitList(commitsBetween)
            );

            // Checkout the middle commit
            RevCommit midCommit = commitsBetween.get(commitsBetween.size() / 2);
            git.checkout().setName(midCommit.getName()).call();

            // Convert RevCommit to CommitInfo
            CommitInfo commitInfo = commitToCommitInfo(midCommit);

            // Create BisectData
            BisectData data = BisectData.started(
                commitInfo,
                commitsBetween.size(),
                bisectState
            );

            return new BisectResponse(data.status(), data.message(), data);
        } catch (GitAPIException | IOException e) {
            logger.error("Error starting bisect: {}", e.getMessage(), e);
            return new ErrorResponse("Error starting bisect: " + e.getMessage());
        }
    }

    /**
     * Marks the current commit as good in a bisect session.
     *
     * @param state The current state of the bisect session
     * @return Result of the operation with the next commit to test
     */
    @Tool(name = "git_bisect_good", description = "Marks the current commit as good in a bisect session")
    public GitResponse bisectGood(BisectState state) {
        logger.debug("Marking current commit as good in bisect");

        if (state == null) {
            logger.error("No bisect session in progress");
            return new ErrorResponse("No bisect session in progress. Start a bisect session first.");
        }

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Get current commit
            ObjectId currentCommitId = repo.resolve("HEAD");
            String currentCommit = currentCommitId.getName();

            // Update bisect state with the current commit marked as good
            BisectState updatedState = state.withGoodCommit(currentCommit);

            // Deserialize the remaining commits
            List<RevCommit> remainingCommits = deserializeCommitList(repo, updatedState.remainingCommits());

            if (remainingCommits.isEmpty() || updatedState.isComplete()) {
                // Bisect complete - the first bad commit is the one marked as bad
                CommitInfo firstBadCommit = getCommitDetails(repo, updatedState.badCommit());

                // Create result
                BisectData data = BisectData.complete(firstBadCommit);

                return new BisectResponse(data.status(), data.message(), data);
            }

            // Checkout the middle commit
            RevCommit midCommit = remainingCommits.get(remainingCommits.size() / 2);
            git.checkout().setName(midCommit.getName()).call();

            // Convert RevCommit to CommitInfo
            CommitInfo commitInfo = commitToCommitInfo(midCommit);

            // Create BisectData for in-progress state
            BisectData data = BisectData.inProgress(
                commitInfo,
                remainingCommits.size(),
                updatedState,
                true // marked as good
            );

            return new BisectResponse(data.status(), data.message(), data);
        } catch (GitAPIException | IOException e) {
            logger.error("Error in bisect good: {}", e.getMessage(), e);
            return new ErrorResponse("Error in bisect good: " + e.getMessage());
        }
    }

    /**
     * Marks the current commit as bad in a bisect session.
     *
     * @param state The current state of the bisect session
     * @return Result of the operation with the next commit to test
     */
    @Tool(name = "git_bisect_bad", description = "Marks the current commit as bad in a bisect session")
    public GitResponse bisectBad(BisectState state) {
        logger.debug("Marking current commit as bad in bisect");

        if (state == null) {
            logger.error("No bisect session in progress");
            return new ErrorResponse("No bisect session in progress. Start a bisect session first.");
        }

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Get current commit
            ObjectId currentCommitId = repo.resolve("HEAD");
            String currentCommit = currentCommitId.getName();

            // Update bisect state with the current commit marked as bad
            BisectState updatedState = state.withBadCommit(currentCommit);

            // Deserialize the remaining commits
            List<RevCommit> remainingCommits = deserializeCommitList(repo, updatedState.remainingCommits());

            if (remainingCommits.isEmpty() || updatedState.isComplete()) {
                // Bisect complete - the first bad commit is the current one
                CommitInfo firstBadCommit = getCommitDetails(repo, currentCommit);

                // Create result
                BisectData data = BisectData.complete(firstBadCommit);

                return new BisectResponse(data.status(), data.message(), data);
            }

            // Checkout the middle commit
            RevCommit midCommit = remainingCommits.get(remainingCommits.size() / 2);
            git.checkout().setName(midCommit.getName()).call();

            // Convert RevCommit to CommitInfo
            CommitInfo commitInfo = commitToCommitInfo(midCommit);

            // Create BisectData for in-progress state
            BisectData data = BisectData.inProgress(
                commitInfo,
                remainingCommits.size(),
                updatedState,
                false // marked as bad
            );

            return new BisectResponse(data.status(), data.message(), data);
        } catch (GitAPIException | IOException e) {
            logger.error("Error in bisect bad: {}", e.getMessage(), e);
            return new ErrorResponse("Error in bisect bad: " + e.getMessage());
        }
    }

    /**
     * Resets the bisect session and returns to the original branch.
     *
     * @param state The current state of the bisect session (can be null)
     * @return Result of the operation
     */
    @Tool(name = "git_bisect_reset", description = "Resets the bisect session and returns to the original branch")
    public GitResponse bisectReset(BisectState state) {
        logger.debug("Resetting bisect session");

        if (state == null) {
            logger.warn("No bisect session in progress");
            String message = "No bisect session was in progress.";

            // Create BisectData for reset state
            BisectData data = BisectData.reset(message);

            return new BisectResponse(data.status(), data.message(), data);
        }

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ErrorResponse("Repository is not open");
            }

            // Return to the original branch (assuming master/main for simplicity)
            git.checkout().setName("master").call();

            // Create BisectData for reset state
            String message = "Bisect session reset. Returned to original branch.";
            BisectData data = BisectData.reset(message);

            return new BisectResponse(data.status(), data.message(), data);
        } catch (GitAPIException e) {
            logger.error("Error resetting bisect: {}", e.getMessage(), e);
            return new ErrorResponse("Error resetting bisect: " + e.getMessage());
        }
    }

    // Helper methods
    private List<RevCommit> getCommitsBetween(Repository repo, ObjectId goodCommitId, ObjectId badCommitId) throws IOException, GitAPIException {
        List<RevCommit> result = new ArrayList<>();

        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit goodCommit = walk.parseCommit(goodCommitId);
            RevCommit badCommit = walk.parseCommit(badCommitId);

            walk.markStart(badCommit);
            walk.markUninteresting(goodCommit);

            for (RevCommit commit : walk) {
                result.add(commit);
            }
        }

        return result;
    }

    private CommitInfo commitToCommitInfo(RevCommit commit) {
        return new CommitInfo(
            commit.getId().getName(),
            commit.getId().abbreviate(7).name(),
            commit.getShortMessage(),
            commit.getAuthorIdent().getName(),
            commit.getAuthorIdent().getEmailAddress(),
            new Date(commit.getCommitTime() * 1000L)
        );
    }

    private CommitInfo getCommitDetails(Repository repo, String commitId) throws IOException {
        try (RevWalk walk = new RevWalk(repo)) {
            ObjectId objectId = repo.resolve(commitId);
            RevCommit commit = walk.parseCommit(objectId);
            return commitToCommitInfo(commit);
        }
    }

    /**
     * Serializes a list of RevCommit objects to a list of commit IDs.
     * This is needed because RevCommit objects are not serializable and can't be stored in the response.
     *
     * @param commits List of RevCommit objects
     * @return List of commit IDs as strings
     */
    private List<String> serializeCommitList(List<RevCommit> commits) {
        return commits.stream()
                .map(commit -> commit.getId().getName())
                .collect(Collectors.toList());
    }

    /**
     * Deserializes a list of commit IDs back to RevCommit objects.
     *
     * @param repo Repository to resolve commits from
     * @param commitIds List of commit IDs as strings
     * @return List of RevCommit objects
     * @throws IOException If a commit can't be resolved
     */
    private List<RevCommit> deserializeCommitList(Repository repo, List<String> commitIds) throws IOException {
        List<RevCommit> result = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repo)) {
            for (String commitId : commitIds) {
                ObjectId objectId = repo.resolve(commitId);
                if (objectId != null) {
                    RevCommit commit = walk.parseCommit(objectId);
                    result.add(commit);
                }
            }
        }
        return result;
    }
}
