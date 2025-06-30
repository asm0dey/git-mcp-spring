package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for Git remote operations.
 * Provides methods for remote management, pushing, pulling, and fetching.
 */
@Service
public class GitRemoteService {
    private static final Logger logger = LoggerFactory.getLogger(GitRemoteService.class);

    private final GitRepositoryResource repository;

    /**
     * Creates a new Git remote service.
     * 
     * @param repository Git repository resource
     */
    public GitRemoteService(GitRepositoryResource repository) {
        this.repository = repository;
    }

    /**
     * Lists all remotes in the repository.
     * 
     * @return List of remotes
     */
    public List<Map<String, Object>> listRemotes() {
        logger.debug("Listing remotes for repository: {}", repository.getPath());

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ArrayList<>();
            }

            // Get all remotes
            List<RemoteConfig> remotes = git.remoteList().call();

            // Convert remotes to maps
            return remotes.stream()
                    .map(this::remoteToMap)
                    .collect(Collectors.toList());
        } catch (GitAPIException e) {
            logger.error("Error listing remotes: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets details of a specific remote.
     * 
     * @param remoteName Name of the remote
     * @return Remote details or null if not found
     */
    public Map<String, Object> getRemote(String remoteName) {
        logger.debug("Getting remote: {}", remoteName);

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return null;
            }

            // Get all remotes
            List<RemoteConfig> remotes = git.remoteList().call();

            // Find the specified remote
            return remotes.stream()
                    .filter(remote -> remote.getName().equals(remoteName))
                    .findFirst()
                    .map(this::remoteToMap)
                    .orElse(null);
        } catch (GitAPIException e) {
            logger.error("Error getting remote: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Adds a new remote to the repository.
     * 
     * @param remoteName Name of the remote
     * @param remoteUrl URL of the remote
     * @return Result of the operation
     */
    public Map<String, Object> addRemote(String remoteName, String remoteUrl) {
        logger.debug("Adding remote: {} with URL: {}", remoteName, remoteUrl);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Check if remote already exists
            List<RemoteConfig> existingRemotes = git.remoteList().call();
            boolean remoteExists = existingRemotes.stream()
                    .anyMatch(remote -> remote.getName().equals(remoteName));

            if (remoteExists) {
                logger.error("Remote already exists: {}", remoteName);
                result.put("error", "Remote already exists: " + remoteName);
                result.put("success", false);
                return result;
            }

            // Add the remote
            RemoteConfig remoteConfig = git.remoteAdd()
                    .setName(remoteName)
                    .setUri(new URIish(remoteUrl))
                    .call();

            result.put("success", true);
            result.put("remote", remoteToMap(remoteConfig));

            logger.info("Added remote: {} with URL: {}", remoteName, remoteUrl);

            return result;
        } catch (GitAPIException | URISyntaxException e) {
            logger.error("Error adding remote: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Removes a remote from the repository.
     * 
     * @param remoteName Name of the remote to remove
     * @return Result of the operation
     */
    public Map<String, Object> removeRemote(String remoteName) {
        logger.debug("Removing remote: {}", remoteName);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Check if remote exists
            List<RemoteConfig> existingRemotes = git.remoteList().call();
            boolean remoteExists = existingRemotes.stream()
                    .anyMatch(remote -> remote.getName().equals(remoteName));

            if (!remoteExists) {
                logger.error("Remote does not exist: {}", remoteName);
                result.put("error", "Remote does not exist: " + remoteName);
                result.put("success", false);
                return result;
            }

            // Remove the remote
            git.remoteRemove()
                    .setRemoteName(remoteName)
                    .call();

            result.put("success", true);
            result.put("message", "Remote removed: " + remoteName);

            logger.info("Removed remote: {}", remoteName);

            return result;
        } catch (GitAPIException e) {
            logger.error("Error removing remote: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Pushes to a remote repository.
     * 
     * @param remoteName Name of the remote
     * @param refSpec RefSpec to push (e.g., "refs/heads/main:refs/heads/main")
     * @param username Username for authentication (optional)
     * @param password Password for authentication (optional)
     * @return Result of the push operation
     */
    public Map<String, Object> push(String remoteName, String refSpec, String username, String password) {
        logger.debug("Pushing to remote: {} with refSpec: {}", remoteName, refSpec);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Set up the push command
            PushCommand pushCommand = git.push()
                    .setRemote(remoteName)
                    .setRefSpecs(new RefSpec(refSpec));

            // Add credentials if provided
            if (username != null && !username.isEmpty() && password != null) {
                pushCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(username, password));
            }

            // Execute the push
            Iterable<PushResult> pushResults = pushCommand.call();

            // Process the results
            List<Map<String, Object>> resultDetails = new ArrayList<>();
            boolean success = true;

            for (PushResult pushResult : pushResults) {
                Collection<RemoteRefUpdate> remoteUpdates = pushResult.getRemoteUpdates();

                for (RemoteRefUpdate update : remoteUpdates) {
                    Map<String, Object> updateResult = new HashMap<>();
                    updateResult.put("remoteName", update.getRemoteName());
                    updateResult.put("status", update.getStatus().name());
                    updateResult.put("message", update.getMessage());

                    // Check if this update was successful
                    if (update.getStatus() != RemoteRefUpdate.Status.OK && 
                        update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                        success = false;
                    }

                    resultDetails.add(updateResult);
                }
            }

            result.put("success", success);
            result.put("details", resultDetails);

            if (success) {
                logger.info("Successfully pushed to remote: {}", remoteName);
            } else {
                logger.warn("Push to remote {} completed with issues", remoteName);
            }

            return result;
        } catch (TransportException e) {
            logger.error("Transport error during push: {}", e.getMessage(), e);
            result.put("error", "Authentication failed or remote unreachable: " + e.getMessage());
            result.put("success", false);
            return result;
        } catch (GitAPIException e) {
            logger.error("Error pushing to remote: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Fetches from a remote repository.
     * 
     * @param remoteName Name of the remote
     * @param refSpec RefSpec to fetch (optional, fetches all if null)
     * @param username Username for authentication (optional)
     * @param password Password for authentication (optional)
     * @return Result of the fetch operation
     */
    public Map<String, Object> fetch(String remoteName, String refSpec, String username, String password) {
        logger.debug("Fetching from remote: {}", remoteName);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Set up the fetch command
            org.eclipse.jgit.api.FetchCommand fetchCommand = git.fetch()
                    .setRemote(remoteName);

            // Add refSpec if provided
            if (refSpec != null && !refSpec.isEmpty()) {
                fetchCommand.setRefSpecs(new RefSpec(refSpec));
            }

            // Add credentials if provided
            if (username != null && !username.isEmpty() && password != null) {
                fetchCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(username, password));
            }

            // Execute the fetch
            FetchResult fetchResult = fetchCommand.call();

            // Process the results
            Map<String, Object> fetchDetails = new HashMap<>();
            fetchDetails.put("messages", fetchResult.getMessages());

            // Add tracking ref updates
            List<Map<String, Object>> refUpdates = new ArrayList<>();
            for (Ref ref : fetchResult.getAdvertisedRefs()) {
                Map<String, Object> refUpdate = new HashMap<>();
                refUpdate.put("name", ref.getName());
                refUpdate.put("objectId", ref.getObjectId().getName());
                refUpdates.add(refUpdate);
            }
            fetchDetails.put("refUpdates", refUpdates);

            result.put("success", true);
            result.put("details", fetchDetails);

            logger.info("Successfully fetched from remote: {}", remoteName);

            return result;
        } catch (TransportException e) {
            logger.error("Transport error during fetch: {}", e.getMessage(), e);
            result.put("error", "Authentication failed or remote unreachable: " + e.getMessage());
            result.put("success", false);
            return result;
        } catch (GitAPIException e) {
            logger.error("Error fetching from remote: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Pulls from a remote repository.
     * 
     * @param remoteName Name of the remote
     * @param branchName Name of the branch to pull
     * @param username Username for authentication (optional)
     * @param password Password for authentication (optional)
     * @return Result of the pull operation
     */
    public Map<String, Object> pull(String remoteName, String branchName, String username, String password) {
        logger.debug("Pulling from remote: {} branch: {}", remoteName, branchName);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Set up the pull command
            PullCommand pullCommand = git.pull()
                    .setRemote(remoteName);

            // Set branch if provided
            if (branchName != null && !branchName.isEmpty()) {
                pullCommand.setRemoteBranchName(branchName);
            }

            // Add credentials if provided
            if (username != null && !username.isEmpty() && password != null) {
                pullCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(username, password));
            }

            // Execute the pull
            PullResult pullResult = pullCommand.call();

            // Process the results
            Map<String, Object> pullDetails = new HashMap<>();
            pullDetails.put("successful", pullResult.isSuccessful());
            pullDetails.put("fetchedFrom", pullResult.getFetchedFrom());

            // Add merge result if available
            if (pullResult.getMergeResult() != null) {
                Map<String, Object> mergeResult = new HashMap<>();
                mergeResult.put("mergeStatus", pullResult.getMergeResult().getMergeStatus().name());
                mergeResult.put("failed", pullResult.getMergeResult().getFailingPaths());
                pullDetails.put("mergeResult", mergeResult);
            }

            // Add rebase result if available
            if (pullResult.getRebaseResult() != null) {
                Map<String, Object> rebaseResult = new HashMap<>();
                rebaseResult.put("status", pullResult.getRebaseResult().getStatus().name());
                pullDetails.put("rebaseResult", rebaseResult);
            }

            result.put("success", pullResult.isSuccessful());
            result.put("details", pullDetails);

            if (pullResult.isSuccessful()) {
                logger.info("Successfully pulled from remote: {}", remoteName);
            } else {
                logger.warn("Pull from remote {} completed with issues", remoteName);
            }

            return result;
        } catch (TransportException e) {
            logger.error("Transport error during pull: {}", e.getMessage(), e);
            result.put("error", "Authentication failed or remote unreachable: " + e.getMessage());
            result.put("success", false);
            return result;
        } catch (GitAPIException e) {
            logger.error("Error pulling from remote: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Converts a RemoteConfig to a map representation.
     * 
     * @param remote RemoteConfig to convert
     * @return Map representation of the remote
     */
    private Map<String, Object> remoteToMap(RemoteConfig remote) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", remote.getName());

        // Add URIs
        List<String> uris = remote.getURIs().stream()
                .map(URIish::toString)
                .collect(Collectors.toList());
        result.put("uris", uris);

        // Add push URIs
        List<String> pushUris = remote.getPushURIs().stream()
                .map(URIish::toString)
                .collect(Collectors.toList());
        result.put("pushUris", pushUris);

        // Add fetch specs
        List<String> fetchSpecs = remote.getFetchRefSpecs().stream()
                .map(RefSpec::toString)
                .collect(Collectors.toList());
        result.put("fetchSpecs", fetchSpecs);

        // Add push specs
        List<String> pushSpecs = remote.getPushRefSpecs().stream()
                .map(RefSpec::toString)
                .collect(Collectors.toList());
        result.put("pushSpecs", pushSpecs);

        return result;
    }
}
