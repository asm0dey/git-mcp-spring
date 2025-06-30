package com.github.asm0dey.git_mcp_spring.config;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages configuration settings for Git repositories.
 * Provides methods to read and update Git repository configuration.
 */
public class GitRepositoryHolder {
    private static final Logger logger = LoggerFactory.getLogger(GitRepositoryHolder.class);

    private final Repository repository;
    private Config config;

    /**
     * Creates a new Git repository configuration manager.
     * 
     * @param repository JGit repository
     */
    public GitRepositoryHolder(Repository repository) {
        this.repository = repository;
        this.config = repository.getConfig();
    }

    /**
     * Reloads the configuration from the repository.
     */
    public void reload() {
        this.config = repository.getConfig();
        logger.debug("Reloaded configuration for repository: {}", repository.getDirectory());
    }

    /**
     * Gets a configuration value.
     * 
     * @param section Configuration section
     * @param name Configuration name
     * @return Configuration value or null if not found
     */
    public String getString(String section, String name) {
        return config.getString(section, null, name);
    }

    /**
     * Gets a configuration value.
     * 
     * @param section Configuration section
     * @param subsection Configuration subsection
     * @param name Configuration name
     * @return Configuration value or null if not found
     */
    public String getString(String section, String subsection, String name) {
        return config.getString(section, subsection, name);
    }

    /**
     * Sets a configuration value.
     * 
     * @param section Configuration section
     * @param name Configuration name
     * @param value Configuration value
     */
    public void setString(String section, String name, String value) {
        config.setString(section, null, name, value);
        logger.debug("Set configuration {}={} for section {}", name, value, section);
    }

    /**
     * Sets a configuration value.
     * 
     * @param section Configuration section
     * @param subsection Configuration subsection
     * @param name Configuration name
     * @param value Configuration value
     */
    public void setString(String section, String subsection, String name, String value) {
        config.setString(section, subsection, name, value);
        logger.debug("Set configuration {}={} for section {}.{}", name, value, section, subsection);
    }

    /**
     * Gets a boolean configuration value.
     * 
     * @param section Configuration section
     * @param name Configuration name
     * @return Configuration value or false if not found
     */
    public boolean getBoolean(String section, String name) {
        return config.getBoolean(section, null, name, false);
    }

    /**
     * Gets a boolean configuration value.
     * 
     * @param section Configuration section
     * @param subsection Configuration subsection
     * @param name Configuration name
     * @return Configuration value or false if not found
     */
    public boolean getBoolean(String section, String subsection, String name) {
        return config.getBoolean(section, subsection, name, false);
    }

    /**
     * Sets a boolean configuration value.
     * 
     * @param section Configuration section
     * @param name Configuration name
     * @param value Configuration value
     */
    public void setBoolean(String section, String name, boolean value) {
        config.setBoolean(section, null, name, value);
        logger.debug("Set configuration {}={} for section {}", name, value, section);
    }

    /**
     * Sets a boolean configuration value.
     * 
     * @param section Configuration section
     * @param subsection Configuration subsection
     * @param name Configuration name
     * @param value Configuration value
     */
    public void setBoolean(String section, String subsection, String name, boolean value) {
        config.setBoolean(section, subsection, name, value);
        logger.debug("Set configuration {}={} for section {}.{}", name, value, section, subsection);
    }

    /**
     * Gets all configuration values for a section.
     * 
     * @param section Configuration section
     * @return Map of configuration names to values
     */
    public Map<String, String> getSection(String section) {
        Map<String, String> result = new HashMap<>();
        Set<String> names = config.getNames(section);
        for (String name : names) {
            result.put(name, config.getString(section, null, name));
        }
        return result;
    }

    /**
     * Gets all configuration values for a section and subsection.
     * 
     * @param section Configuration section
     * @param subsection Configuration subsection
     * @return Map of configuration names to values
     */
    public Map<String, String> getSection(String section, String subsection) {
        Map<String, String> result = new HashMap<>();
        Set<String> names = config.getNames(section, subsection);
        for (String name : names) {
            result.put(name, config.getString(section, subsection, name));
        }
        return result;
    }

    /**
     * Saves the configuration changes to the repository.
     * 
     * @return True if the configuration was saved successfully, false otherwise
     */
    public boolean save() {
        try {
            // In JGit, we need to save the config to the repository's config file
            repository.getConfig().save();
            logger.info("Saved configuration for repository: {}", repository.getDirectory());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save configuration for repository: {}", repository.getDirectory(), e);
            return false;
        }
    }

    /**
     * Gets the user name configured for the repository.
     * 
     * @return User name or null if not configured
     */
    public String getUserName() {
        return getString("user", "name");
    }

    /**
     * Sets the user name for the repository.
     * 
     * @param name User name
     */
    public void setUserName(String name) {
        setString("user", "name", name);
    }

    /**
     * Gets the user email configured for the repository.
     * 
     * @return User email or null if not configured
     */
    public String getUserEmail() {
        return getString("user", "email");
    }

    /**
     * Sets the user email for the repository.
     * 
     * @param email User email
     */
    public void setUserEmail(String email) {
        setString("user", "email", email);
    }

    /**
     * Gets the default branch configured for the repository.
     * 
     * @return Default branch or "master" if not configured
     */
    public String getDefaultBranch() {
        return getString("init", "defaultBranch");
    }

    /**
     * Sets the default branch for the repository.
     * 
     * @param branch Default branch
     */
    public void setDefaultBranch(String branch) {
        setString("init", "defaultBranch", branch);
    }
}
