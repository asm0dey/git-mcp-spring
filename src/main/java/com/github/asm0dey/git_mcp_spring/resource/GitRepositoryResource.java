package com.github.asm0dey.git_mcp_spring.resource;

import com.github.asm0dey.git_mcp_spring.config.GitRepositoryHolder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.github.asm0dey.git_mcp_spring.model.GitResponse;
import com.github.asm0dey.git_mcp_spring.model.GitResponse.RepositoryStatusResponse;

/**
 * Represents a Git repository as a resource in the MCP protocol.
 * Provides methods to interact with the repository.
 */
public class GitRepositoryResource {
    private static final Logger logger = LoggerFactory.getLogger(GitRepositoryResource.class);

    private final String name;
    private final Path path;
    private Repository repository;
    private Git git;
    private GitRepositoryHolder config;

    /**
     * Creates a new Git repository resource.
     * 
     * @param name Repository name
     * @param path Path to the repository
     */
    public GitRepositoryResource(String name, Path path) {
        this.name = name;
        this.path = path;
    }

    /**
     * Opens the repository.
     * 
     * @return True if the repository was opened successfully, false otherwise
     */
    public boolean open() {
        try {
            File gitDir = new File(path.toFile(), ".git");
            if (!gitDir.exists()) {
                gitDir = path.toFile(); // Bare repository
            }

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repository = builder.setGitDir(gitDir)
                    .readEnvironment() // Scan environment GIT_* variables
                    .findGitDir()      // Scan up the file system tree
                    .build();

            git = new Git(repository);
            config = new GitRepositoryHolder(repository);
            logger.info("Opened Git repository: {}", path);
            return true;
        } catch (IOException e) {
            logger.error("Failed to open Git repository: {}", path, e);
            return false;
        }
    }

    /**
     * Initializes a new repository at the specified path.
     * 
     * @param bare Whether to create a bare repository
     * @return True if the repository was initialized successfully, false otherwise
     */
    public boolean init(boolean bare) {
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(path.getParent());

            // Initialize the repository
            git = Git.init()
                    .setDirectory(path.toFile())
                    .setBare(bare)
                    .call();

            repository = git.getRepository();
            config = new GitRepositoryHolder(repository);
            logger.info("Initialized Git repository: {}", path);
            return true;
        } catch (IOException | GitAPIException e) {
            logger.error("Failed to initialize Git repository: {}", path, e);
            return false;
        }
    }

    /**
     * Closes the repository.
     */
    public void close() {
        if (git != null) {
            git.close();
        }
        if (repository != null) {
            repository.close();
        }
        logger.debug("Closed Git repository: {}", path);
    }

    /**
     * Gets the repository status.
     * 
     * @return Repository status information as a GitResponse
     */
    public GitResponse getStatus() {
        try {
            if (repository == null || !repository.getObjectDatabase().exists()) {
                return new RepositoryStatusResponse(
                    false, false, true, null, 
                    true, false, false, false, 
                    false, false, false, false
                );
            }

            boolean exists = true;
            boolean bare = repository.isBare();
            boolean empty = repository.getRefDatabase().getRefs().isEmpty();

            // Get current branch
            String branch = null;
            try {
                branch = repository.getBranch();
            } catch (IOException e) {
                logger.warn("Could not get current branch", e);
            }

            // Default values for working directory status
            boolean clean = true;
            boolean modified = false;
            boolean untracked = false;
            boolean added = false;
            boolean changed = false;
            boolean removed = false;
            boolean missing = false;
            boolean conflicting = false;

            // Get working directory status
            if (!repository.isBare()) {
                org.eclipse.jgit.api.Status gitStatus = git.status().call();
                clean = gitStatus.isClean();
                modified = !gitStatus.getModified().isEmpty();
                untracked = !gitStatus.getUntracked().isEmpty();
                added = !gitStatus.getAdded().isEmpty();
                changed = !gitStatus.getChanged().isEmpty();
                removed = !gitStatus.getRemoved().isEmpty();
                missing = !gitStatus.getMissing().isEmpty();
                conflicting = !gitStatus.getConflicting().isEmpty();
            }

            return new RepositoryStatusResponse(
                exists, bare, empty, branch, 
                clean, modified, untracked, added, 
                changed, removed, missing, conflicting
            );
        } catch (Exception e) {
            logger.error("Error getting repository status: {}", path, e);
            return new GitResponse.ErrorResponse("Error getting repository status: " + e.getMessage());
        }
    }

    /**
     * Gets the repository name.
     * 
     * @return Repository name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the repository path.
     * 
     * @return Repository path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Gets the JGit repository object.
     * 
     * @return JGit repository
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * Gets the JGit Git object.
     * 
     * @return JGit Git
     */
    public Git getGit() {
        return git;
    }

    /**
     * Gets the repository configuration.
     * 
     * @return Repository configuration
     */
    public GitRepositoryHolder getConfig() {
        return config;
    }

    /**
     * Gets a configuration value.
     * 
     * @param section Configuration section
     * @param name Configuration name
     * @return Configuration value or null if not found
     */
    public String getConfigValue(String section, String name) {
        if (config == null) {
            return null;
        }
        return config.getString(section, name);
    }

    /**
     * Gets a configuration value.
     * 
     * @param section Configuration section
     * @param subsection Configuration subsection
     * @param name Configuration name
     * @return Configuration value or null if not found
     */
    public String getConfigValue(String section, String subsection, String name) {
        if (config == null) {
            return null;
        }
        return config.getString(section, subsection, name);
    }

    /**
     * Sets a configuration value.
     * 
     * @param section Configuration section
     * @param name Configuration name
     * @param value Configuration value
     * @return True if the value was set and saved successfully, false otherwise
     */
    public boolean setConfigValue(String section, String name, String value) {
        if (config == null) {
            return false;
        }
        config.setString(section, name, value);
        return config.save();
    }

    /**
     * Sets a configuration value.
     * 
     * @param section Configuration section
     * @param subsection Configuration subsection
     * @param name Configuration name
     * @param value Configuration value
     * @return True if the value was set and saved successfully, false otherwise
     */
    public boolean setConfigValue(String section, String subsection, String name, String value) {
        if (config == null) {
            return false;
        }
        config.setString(section, subsection, name, value);
        return config.save();
    }

}
