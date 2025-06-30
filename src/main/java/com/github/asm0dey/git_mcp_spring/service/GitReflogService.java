package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Git reflog operations.
 * Provides methods for reflog access and management.
 */
@Service
public class GitReflogService {
    private static final Logger logger = LoggerFactory.getLogger(GitReflogService.class);

    private final GitRepositoryResource repository;

    public record ReflogCommitter(String name, String email, LocalDateTime time) {
    }

    public record ReflogEntry(
            String oldId,
            String newId,
            String message,
            ReflogCommitter committer,
            String checkout,
            String refName
    ) {
    }

    /**
     * Creates a new Git reflog service.
     *
     * @param repository Git repository resource
     */
    public GitReflogService(GitRepositoryResource repository) {
        this.repository = repository;
    }

    /**
     * Gets the reflog for a specific ref.
     *
     * @param refName  Name of the ref (e.g., "HEAD", "refs/heads/main")
     * @param maxCount Maximum number of entries to return (0 for all)
     * @return List of reflog entries
     */
    @Tool(name = "git_reflog_get", description = "Gets the reflog for a specific ref, HEAD by default")
    public List<ReflogEntry> getReflog(String refName, int maxCount) {
        if (refName == null || refName.trim().isEmpty()) {
            refName = "HEAD";
        }
        if (maxCount < 0) {
            maxCount = 0;
        }
        var ref = refName;
        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();
            ReflogReader reader = repo.getRefDatabase().getReflogReader(refName);
            if (reader == null) {
                logger.warn("No reflog found for ref: {}", refName);
                return List.of();
            }

            return reader.getReverseEntries().stream()
                    .limit(maxCount > 0 ? maxCount : Long.MAX_VALUE)
                    .map(entry -> new ReflogEntry(
                            entry.getOldId().name(),
                            entry.getNewId().name(),
                            entry.getComment(),
                            new ReflogCommitter(
                                    entry.getWho().getName(),
                                    entry.getWho().getEmailAddress(),
                                    LocalDateTime.ofInstant(entry.getWho().getWhenAsInstant(), ZoneId.systemDefault())
                            ),
                            null,
                            ref
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error reading reflog for ref: {}", refName, e);
            return List.of();
        }

    }
}