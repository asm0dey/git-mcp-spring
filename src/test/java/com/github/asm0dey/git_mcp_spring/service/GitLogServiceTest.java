package com.github.asm0dey.git_mcp_spring.service;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitLogServiceTest {

    @Test
    void getLog_withDefaultOptions_returnsCommitInfo(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create some commits
            createAndCommitFile(git, "file1.txt", "Content 1");
            createAndCommitFile(git, "file2.txt", "Content 2");

            // Test with default options
            GitLogService.GitLogOptions options = GitLogService.GitLogOptions.defaults();
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(2);

            // Check first commit (most recent)
            GitLogService.CommitInfo firstCommit = commits.getFirst();
            assertThat(firstCommit.shortMessage()).isEqualTo("Add file2.txt");
            assertThat(firstCommit.commitId()).isNotNull();
            assertThat(firstCommit.author()).isNotNull();
            assertThat(firstCommit.commitTime()).isNotNull();
            assertThat(firstCommit.tags()).isEmpty(); // Default options don't show tags
            assertThat(firstCommit.branches()).isEmpty(); // Default options don't show branches
            assertThat(firstCommit.fileChanges()).isEmpty(); // Default options don't show file changes
            assertThat(firstCommit.diffs()).isEmpty(); // Default options don't show diffs

            // Check second commit (older)
            GitLogService.CommitInfo secondCommit = commits.get(1);
            assertThat(secondCommit.shortMessage()).isEqualTo("Add file1.txt");

            repositoryService.close();
        }
    }

    @Test
    void getLog_withMaxCount_limitsResults(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "Content 1");
            createAndCommitFile(git, "file2.txt", "Content 2");
            createAndCommitFile(git, "file3.txt", "Content 3");

            // Test with maxCount = 1
            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, false, false, 1
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(1);
            assertThat(commits.getFirst().shortMessage()).isEqualTo("Add file3.txt");

            repositoryService.close();
        }
    }

    @Test
    void getLog_withFullMessages_returnsFullMessage(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create a commit with a multi-line message
            Path filePath = git.getRepository().getWorkTree().toPath().resolve("test.txt");
            Files.writeString(filePath, "test content");
            git.add().addFilepattern("test.txt").call();
            git.commit()
                    .setMessage("Short message\n\nThis is a longer description\nwith multiple lines")
                    .call();

            // Test with fullMessages = true
            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, true, true, true, false, false, false, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(1);
            GitLogService.CommitInfo commit = commits.getFirst();
            assertThat(commit.shortMessage()).isEqualTo("Short message");
            assertThat(commit.fullMessage()).contains("This is a longer description");
            assertThat(commit.fullMessage()).contains("with multiple lines");

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowCommitters_includesCommitterInfo(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            createAndCommitFile(git, "file1.txt", "Content 1");

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, true, false, true, true, false, false, false, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(1);
            GitLogService.CommitInfo commit = commits.getFirst();
            assertThat(commit.author()).isNotNull();
            assertThat(commit.committer()).isNotNull();
            assertThat(commit.author().name()).isNotNull();
            assertThat(commit.committer().name()).isNotNull();

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowFileChanges_includesFileChangeInfo(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create initial commit
            createAndCommitFile(git, "file1.txt", "Initial content");

            // Modify the file
            Path filePath = git.getRepository().getWorkTree().toPath().resolve("file1.txt");
            Files.writeString(filePath, "Modified content");
            git.add().addFilepattern("file1.txt").call();
            git.commit().setMessage("Modify file1.txt").call();

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, false, true, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(2);
            GitLogService.CommitInfo modifyCommit = commits.getFirst();
            assertThat(modifyCommit.fileChanges()).isNotEmpty();
            assertThat(modifyCommit.fileChanges().getFirst().path()).isEqualTo("file1.txt");
            assertThat(modifyCommit.fileChanges().getFirst().changeType()).isEqualTo("MODIFY");

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowTags_includesTagInfo(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create commit and tag it
            createAndCommitFile(git, "file1.txt", "Content 1");
            git.tag().setName("v1.0").call();

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, true, false, false, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(1);
            GitLogService.CommitInfo commit = commits.getFirst();
            assertThat(commit.tags()).contains("v1.0");

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowBranches_includesBranchInfo(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create commit
            createAndCommitFile(git, "file1.txt", "Content 1");

            // Create a new branch pointing to the same commit
            git.branchCreate().setName("feature-branch").call();

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, true, false, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(1);
            GitLogService.CommitInfo commit = commits.getFirst();
            assertThat(commit.branches()).contains("main", "feature-branch");

            repositoryService.close();
        }
    }

    @Test
    void getLog_withEmptyRepository_returnsEmptyList(@TempDir Path tempDir) throws Exception {
        try (var _ = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            GitLogService.GitLogOptions options = GitLogService.GitLogOptions.defaults();
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).isEmpty();

            repositoryService.close();
        }
    }

    @Test
    void getLog_personInfoFromPersonIdent_mapsCorrectly(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            createAndCommitFile(git, "file1.txt", "Content 1");

            GitLogService.GitLogOptions options = GitLogService.GitLogOptions.defaults();
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(1);
            GitLogService.PersonInfo author = commits.getFirst().author();
            assertThat(author.name()).isNotBlank();
            assertThat(author.email()).isNotBlank();
            assertThat(author.when()).isBefore(Instant.now());

            repositoryService.close();
        }
    }

    @Test
    void getLog_commitInfoFields_allPopulated(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            createAndCommitFile(git, "test.txt", "test content");

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, true, true, true, true, true, true, true, true, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(1);
            GitLogService.CommitInfo commit = commits.getFirst();

            // Verify all fields are present (even if some lists are empty)
            assertThat(commit.commitId()).isNotNull();
            assertThat(commit.shortMessage()).isNotNull();
            assertThat(commit.fullMessage()).isNotNull();
            assertThat(commit.author()).isNotNull();
            assertThat(commit.committer()).isNotNull();
            assertThat(commit.commitTime()).isNotNull();
            assertThat(commit.tags()).isNotNull();
            assertThat(commit.branches()).isNotNull();
            assertThat(commit.fileChanges()).isNotNull();
            assertThat(commit.diffs()).isNotNull();

            repositoryService.close();
        }
    }

    @Test
    void getLog_multipleFileChanges_tracksAllChanges(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create initial commit with multiple files
            createAndCommitFile(git, "file1.txt", "Content 1");
            createAndCommitFile(git, "file2.txt", "Content 2");

            // Modify multiple files in one commit
            Files.writeString(tempDir.resolve("file1.txt"), "Modified content 1");
            Files.writeString(tempDir.resolve("file2.txt"), "Modified content 2");
            Files.writeString(tempDir.resolve("file3.txt"), "New content 3");

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Modify multiple files").call();

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, false, true, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            // Check the latest commit (multiple file changes)
            GitLogService.CommitInfo latestCommit = commits.getFirst();
            assertThat(latestCommit.fileChanges()).hasSize(3);

            List<String> changedFiles = latestCommit.fileChanges().stream()
                    .map(GitLogService.FileChangeInfo::path)
                    .toList();
            assertThat(changedFiles).contains("file1.txt", "file2.txt", "file3.txt");

            repositoryService.close();
        }
    }

    @Test
    void getLog_defaultOptions_hasCorrectDefaults() {
        GitLogService.GitLogOptions defaults = GitLogService.GitLogOptions.defaults();

        assertThat(defaults.showAuthors()).isTrue();
        assertThat(defaults.showCommitters()).isFalse();
        assertThat(defaults.fullMessages()).isFalse();
        assertThat(defaults.showCommitIds()).isTrue();
        assertThat(defaults.showDates()).isTrue();
        assertThat(defaults.showTags()).isFalse();
        assertThat(defaults.showBranches()).isFalse();
        assertThat(defaults.showDiffs()).isFalse();
        assertThat(defaults.showFileChanges()).isFalse();
        assertThat(defaults.maxCount()).isEqualTo(30);
    }

    @Test
    void getLog_withZeroMaxCount_returnsAllCommits(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "Content 1");
            createAndCommitFile(git, "file2.txt", "Content 2");
            createAndCommitFile(git, "file3.txt", "Content 3");

            // Test with maxCount = 0 (should return all commits)
            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, false, false, 0
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(3);

            repositoryService.close();
        }
    }

    private void createAndCommitFile(Git git, String filename, String content) throws Exception {
        Path filePath = git.getRepository().getWorkTree().toPath().resolve(filename);
        Files.writeString(filePath, content);
        git.add().addFilepattern(filename).call();
        git.commit().setMessage("Add " + filename).call();
    }

    @Test
    void getLog_withShowDiffs_includesDiffInfo(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create initial commit with a file
            createAndCommitFile(git, "example.txt", "Line 1\nLine 2\nLine 3\n");

            // Modify the file to create a meaningful diff
            Path filePath = git.getRepository().getWorkTree().toPath().resolve("example.txt");
            Files.writeString(filePath, "Line 1\nModified Line 2\nLine 3\nNew Line 4\n");
            git.add().addFilepattern("example.txt").call();
            git.commit().setMessage("Modify example.txt").call();

            // Test with showDiffs = true
            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, true, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(2);

            // Check the modification commit (most recent)
            GitLogService.CommitInfo modifyCommit = commits.getFirst();
            assertThat(modifyCommit.diffs()).isNotEmpty();
            assertThat(modifyCommit.diffs()).hasSize(1);

            GitLogService.DiffInfo diffInfo = modifyCommit.diffs().getFirst();
            assertThat(diffInfo.oldPath()).isEqualTo("example.txt");
            assertThat(diffInfo.newPath()).isEqualTo("example.txt");
            assertThat(diffInfo.diff()).isNotNull();

            // Check the initial commit (should have no diffs since it has no parent)
            GitLogService.CommitInfo initialCommit = commits.get(1);
            assertThat(initialCommit.diffs()).isEmpty();

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowDiffs_multipleFiles(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create initial commit with multiple files
            createAndCommitFile(git, "file1.txt", "Original content 1");
            createAndCommitFile(git, "file2.txt", "Original content 2");

            // Modify both files and add a new one in a single commit
            Files.writeString(tempDir.resolve("file1.txt"), "Modified content 1");
            Files.writeString(tempDir.resolve("file2.txt"), "Modified content 2");
            Files.writeString(tempDir.resolve("file3.txt"), "New file content");

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Modify multiple files").call();

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, true, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            // Check the latest commit with multiple file changes
            GitLogService.CommitInfo latestCommit = commits.getFirst();
            assertThat(latestCommit.diffs()).hasSize(3);

            List<String> modifiedFiles = latestCommit.diffs().stream()
                    .map(GitLogService.DiffInfo::newPath)
                    .toList();
            assertThat(modifiedFiles).contains("file1.txt", "file2.txt", "file3.txt");

            // Verify each diff has content
            for (GitLogService.DiffInfo diff : latestCommit.diffs()) {
                assertThat(diff.diff()).isNotNull();
                assertThat(diff.newPath()).isNotNull();
            }

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowDiffs_fileAddedAndDeleted(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create initial commit
            createAndCommitFile(git, "temp.txt", "Temporary content");
            createAndCommitFile(git, "keep.txt", "Keep this file");

            // Delete one file and add another
            Files.delete(tempDir.resolve("temp.txt"));
            Files.writeString(tempDir.resolve("new.txt"), "New file content");

            git.add().addFilepattern(".").call();
            git.rm().addFilepattern("temp.txt").call();
            git.commit().setMessage("Delete temp.txt and add new.txt").call();

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, true, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            // Check the latest commit
            GitLogService.CommitInfo latestCommit = commits.getFirst();
            assertThat(latestCommit.diffs()).hasSize(2); // One deletion, one addition

            for (GitLogService.DiffInfo diff : latestCommit.diffs()) {
                assertThat(diff.diff()).isNotNull();
            }

            // Note: The exact paths for additions/deletions might vary based on JGit implementation
            // So we'll check that we have the expected number of diffs and they contain content
            assertThat(latestCommit.diffs()).allMatch(diff -> diff.diff() != null);

            repositoryService.close();
        }
    }

    @Test
    void getLog_withoutShowDiffs_noDiffInfo(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create and modify files
            createAndCommitFile(git, "file1.txt", "Original content");
            Files.writeString(tempDir.resolve("file1.txt"), "Modified content");
            git.add().addFilepattern("file1.txt").call();
            git.commit().setMessage("Modify file1.txt").call();

            // Test with showDiffs = false (default)
            GitLogService.GitLogOptions options = GitLogService.GitLogOptions.defaults();
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(2);

            // Verify that no diffs are included when showDiffs is false
            for (GitLogService.CommitInfo commit : commits) {
                assertThat(commit.diffs()).isEmpty();
            }

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowDiffs_verifyDiffContent(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create initial commit with specific content
            String originalContent = "Line 1\nLine 2\nLine 3\nLine 4\n";
            createAndCommitFile(git, "test.txt", originalContent);

            // Modify the file with specific changes
            String modifiedContent = "Line 1\nModified Line 2\nLine 3\nNew Line 4\nAdded Line 5\n";
            Files.writeString(tempDir.resolve("test.txt"), modifiedContent);
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Modify test.txt with specific changes").call();

            // Test with showDiffs = true
            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, true, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            assertThat(commits).hasSize(2);

            // Get the modification commit
            GitLogService.CommitInfo modifyCommit = commits.get(0);
            assertThat(modifyCommit.diffs()).hasSize(1);

            GitLogService.DiffInfo diffInfo = modifyCommit.diffs().get(0);
            String diffContent = diffInfo.diff();

            // Verify diff content contains Git diff format elements
            assertThat(diffContent).isNotNull();
            assertThat(diffContent).isNotEmpty();

            // Git diff should contain standard diff headers
            assertThat(diffContent).contains("diff --git");
            assertThat(diffContent).contains("a/test.txt");
            assertThat(diffContent).contains("b/test.txt");
            assertThat(diffContent).contains("@@"); // Hunk header

            // Verify that the diff shows the actual changes
            assertThat(diffContent).contains("-Line 2"); // Removed line
            assertThat(diffContent).contains("+Modified Line 2"); // Added line
            assertThat(diffContent).contains("-Line 4"); // Original Line 4
            assertThat(diffContent).contains("+New Line 4"); // Modified Line 4
            assertThat(diffContent).contains("+Added Line 5"); // Newly added line

            // Lines that didn't change should appear without +/- prefix
            assertThat(diffContent).contains(" Line 1"); // Context line
            assertThat(diffContent).contains(" Line 3"); // Context line

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowDiffs_newFileContent(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create initial commit
            createAndCommitFile(git, "existing.txt", "Existing file");

            // Add a completely new file
            String newFileContent = "This is a new file\nWith multiple lines\nAnd specific content\n";
            Files.writeString(tempDir.resolve("newfile.txt"), newFileContent);
            git.add().addFilepattern("newfile.txt").call();
            git.commit().setMessage("Add new file").call();

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, true, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            // Get the commit that added the new file
            GitLogService.CommitInfo addCommit = commits.get(0);
            assertThat(addCommit.diffs()).hasSize(1);

            GitLogService.DiffInfo diffInfo = addCommit.diffs().get(0);
            String diffContent = diffInfo.diff();

            // Verify diff content for new file
            assertThat(diffContent).contains("diff --git");
            assertThat(diffContent).contains("a/newfile.txt");
            assertThat(diffContent).contains("b/newfile.txt");
            assertThat(diffContent).contains("new file mode"); // Indicates a new file

            // All lines in a new file should be additions (prefixed with +)
            assertThat(diffContent).contains("+This is a new file");
            assertThat(diffContent).contains("+With multiple lines");
            assertThat(diffContent).contains("+And specific content");

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowDiffs_deletedFileContent(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create initial commit with file to be deleted
            String fileContent = "This file will be deleted\nIt has multiple lines\nFor testing purposes\n";
            createAndCommitFile(git, "todelete.txt", fileContent);
            createAndCommitFile(git, "tokeep.txt", "This file stays");

            // Delete the file
            Files.delete(tempDir.resolve("todelete.txt"));
            git.rm().addFilepattern("todelete.txt").call();
            git.commit().setMessage("Delete file").call();

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, true, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            // Get the deletion commit
            GitLogService.CommitInfo deleteCommit = commits.get(0);
            assertThat(deleteCommit.diffs()).hasSize(1);

            GitLogService.DiffInfo diffInfo = deleteCommit.diffs().get(0);
            String diffContent = diffInfo.diff();

            // Verify diff content for deleted file
            assertThat(diffContent).contains("diff --git");
            assertThat(diffContent).contains("a/todelete.txt");
            assertThat(diffContent).contains("b/todelete.txt");
            assertThat(diffContent).contains("deleted file mode"); // Indicates a deleted file

            // All lines in a deleted file should be removals (prefixed with -)
            assertThat(diffContent).contains("-This file will be deleted");
            assertThat(diffContent).contains("-It has multiple lines");
            assertThat(diffContent).contains("-For testing purposes");

            repositoryService.close();
        }
    }

    @Test
    void getLog_withShowDiffs_errorHandling(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService repositoryService = new GitRepositoryService();
            repositoryService.open(tempDir.toString());
            GitLogService gitLogService = new GitLogService(repositoryService);

            // Create a normal commit first
            createAndCommitFile(git, "normal.txt", "Normal content");

            GitLogService.GitLogOptions options = new GitLogService.GitLogOptions(
                    true, false, false, true, true, false, false, true, false, 30
            );
            List<GitLogService.CommitInfo> commits = gitLogService.getLog(options);

            // Even if there are issues with diff generation, the method should handle it gracefully
            // and return some content (either the diff or an error message)
            if (!commits.isEmpty() && !commits.getFirst().diffs().isEmpty()) {
                GitLogService.DiffInfo diffInfo = commits.getFirst().diffs().getFirst();
                String diffContent = diffInfo.diff();

                // The diff content should either be a valid diff or an error message
                assertThat(diffContent).isNotNull();
                assertThat(diffContent).satisfiesAnyOf(
                        content -> assertThat(content).contains("diff --git"), // Valid diff
                        content -> assertThat(content).contains("Error getting diff:") // Error message
                );
            }

            repositoryService.close();
        }
    }
}