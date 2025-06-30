package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service for Git commit operations.
 * Provides methods for commit querying and analysis.
 */
@Service
public class GitCommitService {
    private static final Logger logger = LoggerFactory.getLogger(GitCommitService.class);

    private final GitRepositoryResource repository;

    /**
     * Creates a new Git commit service.
     * 
     * @param repository Git repository resource
     */
    public GitCommitService(GitRepositoryResource repository) {
        this.repository = repository;
    }

    /**
     * Searches for commits by message.
     * 
     * @param searchTerm Search term to look for in commit messages
     * @param maxCount Maximum number of commits to return (0 for unlimited)
     * @return List of commits matching the search term
     */
    @Tool(name = "git_commit_search", description = "Search commits by message")
    public List<Map<String, Object>> searchCommitsByMessage(String searchTerm, int maxCount) {
        logger.debug("Searching for commits with message containing: {}", searchTerm);

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ArrayList<>();
            }

            // Create a pattern for case-insensitive search
            Pattern pattern = Pattern.compile(Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);

            // Set up the log command
            LogCommand logCommand = git.log();
            if (maxCount > 0) {
                logCommand.setMaxCount(maxCount);
            }

            // Execute the log command and filter commits by message
            Iterable<RevCommit> commits = logCommand.call();

            // Convert matching commits to maps
            return StreamSupport.stream(commits.spliterator(), false)
                    .filter(commit -> pattern.matcher(commit.getFullMessage()).find())
                    .map(this::commitToMap)
                    .toList();
        } catch (GitAPIException e) {
            logger.error("Error searching commits: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets a commit by ID.
     * 
     * @param commitId Commit ID (SHA-1 hash)
     * @return Commit details or null if not found
     */
    @Tool(name = "git_commit_get", description = "Get a commit by ID")
    public Map<String, Object> getCommit(String commitId) {
        logger.debug("Getting commit: {}", commitId);

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return null;
            }

            // Parse the commit ID
            ObjectId objectId = repository.getRepository().resolve(commitId);
            if (objectId == null) {
                logger.error("Invalid commit ID: {}", commitId);
                return null;
            }

            // Get the commit
            RevCommit commit = git.log().add(objectId).setMaxCount(1).call().iterator().next();
            return commitToMap(commit);
        } catch (IOException | GitAPIException e) {
            logger.error("Error getting commit: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets the commit history.
     * 
     * @param maxCount Maximum number of commits to return (0 for unlimited)
     * @return List of commits
     */
    @Tool(name = "git_commit_history", description = "Get the commit history")
    public List<Map<String, Object>> getCommitHistory(int maxCount) {
        logger.debug("Getting commit history, max count: {}", maxCount);

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ArrayList<>();
            }

            // Set up the log command
            LogCommand logCommand = git.log();
            if (maxCount > 0) {
                logCommand.setMaxCount(maxCount);
            }

            // Execute the log command
            Iterable<RevCommit> commits = logCommand.call();

            // Convert commits to maps
            return StreamSupport.stream(commits.spliterator(), false)
                    .map(this::commitToMap)
                    .collect(Collectors.toList());
        } catch (GitAPIException e) {
            logger.error("Error getting commit history: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Analyzes changes in a commit.
     * 
     * @param commitId Commit ID (SHA-1 hash)
     * @return Map containing change analysis information
     */
    @Tool(name = "git_commit_changes", description = "Analyzes changes in a commit")
    public Map<String, Object> analyzeCommitChanges(String commitId) {
        logger.debug("Analyzing changes in commit: {}", commitId);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                return result;
            }

            // Parse the commit ID
            ObjectId objectId = repository.getRepository().resolve(commitId);
            if (objectId == null) {
                logger.error("Invalid commit ID: {}", commitId);
                result.put("error", "Invalid commit ID");
                return result;
            }

            // Get the commit
            RevCommit commit = git.log().add(objectId).setMaxCount(1).call().iterator().next();

            // Get the parent commit
            if (commit.getParentCount() == 0) {
                // This is the first commit
                result.put("isInitialCommit", true);
                result.put("filesChanged", 0);
                result.put("insertions", 0);
                result.put("deletions", 0);
                return result;
            }

            RevCommit parentCommit = commit.getParent(0);

            // Get the diff between the commit and its parent
            List<Map<String, Object>> fileChanges = new ArrayList<>();

            // Use DiffFormatter to get detailed diff information
            try (org.eclipse.jgit.diff.DiffFormatter diffFormatter = new org.eclipse.jgit.diff.DiffFormatter(
                    org.eclipse.jgit.util.io.NullOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                diffFormatter.setDetectRenames(true);

                // Get the diff entries
                List<org.eclipse.jgit.diff.DiffEntry> diffs = diffFormatter.scan(
                        parentCommit.getTree(), 
                        commit.getTree());

                // Process each diff entry
                for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                    Map<String, Object> fileChange = new HashMap<>();
                    fileChange.put("path", diff.getNewPath());
                    fileChange.put("changeType", diff.getChangeType().name());

                    // Get detailed edit list to count insertions and deletions
                    org.eclipse.jgit.diff.EditList edits = diffFormatter.toFileHeader(diff).toEditList();
                    int insertions = 0;
                    int deletions = 0;

                    for (org.eclipse.jgit.diff.Edit edit : edits) {
                        insertions += edit.getLengthB();
                        deletions += edit.getLengthA();
                    }

                    fileChange.put("insertions", insertions);
                    fileChange.put("deletions", deletions);
                    fileChanges.add(fileChange);
                }
            }

            // Calculate summary statistics
            int filesChanged = fileChanges.size();
            int insertions = fileChanges.stream().mapToInt(change -> (int) change.getOrDefault("insertions", 0)).sum();
            int deletions = fileChanges.stream().mapToInt(change -> (int) change.getOrDefault("deletions", 0)).sum();

            // Build the result
            result.put("isInitialCommit", false);
            result.put("filesChanged", filesChanged);
            result.put("insertions", insertions);
            result.put("deletions", deletions);
            result.put("fileChanges", fileChanges);

            return result;
        } catch (IOException | GitAPIException e) {
            logger.error("Error analyzing commit changes: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Prepares a tree parser for a commit.
     * 
     * @param git Git instance
     * @param commitId Commit ID
     * @return Tree parser
     * @throws IOException If an I/O error occurs
     */
    private org.eclipse.jgit.treewalk.AbstractTreeIterator prepareTreeParser(Git git, String commitId) throws IOException {
        org.eclipse.jgit.treewalk.CanonicalTreeParser treeParser = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
        try (org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader()) {
            treeParser.reset(reader, git.getRepository().resolve(commitId + "^{tree}"));
        }
        return treeParser;
    }

    /**
     * Converts a JGit RevCommit to a map representation.
     * 
     * @param commit JGit RevCommit
     * @return Map representation of the commit
     */
    private Map<String, Object> commitToMap(RevCommit commit) {
        Map<String, Object> result = new HashMap<>();

        // Basic commit information
        result.put("id", commit.getName());
        result.put("shortId", commit.getName().substring(0, 7));
        result.put("message", commit.getShortMessage());
        result.put("fullMessage", commit.getFullMessage());
        result.put("time", commit.getCommitTime() * 1000L); // Convert to milliseconds

        // Author information
        PersonIdent authorIdent = commit.getAuthorIdent();
        Map<String, Object> author = new HashMap<>();
        author.put("name", authorIdent.getName());
        author.put("email", authorIdent.getEmailAddress());
        author.put("time", authorIdent.getWhen().getTime());
        result.put("author", author);

        // Committer information
        PersonIdent committerIdent = commit.getCommitterIdent();
        Map<String, Object> committer = new HashMap<>();
        committer.put("name", committerIdent.getName());
        committer.put("email", committerIdent.getEmailAddress());
        committer.put("time", committerIdent.getWhen().getTime());
        result.put("committer", committer);

        // Parent commits
        List<String> parents = new ArrayList<>();
        for (RevCommit parent : commit.getParents()) {
            parents.add(parent.getName());
        }
        result.put("parents", parents);

        return result;
    }
}
