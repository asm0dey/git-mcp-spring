package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.config.GitRepositoryHolder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Represents a Git repository as a resource in the MCP protocol.
 * Provides methods to interact with the repository.
 */
@Service
public class GitRepositoryService {
    private static final Logger logger = LoggerFactory.getLogger(GitRepositoryService.class);

    private Repository repository;
    private Git git;
    private GitRepositoryHolder config;
    boolean hasRepo = false;


    /**
     * Opens the repository.
     *
     * @return True if the repository was opened successfully, false otherwise
     */
    @Tool(name = "git_open", description = "Opens the repository")
    public boolean open(String path) {
        close();

        try {
            File gitDir = new File(path, ".git");
            if (!gitDir.exists()) gitDir = new File(path); // Bare repository

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repository = builder.setGitDir(gitDir)
                    .readEnvironment() // Scan environment GIT_* variables
                    .findGitDir()      // Scan up the file system tree
                    .build();

            git = new Git(repository);
            config = new GitRepositoryHolder(repository);
            logger.info("Opened Git repository: {}", path);
            hasRepo = true;
            return true;
        } catch (IOException e) {
            logger.error("Failed to open Git repository: {}", path, e);
            hasRepo = false;
            return false;
        }
    }

    @Tool(name = "git_close", description = "Closes the repository")
    public void close() {
        if (git != null) {
            git.close();
            var path = git.getRepository().getDirectory().toPath();
            logger.debug("Closed Git repository: {}", path);
        }
        if (repository != null) repository.close();

        hasRepo = false;
    }


    /**
     * Gets the repository path.
     *
     * @return Repository path
     */
    public Path getPath() {
        return git.getRepository().getDirectory().toPath();
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
     * @param name    Configuration name
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
     * @param section    Configuration section
     * @param subsection Configuration subsection
     * @param name       Configuration name
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
     * @param name    Configuration name
     * @param value   Configuration value
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
     * @param section    Configuration section
     * @param subsection Configuration subsection
     * @param name       Configuration name
     * @param value      Configuration value
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
