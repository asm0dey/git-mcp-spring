package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for Git commit operations.
 * Provides methods for commit preparation, creation, and amending.
 */
@Service
public class GitCommitOperationsService {
    private static final Logger logger = LoggerFactory.getLogger(GitCommitOperationsService.class);

    private final GitRepositoryResource repository;


    public GitCommitOperationsService(GitRepositoryResource repository) {
        this.repository = repository;
    }

    /**
     * Checks if a commit can be made.
     *
     * @return Map containing commit preparation information
     */
    @Tool(name = "git_commit_prepare", description = "Checks if a commit can be made")
    public Map<String, Object> prepareCommit() {
        logger.debug("Preparing commit for repository: {}", repository.getPath());

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                return result;
            }

            // Get repository status
            Status status = git.status().call();

            // Check if there are changes to commit
            boolean hasChanges = !status.getAdded().isEmpty() ||
                                 !status.getChanged().isEmpty() ||
                                 !status.getRemoved().isEmpty();

            result.put("canCommit", hasChanges);

            if (!hasChanges) {
                result.put("message", "No changes staged for commit");
            } else {
                // Add staged files information
                List<String> stagedFiles = new ArrayList<>();
                stagedFiles.addAll(status.getAdded());
                stagedFiles.addAll(status.getChanged());
                stagedFiles.addAll(status.getRemoved());
                result.put("stagedFiles", stagedFiles);
                result.put("stagedCount", stagedFiles.size());
            }

            // Add user information
            String userName = repository.getConfigValue("user", "name");
            String userEmail = repository.getConfigValue("user", "email");

            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("name", userName);
            userInfo.put("email", userEmail);
            result.put("userInfo", userInfo);

            // Check if user information is configured
            boolean hasUserInfo = userName != null && !userName.isEmpty() &&
                                  userEmail != null && !userEmail.isEmpty();
            result.put("hasUserInfo", hasUserInfo);

            if (!hasUserInfo) {
                result.put("warning", "User name and email not configured");
            }

            return result;
        } catch (GitAPIException e) {
            logger.error("Error preparing commit: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Creates a new commit with the specified message.
     *
     * @param message     Commit message
     * @param authorName  Author name (optional, uses config if null)
     * @param authorEmail Author email (optional, uses config if null)
     * @return Map containing commit result
     */
    @Tool(name = "git_commit_create", description = "Creates a new commit with the specified message")
    public Map<String, Object> createCommit(String message, String authorName, String authorEmail) {
        logger.debug("Creating commit with message: {}", message);

        Map<String, Object> result = new HashMap<>();

        if (message == null || message.trim().isEmpty()) {
            result.put("error", "Commit message cannot be empty");
            result.put("success", false);
            return result;
        }

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Check if there are changes to commit
            Status status = git.status().call();
            boolean hasChanges = !status.getAdded().isEmpty() ||
                                 !status.getChanged().isEmpty() ||
                                 !status.getRemoved().isEmpty();

            if (!hasChanges) {
                result.put("error", "No changes staged for commit");
                result.put("success", false);
                return result;
            }

            // Get user information from config if not provided
            if (authorName == null || authorName.trim().isEmpty()) {
                authorName = repository.getConfigValue("user", "name");
            }

            if (authorEmail == null || authorEmail.trim().isEmpty()) {
                authorEmail = repository.getConfigValue("user", "email");
            }

            if (authorName == null || authorName.trim().isEmpty() ||
                authorEmail == null || authorEmail.trim().isEmpty()) {
                result.put("error", "Author name and email must be configured");
                result.put("success", false);
                return result;
            }

            // Create the commit
            CommitCommand commitCommand = git.commit()
                    .setMessage(message)
                    .setAuthor(authorName, authorEmail);

            RevCommit commit = commitCommand.call();

            // Build the result
            result.put("success", true);
            result.put("commitId", commit.getName());
            result.put("shortId", commit.getName().substring(0, 7));
            result.put("message", commit.getShortMessage());

            // Add author information
            PersonIdent authorIdent = commit.getAuthorIdent();
            Map<String, Object> author = new HashMap<>();
            author.put("name", authorIdent.getName());
            author.put("email", authorIdent.getEmailAddress());
            author.put("time", authorIdent.getWhen().getTime());
            result.put("author", author);

            logger.info("Created commit: {} - {}", commit.getName().substring(0, 7), commit.getShortMessage());

            return result;
        } catch (GitAPIException e) {
            logger.error("Error creating commit: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Amends the last commit with new changes and/or a new message.
     *
     * @param message     New commit message (optional, uses previous message if null)
     * @param authorName  Author name (optional, uses config if null)
     * @param authorEmail Author email (optional, uses config if null)
     * @return Map containing amend result
     */
    @Tool(name = "git_commit_amend", description = "Amends the last commit with new changes and/or a new message")
    public Map<String, Object> amendCommit(String message, String authorName, String authorEmail) {
        logger.debug("Amending last commit");

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Get the last commit
            RevCommit lastCommit = null;
            try {
                lastCommit = git.log().setMaxCount(1).call().iterator().next();
            } catch (Exception e) {
                result.put("error", "No commits found to amend");
                result.put("success", false);
                return result;
            }

            // Use the previous message if a new one is not provided
            if (message == null || message.trim().isEmpty()) {
                message = lastCommit.getFullMessage();
            }

            // Get user information from config if not provided
            if (authorName == null || authorName.trim().isEmpty()) {
                authorName = repository.getConfigValue("user", "name");
            }

            if (authorEmail == null || authorEmail.trim().isEmpty()) {
                authorEmail = repository.getConfigValue("user", "email");
            }

            if (authorName == null || authorName.trim().isEmpty() ||
                authorEmail == null || authorEmail.trim().isEmpty()) {
                result.put("error", "Author name and email must be configured");
                result.put("success", false);
                return result;
            }

            // Amend the commit
            CommitCommand commitCommand = git.commit()
                    .setMessage(message)
                    .setAuthor(authorName, authorEmail)
                    .setAmend(true);

            RevCommit amendedCommit = commitCommand.call();

            // Build the result
            result.put("success", true);
            result.put("commitId", amendedCommit.getName());
            result.put("shortId", amendedCommit.getName().substring(0, 7));
            result.put("message", amendedCommit.getShortMessage());
            result.put("previousCommitId", lastCommit.getName());

            // Add author information
            PersonIdent authorIdent = amendedCommit.getAuthorIdent();
            Map<String, Object> author = new HashMap<>();
            author.put("name", authorIdent.getName());
            author.put("email", authorIdent.getEmailAddress());
            author.put("time", authorIdent.getWhen().getTime());
            result.put("author", author);

            logger.info("Amended commit: {} - {}", amendedCommit.getName().substring(0, 7), amendedCommit.getShortMessage());

            return result;
        } catch (GitAPIException e) {
            logger.error("Error amending commit: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }
}