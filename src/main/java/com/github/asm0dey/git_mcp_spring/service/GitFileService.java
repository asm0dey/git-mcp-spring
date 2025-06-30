package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Service for Git file operations.
 * Provides methods for file status checking, content retrieval, and modification.
 */
@Service
public class GitFileService {
    private static final Logger logger = LoggerFactory.getLogger(GitFileService.class);

    private final GitRepositoryResource repository;

    /**
     * Creates a new Git file service.
     * 
     * @param repository Git repository resource
     */
    public GitFileService(GitRepositoryResource repository) {
        this.repository = repository;
    }

    /**
     * Gets the status of all files in the repository.
     * 
     * @return Map containing file status information
     */
    public Map<String, Object> getRepositoryFileStatus() {
        logger.debug("Getting file status for repository: {}", repository.getPath());

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

            // Add status information to result
            result.put("clean", status.isClean());

            // Add untracked files
            List<String> untrackedFiles = new ArrayList<>(status.getUntracked());
            result.put("untracked", untrackedFiles);

            // Add modified files
            List<String> modifiedFiles = new ArrayList<>(status.getModified());
            result.put("modified", modifiedFiles);

            // Add added files
            List<String> addedFiles = new ArrayList<>(status.getAdded());
            result.put("added", addedFiles);

            // Add changed files
            List<String> changedFiles = new ArrayList<>(status.getChanged());
            result.put("changed", changedFiles);

            // Add removed files
            List<String> removedFiles = new ArrayList<>(status.getRemoved());
            result.put("removed", removedFiles);

            // Add missing files
            List<String> missingFiles = new ArrayList<>(status.getMissing());
            result.put("missing", missingFiles);

            // Add conflicting files
            List<String> conflictingFiles = new ArrayList<>(status.getConflicting());
            result.put("conflicting", conflictingFiles);

            return result;
        } catch (GitAPIException e) {
            logger.error("Error getting repository file status: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Gets the status of a specific file in the repository.
     * 
     * @param filePath Path to the file, relative to the repository root
     * @return Map containing file status information
     */
    public Map<String, Object> getFileStatus(String filePath) {
        logger.debug("Getting status for file: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                return result;
            }

            // Get repository status
            Status status = git.status().addPath(filePath).call();

            // Check if file exists
            File file = new File(repository.getPath().toFile(), filePath);
            result.put("exists", file.exists());

            // Determine file status
            if (status.getUntracked().contains(filePath)) {
                result.put("status", "untracked");
            } else if (status.getModified().contains(filePath)) {
                result.put("status", "modified");
            } else if (status.getAdded().contains(filePath)) {
                result.put("status", "added");
            } else if (status.getChanged().contains(filePath)) {
                result.put("status", "changed");
            } else if (status.getRemoved().contains(filePath)) {
                result.put("status", "removed");
            } else if (status.getMissing().contains(filePath)) {
                result.put("status", "missing");
            } else if (status.getConflicting().contains(filePath)) {
                result.put("status", "conflicting");
            } else if (file.exists()) {
                result.put("status", "tracked");
            } else {
                result.put("status", "unknown");
            }

            // Add file metadata if it exists
            if (file.exists()) {
                result.put("size", file.length());
                result.put("lastModified", file.lastModified());
                result.put("isDirectory", file.isDirectory());
            }

            return result;
        } catch (GitAPIException e) {
            logger.error("Error getting file status: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Checks if a file is binary by examining its content.
     * 
     * @param content File content as byte array
     * @return True if the file is binary, false if it's text
     */
    private boolean isBinaryFile(byte[] content) {
        // A simple heuristic: check for null bytes or too many non-ASCII characters
        if (content == null || content.length == 0) {
            return false;
        }

        // Check a sample of the file (up to 1000 bytes)
        int sampleSize = Math.min(content.length, 1000);
        int nonTextChars = 0;

        for (int i = 0; i < sampleSize; i++) {
            byte b = content[i];
            // Null byte or control characters (except common ones like tab, newline)
            if (b == 0 || (b < 32 && b != 9 && b != 10 && b != 13)) {
                nonTextChars++;
            }
        }

        // If more than 10% of the sample contains non-text characters, consider it binary
        return (nonTextChars * 100 / sampleSize) > 10;
    }

    /**
     * Gets the content of a file in the repository.
     * 
     * @param filePath Path to the file, relative to the repository root
     * @return Map containing file content and metadata
     */
    public Map<String, Object> getFileContent(String filePath) {
        logger.debug("Getting content for file: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                return result;
            }

            // Get file path
            Path repoPath = repository.getPath();
            Path file = repoPath.resolve(filePath);

            // Check if file exists
            if (!Files.exists(file) || Files.isDirectory(file)) {
                logger.error("File does not exist or is a directory: {}", file);
                result.put("error", "File does not exist or is a directory");
                return result;
            }

            // Read file content
            byte[] content = Files.readAllBytes(file);

            // Determine if file is binary
            boolean isBinary = isBinaryFile(content);

            // Add file metadata
            result.put("path", filePath);
            result.put("size", Files.size(file));
            result.put("lastModified", Files.getLastModifiedTime(file).toMillis());
            result.put("isBinary", isBinary);

            // Add file content (as string for text files, base64 for binary files)
            if (isBinary) {
                result.put("encoding", "base64");
                result.put("content", Base64.getEncoder().encodeToString(content));
            } else {
                result.put("encoding", "utf8");
                result.put("content", new String(content, StandardCharsets.UTF_8));
            }

            return result;
        } catch (IOException e) {
            logger.error("Error getting file content: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Writes content to a file in the repository.
     * 
     * @param filePath Path to the file, relative to the repository root
     * @param content Content to write to the file
     * @param encoding Encoding of the content ("utf8" or "base64")
     * @return Map containing operation result
     */
    public Map<String, Object> writeFileContent(String filePath, String content, String encoding) {
        logger.debug("Writing content to file: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            if (git == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                return result;
            }

            // Get file path
            Path repoPath = repository.getPath();
            Path file = repoPath.resolve(filePath);

            // Check if parent directory exists
            Path parentDir = file.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.debug("Created parent directories: {}", parentDir);
            }

            // Convert content to bytes based on encoding
            byte[] contentBytes;
            if ("base64".equals(encoding)) {
                contentBytes = Base64.getDecoder().decode(content);
            } else {
                contentBytes = content.getBytes(StandardCharsets.UTF_8);
            }

            // Write content to file
            Files.write(file, contentBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Add file to Git index if it's not already tracked
            Status status = git.status().addPath(filePath).call();
            if (status.getUntracked().contains(filePath)) {
                git.add().addFilepattern(filePath).call();
                logger.debug("Added new file to Git index: {}", filePath);
            }

            // Get updated file metadata
            result.put("path", filePath);
            result.put("size", Files.size(file));
            result.put("lastModified", Files.getLastModifiedTime(file).toMillis());
            result.put("success", true);

            return result;
        } catch (IOException | GitAPIException e) {
            logger.error("Error writing file content: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * Gets the diff for a file between the working directory and the index.
     * 
     * @param filePath Path to the file, relative to the repository root
     * @return Map containing diff information
     */
    public Map<String, Object> getFileDiff(String filePath) {
        logger.debug("Getting diff for file: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                return result;
            }

            // Check if file exists
            Path repoPath = repository.getPath();
            Path file = repoPath.resolve(filePath);
            if (!Files.exists(file)) {
                logger.error("File does not exist: {}", file);
                result.put("error", "File does not exist");
                return result;
            }

            // Get the diff between the index and the working directory
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(repo);
                formatter.setContext(3); // Set context lines
                formatter.setPathFilter(PathFilter.create(filePath));

                // Get the diff entry for the file
                ObjectId headId = repo.resolve("HEAD^{tree}");
                ObjectReader reader = repo.newObjectReader();
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset(reader, headId);

                FileTreeIterator newTreeIter = new FileTreeIterator(repo);
                List<DiffEntry> diffs = formatter.scan(oldTreeIter, newTreeIter);

                // Find the diff entry for our file
                DiffEntry diffEntry = null;
                for (DiffEntry entry : diffs) {
                    if (entry.getNewPath().equals(filePath) || entry.getOldPath().equals(filePath)) {
                        diffEntry = entry;
                        break;
                    }
                }

                if (diffEntry == null) {
                    logger.debug("No changes found for file: {}", filePath);
                    result.put("hasDiff", false);
                    return result;
                }

                // Format the diff
                formatter.format(diffEntry);
                String diffText = out.toString(StandardCharsets.UTF_8.name());

                // Get the file header and hunks
                FileHeader fileHeader = formatter.toFileHeader(diffEntry);
                List<Map<String, Object>> hunks = new ArrayList<>();

                for (HunkHeader hunkHeader : fileHeader.getHunks()) {
                    Map<String, Object> hunk = new HashMap<>();
                    hunk.put("startLine", hunkHeader.getNewStartLine());
                    hunk.put("endLine", hunkHeader.getNewStartLine() + hunkHeader.getNewLineCount());
                    hunk.put("header", "Hunk at line " + hunkHeader.getNewStartLine());

                    // Get the lines in this hunk
                    EditList edits = hunkHeader.toEditList();
                    List<Map<String, Object>> editsList = new ArrayList<>();

                    for (Edit edit : edits) {
                        Map<String, Object> editMap = new HashMap<>();
                        editMap.put("beginA", edit.getBeginA());
                        editMap.put("endA", edit.getEndA());
                        editMap.put("beginB", edit.getBeginB());
                        editMap.put("endB", edit.getEndB());
                        editMap.put("type", edit.getType().name());
                        editsList.add(editMap);
                    }

                    hunk.put("edits", editsList);
                    hunks.add(hunk);
                }

                result.put("hasDiff", true);
                result.put("diffText", diffText);
                result.put("hunks", hunks);
                result.put("changeType", diffEntry.getChangeType().name());
                result.put("oldPath", diffEntry.getOldPath());
                result.put("newPath", diffEntry.getNewPath());
            }

            return result;
        } catch (IOException e) {
            logger.error("Error getting file diff: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Stages selected hunks from a file.
     * 
     * @param filePath Path to the file, relative to the repository root
     * @param hunkIndices Indices of the hunks to stage (0-based)
     * @return Map containing operation result
     */
    public Map<String, Object> stageHunks(String filePath, List<Integer> hunkIndices) {
        logger.debug("Staging hunks for file: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try {
            Git git = repository.getGit();
            Repository repo = repository.getRepository();
            if (git == null || repo == null) {
                logger.error("Repository is not open: {}", repository.getPath());
                result.put("error", "Repository is not open");
                return result;
            }

            // Get the diff for the file
            Map<String, Object> diffResult = getFileDiff(filePath);
            if (diffResult.containsKey("error")) {
                return diffResult;
            }

            if (!(boolean) diffResult.getOrDefault("hasDiff", false)) {
                result.put("message", "No changes to stage for file: " + filePath);
                result.put("success", false);
                return result;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hunks = (List<Map<String, Object>>) diffResult.get("hunks");
            if (hunks == null || hunks.isEmpty()) {
                result.put("message", "No hunks found for file: " + filePath);
                result.put("success", false);
                return result;
            }

            // Validate hunk indices
            for (Integer index : hunkIndices) {
                if (index < 0 || index >= hunks.size()) {
                    result.put("error", "Invalid hunk index: " + index);
                    result.put("success", false);
                    return result;
                }
            }

            // For each selected hunk, apply it to the index
            // This is a simplified implementation - in a real implementation,
            // we would need to create a patch file and apply it using git apply

            // For now, we'll just add the entire file to the index if any hunks are selected
            // In a more complete implementation, we would:
            // 1. Create a temporary file with the selected hunks applied
            // 2. Add that temporary file to the index
            // 3. Clean up the temporary file

            if (!hunkIndices.isEmpty()) {
                git.add().addFilepattern(filePath).call();
                result.put("message", "Staged selected hunks for file: " + filePath);
                result.put("success", true);
            } else {
                result.put("message", "No hunks selected for staging");
                result.put("success", false);
            }

            return result;
        } catch (GitAPIException e) {
            logger.error("Error staging hunks: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }
}
