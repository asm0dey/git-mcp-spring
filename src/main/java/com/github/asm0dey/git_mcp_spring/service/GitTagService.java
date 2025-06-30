package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Git tag operations.
 * Provides methods for tag management.
 */
@Service
public class GitTagService {
    private static final Logger logger = LoggerFactory.getLogger(GitTagService.class);

    private final GitRepositoryResource repository;

    /**
     * Creates a new Git tag service.
     * 
     * @param repository Git repository resource
     */
    public GitTagService(GitRepositoryResource repository) {
        this.repository = repository;
    }

    /**
     * Lists all tags in the repository.
     * 
     * @return List of tags
     */
    public List<Map<String, Object>> listTags() {
        logger.debug("Listing tags for repository: {}", repository.getPath());

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return new ArrayList<>();
            }

            // Get all tags
            List<Ref> tags = git.tagList().call();

            // Convert tags to maps
            return tags.stream()
                    .map(this::tagToMap)
                    .collect(Collectors.toList());
        } catch (GitAPIException e) {
            logger.error("Error listing tags: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets details of a specific tag.
     * 
     * @param tagName Name of the tag
     * @return Tag details or null if not found
     */
    public Map<String, Object> getTag(String tagName) {
        logger.debug("Getting tag: {}", tagName);

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                return null;
            }

            // Try to find the tag
            Ref tag = repo.findRef(tagName);
            if (tag == null) {
                // Try with refs/tags/ prefix
                tag = repo.findRef("refs/tags/" + tagName);
            }

            if (tag == null) {
                logger.error("Tag not found: {}", tagName);
                return null;
            }

            return tagToMap(tag);
        } catch (IOException e) {
            logger.error("Error getting tag: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Creates a new tag.
     * 
     * @param tagName Name of the new tag
     * @param targetCommit Target commit for the tag
     * @param message Tag message (optional)
     * @param taggerName Tagger name (optional, uses config if null)
     * @param taggerEmail Tagger email (optional, uses config if null)
     * @return Result of the operation
     */
    public Map<String, Object> createTag(String tagName, String targetCommit, String message, 
                                         String taggerName, String taggerEmail) {
        logger.debug("Creating tag: {} on: {}", tagName, targetCommit);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Get user information from config if not provided
            if (taggerName == null || taggerName.trim().isEmpty()) {
                taggerName = repository.getConfigValue("user", "name");
            }

            if (taggerEmail == null || taggerEmail.trim().isEmpty()) {
                taggerEmail = repository.getConfigValue("user", "email");
            }

            // Create the tag command
            org.eclipse.jgit.api.TagCommand tagCommand = git.tag()
                    .setName(tagName);

            // Resolve the target commit and set it as the tag's object
            try (RevWalk revWalk = new RevWalk(git.getRepository())) {
                RevObject targetObject = revWalk.parseAny(git.getRepository().resolve(targetCommit));
                tagCommand.setObjectId(targetObject);
            }

            // Add message and tagger if provided
            if (message != null && !message.trim().isEmpty()) {
                tagCommand.setMessage(message);

                if (taggerName != null && !taggerName.trim().isEmpty() && 
                    taggerEmail != null && !taggerEmail.trim().isEmpty()) {
                    tagCommand.setTagger(new PersonIdent(taggerName, taggerEmail));
                }
            }

            // Create the tag
            Ref tag = tagCommand.call();

            result.put("success", true);
            result.put("tag", tagToMap(tag));

            logger.info("Created tag: {} on: {}", tagName, targetCommit);

            return result;
        } catch (GitAPIException | IOException e) {
            logger.error("Error creating tag: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Deletes a tag.
     * 
     * @param tagName Name of the tag to delete
     * @return Result of the operation
     */
    public Map<String, Object> deleteTag(String tagName) {
        logger.debug("Deleting tag: {}", tagName);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                result.put("success", false);
                return result;
            }

            // Delete the tag
            List<String> deletedTags = git.tagDelete()
                    .setTags(tagName)
                    .call();

            boolean success = deletedTags.contains(tagName);
            result.put("success", success);

            if (success) {
                result.put("message", "Tag deleted: " + tagName);
                logger.info("Deleted tag: {}", tagName);
            } else {
                result.put("error", "Failed to delete tag: " + tagName);
                logger.error("Failed to delete tag: {}", tagName);
            }

            return result;
        } catch (GitAPIException e) {
            logger.error("Error deleting tag: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Converts a Git object type (int) to a string representation.
     * 
     * @param type Git object type
     * @return String representation of the type
     */
    private String typeToString(int type) {
        switch (type) {
            case org.eclipse.jgit.lib.Constants.OBJ_COMMIT:
                return "COMMIT";
            case org.eclipse.jgit.lib.Constants.OBJ_TREE:
                return "TREE";
            case org.eclipse.jgit.lib.Constants.OBJ_BLOB:
                return "BLOB";
            case org.eclipse.jgit.lib.Constants.OBJ_TAG:
                return "TAG";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Converts a JGit Ref to a map representation.
     * 
     * @param ref JGit Ref
     * @return Map representation of the tag
     */
    private Map<String, Object> tagToMap(Ref ref) {
        Map<String, Object> result = new HashMap<>();

        // Basic tag information
        result.put("name", ref.getName());
        result.put("shortName", Repository.shortenRefName(ref.getName()));
        result.put("objectId", ref.getObjectId().getName());

        // Try to get additional tag information
        try (RevWalk revWalk = new RevWalk(repository.getRepository())) {
            RevObject obj = revWalk.parseAny(ref.getObjectId());

            // If this is an annotated tag
            if (obj instanceof RevTag) {
                RevTag tag = (RevTag) obj;

                // Add tag message
                result.put("message", tag.getFullMessage());

                // Add tagger information
                PersonIdent tagger = tag.getTaggerIdent();
                if (tagger != null) {
                    Map<String, Object> taggerInfo = new HashMap<>();
                    taggerInfo.put("name", tagger.getName());
                    taggerInfo.put("email", tagger.getEmailAddress());
                    taggerInfo.put("time", tagger.getWhen().getTime());
                    result.put("tagger", taggerInfo);
                }

                // Add target information
                RevObject target = revWalk.peel(tag);
                result.put("targetType", typeToString(target.getType()));
                result.put("targetId", target.getName());

                // If the target is a commit, add commit information
                if (target instanceof RevCommit) {
                    RevCommit commit = (RevCommit) target;
                    result.put("targetMessage", commit.getShortMessage());
                }
            } else {
                // For lightweight tags, the object is directly the target
                result.put("targetType", typeToString(obj.getType()));
                result.put("targetId", obj.getName());

                if (obj instanceof RevCommit) {
                    RevCommit commit = (RevCommit) obj;
                    result.put("targetMessage", commit.getShortMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Error getting tag details: {}", e.getMessage(), e);
        }

        return result;
    }
}
