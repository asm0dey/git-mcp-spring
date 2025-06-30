package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Git branch and tag operations.
 * Provides methods for branch and tag management.
 */
@Service
public class GitBranchService {
    private static final Logger logger = LoggerFactory.getLogger(GitBranchService.class);

    private final GitRepositoryResource repository;

    public GitBranchService(GitRepositoryResource repository) {
        this.repository = repository;
    }


    /**
     * Lists all branches in the repository.
     *
     * @param listMode Mode for listing branches (ALL, REMOTE, or LOCAL)
     * @return List of branches
     */
    @Tool(name = "git_list_branches", description = "Lists all branches in the repository")
    public List<Map<String, Object>> listBranches(ListBranchCommand.ListMode listMode) {
        logger.debug("Listing branches with mode: {}", listMode);

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ArrayList<>();
            }

            // Get all branches
            List<Ref> branches = git.branchList()
                    .setListMode(listMode)
                    .call();

            // Convert branches to maps
            return branches.stream()
                    .map(this::branchToMap)
                    .collect(Collectors.toList());
        } catch (GitAPIException e) {
            logger.error("Error listing branches: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets details of a specific branch.
     *
     * @param branchName Name of the branch
     * @return Branch details or null if not found
     */
    @Tool(name = "git_get_branch", description = "Gets details of a specific branch")
    public Map<String, Object> getBranch(String branchName) {
        logger.debug("Getting branch: {}", branchName);

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return null;
            }

            // Try to find the branch
            Ref branch = repo.findRef(branchName);
            if (branch == null) {
                // Try with refs/heads/ prefix
                branch = repo.findRef("refs/heads/" + branchName);
            }
            if (branch == null) {
                // Try with refs/remotes/ prefix
                branch = repo.findRef("refs/remotes/" + branchName);
            }

            if (branch == null) {
                logger.error("Branch not found: {}", branchName);
                return null;
            }

            return branchToMap(branch);
        } catch (IOException e) {
            logger.error("Error getting branch: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Creates a new branch.
     *
     * @param branchName Name of the new branch
     * @param startPoint Starting point for the branch (commit ID, branch name, or tag name)
     * @return Result of the operation
     */
    @Tool(name = "git_create_branch", description = "Creates a new branch")
    public Map<String, Object> createBranch(String branchName, String startPoint) {
        logger.debug("Creating branch: {} from: {}", branchName, startPoint);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Create the branch
            Ref branch = git.branchCreate()
                    .setName(branchName)
                    .setStartPoint(startPoint)
                    .call();

            result.put("success", true);
            result.put("branch", branchToMap(branch));

            logger.info("Created branch: {} from: {}", branchName, startPoint);

            return result;
        } catch (GitAPIException e) {
            logger.error("Error creating branch: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Deletes a branch.
     *
     * @param branchName Name of the branch to delete
     * @param force      Whether to force deletion even if branch is not fully merged
     * @return Result of the operation
     */
    @Tool(name = "git_delete_branch", description = "Deletes a branch")
    public Map<String, Object> deleteBranch(String branchName, boolean force) {
        logger.debug("Deleting branch: {} (force: {})", branchName, force);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Delete the branch
            List<String> deletedBranches = git.branchDelete()
                    .setBranchNames(branchName)
                    .setForce(force)
                    .call();

            boolean success = deletedBranches.contains(branchName);
            result.put("success", success);

            if (success) {
                result.put("message", "Branch deleted: " + branchName);
                logger.info("Deleted branch: {}", branchName);
            } else {
                result.put("error", "Failed to delete branch: " + branchName);
                logger.error("Failed to delete branch: {}", branchName);
            }

            return result;
        } catch (GitAPIException e) {
            logger.error("Error deleting branch: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Renames a branch.
     *
     * @param oldName Current name of the branch
     * @param newName New name for the branch
     * @return Result of the operation
     */
    @Tool(name = "git_rename_branch", description = "Renames a branch")
    public Map<String, Object> renameBranch(String oldName, String newName) {
        logger.debug("Renaming branch: {} to: {}", oldName, newName);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Rename the branch
            Ref branch = git.branchRename()
                    .setOldName(oldName)
                    .setNewName(newName)
                    .call();

            result.put("success", true);
            result.put("branch", branchToMap(branch));
            result.put("message", "Branch renamed from " + oldName + " to " + newName);

            logger.info("Renamed branch: {} to: {}", oldName, newName);

            return result;
        } catch (GitAPIException e) {
            logger.error("Error renaming branch: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Checks out a branch.
     *
     * @param branchName   Name of the branch to checkout
     * @param createBranch Whether to create the branch if it doesn't exist
     * @return Result of the operation
     */
    @Tool(name = "git_checkout_branch", description = "Checks out a branch")
    public Map<String, Object> checkoutBranch(String branchName, boolean createBranch) {
        logger.debug("Checking out branch: {} (create: {})", branchName, createBranch);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Checkout the branch
            Ref branch = git.checkout()
                    .setName(branchName)
                    .setCreateBranch(createBranch)
                    .call();

            result.put("success", true);
            result.put("branch", branchToMap(branch));
            result.put("message", "Checked out branch: " + branchName);

            logger.info("Checked out branch: {}", branchName);

            return result;
        } catch (GitAPIException e) {
            logger.error("Error checking out branch: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Converts a JGit Ref to a map representation.
     *
     * @param ref JGit Ref
     * @return Map representation of the branch
     */
    private Map<String, Object> branchToMap(Ref ref) {
        Map<String, Object> result = new HashMap<>();

        // Basic branch information
        result.put("name", ref.getName());
        result.put("shortName", Repository.shortenRefName(ref.getName()));
        result.put("objectId", ref.getObjectId().getName());
        result.put("isSymbolic", ref.isSymbolic());

        // If it's a symbolic ref, add the target
        if (ref.isSymbolic()) {
            result.put("target", ref.getTarget().getName());
        }

        // Determine if it's a local or remote branch
        boolean isRemote = ref.getName().startsWith("refs/remotes/");
        result.put("isRemote", isRemote);

        // If it's a remote branch, extract the remote name
        if (isRemote) {
            String shortName = Repository.shortenRefName(ref.getName());
            int slashIndex = shortName.indexOf('/');
            if (slashIndex > 0) {
                result.put("remoteName", shortName.substring(0, slashIndex));
            }
        }

        return result;
    }
}