package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.model.Result;
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

import static com.github.asm0dey.git_mcp_spring.model.Result.failure;
import static com.github.asm0dey.git_mcp_spring.model.Result.success;
import static org.eclipse.jgit.api.ResetCommand.ResetType.HARD;

/**
 * Service for Git reflog operations.
 * Provides methods for reflog access and management.
 */
@Service
public class GitReflogService {
    private static final Logger logger = LoggerFactory.getLogger(GitReflogService.class);

    private final GitRepositoryService repository;

    public record ReflogCommitter(String name, String email, LocalDateTime time) {
    }

    /**
     * Represents a reflog entry with commit information and index.
     *
     * @param oldId     Previous commit ID
     * @param newId     New commit ID
     * @param message   Reflog message
     * @param committer Committer information
     * @param refName   Name of the ref
     * @param index     Index in reflog (used for HEAD@{n} format)
     */
    public record ReflogEntry(
            String oldId,
            String newId,
            String message,
            ReflogCommitter committer,
            String refName,
            int index
    ) {
    }

    /**
     * Creates a new Git reflog service.
     *
     * @param repository Git repository resource
     */
    public GitReflogService(GitRepositoryService repository) {
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
    public Result<List<ReflogEntry>> getReflog(String refName, int maxCount) {
        if (refName == null || refName.trim().isEmpty()) {
            refName = "HEAD";
        }
        if (maxCount < 0) {
            maxCount = 0;
        }
        var ref = refName;
        if (!repository.hasRepo) return new Result.Failure<>("Repository is not open");
        try (Git git = new Git(repository.getRepository())) {
            Repository repo = git.getRepository();
            ReflogReader reader = repo.getRefDatabase().getReflogReader(ref);
            if (reader == null) {
                logger.warn("No reflog found for ref: {}", ref);
                return failure("No reflog found for ref: " + ref);
            }

            var entries = reader.getReverseEntries();
            return success(entries.stream()
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
                            ref,
                            entries.indexOf(entry)
                    ))
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            logger.error("Error reading reflog for ref: {}", ref, e);
            return failure("Error reading reflog for ref: " + ref);
        }

    }

    /**
     * Reverts the repository state to a specific reflog entry.
     *
     * @param refName  Name of the ref (e.g., "HEAD", "refs/heads/main")
     * @param commitId The commit ID to revert to
     * @return true if revert was successful, false otherwise
     */
    @Tool(name = "git_reflog_revert", description = "Reverts the repository state to a specific reflog entry")
    public boolean revertReflog(String refName, String commitId) {
        if (refName == null || refName.trim().isEmpty()) {
            refName = "HEAD";
        }
        try (Git git = new Git(repository.getRepository())) {
            git.reset()
                    .setMode(HARD)
                    .setRef(commitId)
                    .call();
            return true;
        } catch (Exception e) {
            logger.error("Error reverting to reflog entry: {} at {}", refName, commitId, e);
            return false;
        }
    }

    private Result<String[]> parseReflogExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        var pattern = "(.+)@\\{(\\d+)}";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(expression);
        if (matcher.matches()) {
            String refName = matcher.group(1);
            int index = Integer.parseInt(matcher.group(2));
            var res = getReflog(refName, index + 1);
            switch (res) {
                case Result.Failure(String m) -> {
                    return failure(m);
                }
                case Result.Success(List<ReflogEntry> entries) -> {
                    if (entries.size() > index) {
                        return success(new String[]{refName, entries.get(index).newId()});
                    }

                }
            }
        }
        return null;
    }

    /**
     * Reverts the repository state to a specific reflog entry using ref@{n} syntax.
     *
     * @param expression The reflog expression (e.g., "HEAD@{3}", "refs/heads/main@{2}")
     * @return true if revert was successful, false otherwise
     */
    @Tool(name = "git_reflog_revert_expression", description = "Reverts the repository state to a specific reflog entry using ref@{n} syntax")
    public boolean revertReflog(String expression) {
        Result<String[]> refInfo = parseReflogExpression(expression);
        switch (refInfo) {
            case Result.Failure(String m) -> {
                logger.error("Error parsing reflog expression {}: {}", expression, m);
                return false;
            }
            case Result.Success(String[] ref) -> {
                return revertReflog(ref[0], ref[1]);
            }
        }
    }

}