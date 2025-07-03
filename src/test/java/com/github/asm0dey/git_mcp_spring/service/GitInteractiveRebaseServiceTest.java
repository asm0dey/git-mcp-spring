package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.model.Result;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitInteractiveRebaseServiceTest {

    private void createAndCommitFile(Git git, String filename, String content) throws Exception {
        Path filePath = git.getRepository().getWorkTree().toPath().resolve(filename);
        Files.writeString(filePath, content);
        git.add().addFilepattern(filename).call();
        git.commit().setMessage("Add " + filename).call();
    }

    @Test
    void testRebaseStatusWithNoRepository() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

        Result<GitInteractiveRebaseService.RebaseStatus> result = rebaseService.getRebaseStatus();

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertEquals("Repository is not open", ((Result.Failure<GitInteractiveRebaseService.RebaseStatus>) result).message());
    }

    @Test
    void testRebaseStatusWithNoRebaseInProgress(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");

            Result<GitInteractiveRebaseService.RebaseStatus> result = rebaseService.getRebaseStatus();

            assertThat(result).isInstanceOf(Result.Success.class);
            var status = ((Result.Success<GitInteractiveRebaseService.RebaseStatus>) result).value();
            assertFalse(status.isInProgress());
            assertEquals(0, status.currentStep());
            assertEquals(0, status.totalSteps());
            assertThat(status.commits()).isEmpty();
            assertFalse(status.conflicted());
        }
    }

    @Test
    void testPreviewRebaseWithNoRepository() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

        Result<List<GitInteractiveRebaseService.RebaseCommit>> result = rebaseService.previewRebase("HEAD~2", 0);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertEquals("Repository is not open", ((Result.Failure<List<GitInteractiveRebaseService.RebaseCommit>>) result).message());
    }

    @Test
    void testPreviewRebaseWithInvalidBaseCommit(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");

            Result<List<GitInteractiveRebaseService.RebaseCommit>> result = rebaseService.previewRebase("invalid-commit", 0);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<List<GitInteractiveRebaseService.RebaseCommit>>) result).message())
                    .contains("Could not resolve base commit: invalid-commit");
        }
    }

    @Test
    void testPreviewRebaseWithValidCommits(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "content1");
            String firstCommitId = git.getRepository().resolve("HEAD").name();
            createAndCommitFile(git, "file2.txt", "content2");
            createAndCommitFile(git, "file3.txt", "content3");

            // Preview rebase from first commit
            Result<List<GitInteractiveRebaseService.RebaseCommit>> result = rebaseService.previewRebase(firstCommitId, 0);

            assertThat(result).isInstanceOf(Result.Success.class);
            var commits = ((Result.Success<List<GitInteractiveRebaseService.RebaseCommit>>) result).value();
            assertThat(commits).hasSize(2); // Should have 2 commits after the base commit

            // Check commit details
            GitInteractiveRebaseService.RebaseCommit firstCommit = commits.getFirst();
            assertThat(firstCommit.message()).isEqualTo("Add file3.txt");
            assertThat(firstCommit.action()).isEqualTo(GitInteractiveRebaseService.RebaseAction.PICK);
            assertThat(firstCommit.commitId()).hasSize(7); // Should be short format by default
            assertThat(firstCommit.author()).isNotEmpty();

            assertThat(commits.get(1).message()).isEqualTo("Add file2.txt");
            assertThat(commits.get(1).action()).isEqualTo(GitInteractiveRebaseService.RebaseAction.PICK);
        }
    }

    @Test
    void testPreviewRebaseWithMaxCount(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "content1");
            String firstCommitId = git.getRepository().resolve("HEAD").name();
            createAndCommitFile(git, "file2.txt", "content2");
            createAndCommitFile(git, "file3.txt", "content3");
            createAndCommitFile(git, "file4.txt", "content4");

            // Preview rebase with max count of 2
            Result<List<GitInteractiveRebaseService.RebaseCommit>> result = rebaseService.previewRebase(firstCommitId, 2);

            assertThat(result).isInstanceOf(Result.Success.class);
            var commits = ((Result.Success<List<GitInteractiveRebaseService.RebaseCommit>>) result).value();
            assertThat(commits).hasSize(2); // Should be limited to 2 commits
        }
    }

    @Test
    void testStartRebaseWithNoRepository() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

        var options = GitInteractiveRebaseService.RebaseOptions.defaults("HEAD~2");
        Result<String> result = rebaseService.startInteractiveRebase(options);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertEquals("Repository is not open", ((Result.Failure<String>) result).message());
    }

    @Test
    void testStartRebaseWithEmptyBaseCommit(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");

            var options = new GitInteractiveRebaseService.RebaseOptions("", false);
            Result<String> result = rebaseService.startInteractiveRebase(options);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertEquals("Base commit is required", ((Result.Failure<String>) result).message());
        }
    }

    @Test
    void testStartRebaseWithInvalidBaseCommit(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");

            var options = GitInteractiveRebaseService.RebaseOptions.defaults("invalid-commit");
            Result<String> result = rebaseService.startInteractiveRebase(options);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) result).message())
                    .contains("Could not resolve base commit: invalid-commit");
        }
    }

    @Test
    void testContinueRebaseWithNoRepository() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

        Result<String> result = rebaseService.continueRebase();

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertEquals("Repository is not open", ((Result.Failure<String>) result).message());
    }

    @Test
    void testContinueRebaseWithNoRebaseInProgress(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");

            Result<String> result = rebaseService.continueRebase();

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertEquals("No rebase in progress", ((Result.Failure<String>) result).message());
        }
    }

    @Test
    void testSkipRebaseWithNoRepository() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

        Result<String> result = rebaseService.skipRebase();

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertEquals("Repository is not open", ((Result.Failure<String>) result).message());
    }

    @Test
    void testSkipRebaseWithNoRebaseInProgress(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");

            Result<String> result = rebaseService.skipRebase();

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertEquals("No rebase in progress", ((Result.Failure<String>) result).message());
        }
    }

    @Test
    void testAbortRebaseWithNoRepository() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

        Result<String> result = rebaseService.abortRebase();

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertEquals("Repository is not open", ((Result.Failure<String>) result).message());
    }

    @Test
    void testAbortRebaseWithNoRebaseInProgress(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");

            Result<String> result = rebaseService.abortRebase();

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertEquals("No rebase in progress", ((Result.Failure<String>) result).message());
        }
    }

    @Test
    void testRebaseOptionsDefaults() {
        var options = GitInteractiveRebaseService.RebaseOptions.defaults("HEAD~3");

        assertEquals("HEAD~3", options.baseCommit());
        assertFalse(options.preserve());
    }

    @Test
    void testListCommitsWithNoRepository() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

        Result<List<GitInteractiveRebaseService.NumberedCommit>> result = rebaseService.listCommits("HEAD~5", 5);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertEquals("Repository is not open", ((Result.Failure<List<GitInteractiveRebaseService.NumberedCommit>>) result).message());
    }

    @Test
    void testListCommitsWithValidRepository(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");
            createAndCommitFile(git, "file3.txt", "content3");

            // List commits with default parameters
            Result<List<GitInteractiveRebaseService.NumberedCommit>> result = rebaseService.listCommits(null, 0);

            assertThat(result).isInstanceOf(Result.Success.class);
            var commits = ((Result.Success<List<GitInteractiveRebaseService.NumberedCommit>>) result).value();
            assertThat(commits).hasSize(3);

            // Check that commits have sequential numeric IDs
            assertThat(commits.get(0).id()).isEqualTo(1);
            assertThat(commits.get(1).id()).isEqualTo(2);
            assertThat(commits.get(2).id()).isEqualTo(3);

            // Check commit details
            GitInteractiveRebaseService.NumberedCommit firstCommit = commits.getFirst();
            assertThat(firstCommit.message()).isEqualTo("Add file3.txt");
            assertThat(firstCommit.commitId()).hasSize(7); // Should be short format by default
            assertThat(firstCommit.author()).isNotEmpty();
        }
    }

    @Test
    void testListCommitsWithMaxCount(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");
            createAndCommitFile(git, "file3.txt", "content3");
            createAndCommitFile(git, "file4.txt", "content4");

            // List commits with max count of 2 (no base commit specified)
            Result<List<GitInteractiveRebaseService.NumberedCommit>> result = rebaseService.listCommits(null, 2);

            assertThat(result).isInstanceOf(Result.Success.class);
            var commits = ((Result.Success<List<GitInteractiveRebaseService.NumberedCommit>>) result).value();
            assertThat(commits).hasSize(2); // Should be limited to 2 commits
        }
    }

    @Test
    void testPerformSimpleRebaseWithNoRepository() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

        var instructions = List.of(
                new GitInteractiveRebaseService.RebaseInstruction(GitInteractiveRebaseService.RebaseAction.PICK, 1, null)
        );
        Result<GitInteractiveRebaseService.RebaseExecutionResult> result = 
                rebaseService.performSimpleRebase("HEAD~2", instructions);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertEquals("Repository is not open", ((Result.Failure<GitInteractiveRebaseService.RebaseExecutionResult>) result).message());
    }

    @Test
    void testPerformSimpleRebaseWithEmptyInstructions(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");

            Result<GitInteractiveRebaseService.RebaseExecutionResult> result = 
                    rebaseService.performSimpleRebase("HEAD~1", List.of());

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertEquals("Rebase instructions are required", ((Result.Failure<GitInteractiveRebaseService.RebaseExecutionResult>) result).message());
        }
    }

    @Test
    void testPerformSimpleRebaseWithInvalidCommitId(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");

            var instructions = List.of(
                    new GitInteractiveRebaseService.RebaseInstruction(GitInteractiveRebaseService.RebaseAction.PICK, 999, null) // Invalid commit ID
            );
            Result<GitInteractiveRebaseService.RebaseExecutionResult> result = 
                    rebaseService.performSimpleRebase("HEAD~1", instructions);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<GitInteractiveRebaseService.RebaseExecutionResult>) result).message())
                    .contains("Invalid commit ID: 999");
        }
    }

    @Test
    void testPerformSimpleRebaseWithNullAction(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");

            var instructions = List.of(
                    new GitInteractiveRebaseService.RebaseInstruction(null, 1, null)
            );
            Result<GitInteractiveRebaseService.RebaseExecutionResult> result = 
                    rebaseService.performSimpleRebase("HEAD~1", instructions);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<GitInteractiveRebaseService.RebaseExecutionResult>) result).message())
                    .contains("Action is required for commit ID: 1");
        }
    }

    @Test
    void testRebaseInstructionRecord() {
        var instruction = new GitInteractiveRebaseService.RebaseInstruction(GitInteractiveRebaseService.RebaseAction.PICK, 1, "new message");

        assertEquals(GitInteractiveRebaseService.RebaseAction.PICK, instruction.action());
        assertEquals(1, instruction.commitId());
        assertEquals("new message", instruction.newMessage());
    }

    @Test
    void testNumberedCommitRecord() {
        var commit = new GitInteractiveRebaseService.NumberedCommit(
                1, "abc123", "Short message", "Author"
        );

        assertEquals(1, commit.id());
        assertEquals("abc123", commit.commitId());
        assertEquals("Short message", commit.message());
        assertEquals("Author", commit.author());
    }

    @Test
    void testRebaseExecutionResultRecord() {
        var result = new GitInteractiveRebaseService.RebaseExecutionResult(
                true, false, "Success", List.of()
        );

        assertTrue(result.success());
        assertFalse(result.hasConflicts());
        assertEquals("Success", result.message());
        assertThat(result.conflictedFiles()).isEmpty();
    }

    @Test
    void testListCommitsWithDisplaySettings(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create commits
            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");

            // Test short format (default)
            Result<List<GitInteractiveRebaseService.NumberedCommit>> shortResult = 
                    rebaseService.listCommits(null, 10, false, false);
            assertThat(shortResult).isInstanceOf(Result.Success.class);
            var shortCommits = ((Result.Success<List<GitInteractiveRebaseService.NumberedCommit>>) shortResult).value();

            // Test full format
            Result<List<GitInteractiveRebaseService.NumberedCommit>> fullResult = 
                    rebaseService.listCommits(null, 10, true, true);
            assertThat(fullResult).isInstanceOf(Result.Success.class);
            var fullCommits = ((Result.Success<List<GitInteractiveRebaseService.NumberedCommit>>) fullResult).value();

            // Verify short format has shorter commit IDs and messages
            assertThat(shortCommits.getFirst().commitId()).hasSize(7);
            assertThat(shortCommits.getFirst().message()).isEqualTo("Add file2.txt");

            // Verify full format has longer commit IDs and same messages (since they're the same in this case)
            assertThat(fullCommits.getFirst().commitId()).hasSize(40);
            assertThat(fullCommits.getFirst().message()).isEqualTo("Add file2.txt");
        }
    }

    @Test
    void testPreviewRebaseWithDisplaySettings(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create commits
            createAndCommitFile(git, "file1.txt", "content1");
            String firstCommitId = git.getRepository().resolve("HEAD").name();
            createAndCommitFile(git, "file2.txt", "content2");

            // Test short format (default)
            Result<List<GitInteractiveRebaseService.RebaseCommit>> shortResult = 
                    rebaseService.previewRebase(firstCommitId, 10, false, false);
            assertThat(shortResult).isInstanceOf(Result.Success.class);
            var shortCommits = ((Result.Success<List<GitInteractiveRebaseService.RebaseCommit>>) shortResult).value();

            // Test full format
            Result<List<GitInteractiveRebaseService.RebaseCommit>> fullResult = 
                    rebaseService.previewRebase(firstCommitId, 10, true, true);
            assertThat(fullResult).isInstanceOf(Result.Success.class);
            var fullCommits = ((Result.Success<List<GitInteractiveRebaseService.RebaseCommit>>) fullResult).value();

            // Verify short format has shorter commit IDs
            assertThat(shortCommits.getFirst().commitId()).hasSize(7);
            assertThat(shortCommits.getFirst().message()).isEqualTo("Add file2.txt");

            // Verify full format has longer commit IDs
            assertThat(fullCommits.getFirst().commitId()).hasSize(40);
            assertThat(fullCommits.getFirst().message()).isEqualTo("Add file2.txt");
        }
    }

    @Test
    void testCommitDisplaySettings() {
        var shortSettings = GitInteractiveRebaseService.CommitDisplaySettings.shortFormat();
        assertFalse(shortSettings.useFullCommitId());
        assertFalse(shortSettings.useFullMessage());

        var fullSettings = GitInteractiveRebaseService.CommitDisplaySettings.fullFormat();
        assertTrue(fullSettings.useFullCommitId());
        assertTrue(fullSettings.useFullMessage());

        var customSettings = GitInteractiveRebaseService.CommitDisplaySettings.custom(true, false);
        assertTrue(customSettings.useFullCommitId());
        assertFalse(customSettings.useFullMessage());
    }

    @Test
    void testRebaseActionEnum() {
        // Test enum values
        assertEquals("pick", GitInteractiveRebaseService.RebaseAction.PICK.getCommand());
        assertEquals("squash", GitInteractiveRebaseService.RebaseAction.SQUASH.getCommand());
        assertEquals("drop", GitInteractiveRebaseService.RebaseAction.DROP.getCommand());
        assertEquals("reword", GitInteractiveRebaseService.RebaseAction.REWORD.getCommand());
        assertEquals("edit", GitInteractiveRebaseService.RebaseAction.EDIT.getCommand());
        assertEquals("fixup", GitInteractiveRebaseService.RebaseAction.FIXUP.getCommand());

        // Test descriptions
        assertThat(GitInteractiveRebaseService.RebaseAction.PICK.getDescription()).isNotEmpty();
        assertThat(GitInteractiveRebaseService.RebaseAction.SQUASH.getDescription()).isNotEmpty();

        // Test fromCommand method
        assertEquals(GitInteractiveRebaseService.RebaseAction.PICK, 
                GitInteractiveRebaseService.RebaseAction.fromCommand("pick"));
        assertEquals(GitInteractiveRebaseService.RebaseAction.SQUASH, 
                GitInteractiveRebaseService.RebaseAction.fromCommand("SQUASH"));
        assertEquals(GitInteractiveRebaseService.RebaseAction.DROP, 
                GitInteractiveRebaseService.RebaseAction.fromCommand(" drop "));
        assertThat(GitInteractiveRebaseService.RebaseAction.fromCommand("invalid")).isNull();
        assertThat(GitInteractiveRebaseService.RebaseAction.fromCommand(null)).isNull();

        // Test toJGitAction method
        assertEquals(org.eclipse.jgit.lib.RebaseTodoLine.Action.PICK, 
                GitInteractiveRebaseService.RebaseAction.PICK.toJGitAction());
        assertEquals(org.eclipse.jgit.lib.RebaseTodoLine.Action.SQUASH, 
                GitInteractiveRebaseService.RebaseAction.SQUASH.toJGitAction());
        assertEquals(org.eclipse.jgit.lib.RebaseTodoLine.Action.COMMENT, 
                GitInteractiveRebaseService.RebaseAction.DROP.toJGitAction()); // DROP maps to COMMENT
        assertEquals(org.eclipse.jgit.lib.RebaseTodoLine.Action.REWORD, 
                GitInteractiveRebaseService.RebaseAction.REWORD.toJGitAction());
        assertEquals(org.eclipse.jgit.lib.RebaseTodoLine.Action.EDIT, 
                GitInteractiveRebaseService.RebaseAction.EDIT.toJGitAction());
        assertEquals(org.eclipse.jgit.lib.RebaseTodoLine.Action.FIXUP, 
                GitInteractiveRebaseService.RebaseAction.FIXUP.toJGitAction());
    }

    @Test
    void testPreviewRebaseToRoot(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");
            createAndCommitFile(git, "file3.txt", "content3");

            // Preview rebase to root (null baseCommit)
            Result<List<GitInteractiveRebaseService.RebaseCommit>> result = rebaseService.previewRebase(null, 0);

            assertThat(result).isInstanceOf(Result.Success.class);
            var commits = ((Result.Success<List<GitInteractiveRebaseService.RebaseCommit>>) result).value();
            assertThat(commits).hasSize(3); // Should include all commits

            // Verify commits are in reverse chronological order (newest first)
            assertThat(commits.get(0).message()).isEqualTo("Add file3.txt");
            assertThat(commits.get(1).message()).isEqualTo("Add file2.txt");
            assertThat(commits.get(2).message()).isEqualTo("Add file1.txt");
        }
    }

    @Test
    void testPreviewRebaseToRootWithEmptyString(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");

            // Preview rebase to root (empty string baseCommit)
            Result<List<GitInteractiveRebaseService.RebaseCommit>> result = rebaseService.previewRebase("", 0);

            assertThat(result).isInstanceOf(Result.Success.class);
            var commits = ((Result.Success<List<GitInteractiveRebaseService.RebaseCommit>>) result).value();
            assertThat(commits).hasSize(2); // Should include all commits
        }
    }

    @Test
    void testListCommitsToRoot(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");
            createAndCommitFile(git, "file3.txt", "content3");

            // List commits to root (null baseCommit)
            Result<List<GitInteractiveRebaseService.NumberedCommit>> result = rebaseService.listCommits(null, 0);

            assertThat(result).isInstanceOf(Result.Success.class);
            var commits = ((Result.Success<List<GitInteractiveRebaseService.NumberedCommit>>) result).value();
            assertThat(commits).hasSize(3); // Should include all commits

            // Verify commits have sequential IDs and are in reverse chronological order
            assertThat(commits.get(0).id()).isEqualTo(1);
            assertThat(commits.get(0).message()).isEqualTo("Add file3.txt");
            assertThat(commits.get(1).id()).isEqualTo(2);
            assertThat(commits.get(1).message()).isEqualTo("Add file2.txt");
            assertThat(commits.get(2).id()).isEqualTo(3);
            assertThat(commits.get(2).message()).isEqualTo("Add file1.txt");
        }
    }

    @Test
    void testPerformSimpleRebaseToRootValidation(@TempDir Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir.toAbsolutePath().toString());
            GitInteractiveRebaseService rebaseService = new GitInteractiveRebaseService(gitRepository);

            // Create multiple commits
            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");

            // Test that performSimpleRebase accepts null baseCommit and validates instructions
            var instructions = List.of(
                    new GitInteractiveRebaseService.RebaseInstruction(GitInteractiveRebaseService.RebaseAction.PICK, 1, null),
                    new GitInteractiveRebaseService.RebaseInstruction(GitInteractiveRebaseService.RebaseAction.PICK, 2, null)
            );

            // This should not fail due to null baseCommit, but might fail due to JGit limitations
            Result<GitInteractiveRebaseService.RebaseExecutionResult> result = 
                    rebaseService.performSimpleRebase(null, instructions);

            // The result might be success or failure depending on JGit's capabilities
            // but it should not fail with "Base commit is required"
            if (result instanceof Result.Failure<GitInteractiveRebaseService.RebaseExecutionResult>(String message)) {
                assertThat(message).doesNotContain("Base commit is required");
            }
        }
    }
}
