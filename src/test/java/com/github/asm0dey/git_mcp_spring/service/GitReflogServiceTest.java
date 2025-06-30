package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.model.Result;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GitReflogServiceTest {
    private void createAndCommitFile(Git git, String filename, String content) throws Exception {
        Path filePath = git.getRepository().getWorkTree().toPath().resolve(filename);
        Files.writeString(filePath, content);
        git.add().addFilepattern(filename).call();
        git.commit().setMessage("Add " + filename).call();
    }

    @Test
    void checkFirstReflogEntry() {
        GitRepositoryService gitRepository = new GitRepositoryService();
        gitRepository.open(Paths.get(System.getProperty("user.dir")));
        GitReflogService reflogService = new GitReflogService(gitRepository);
        Result<List<GitReflogService.ReflogEntry>> outRef = reflogService.getReflog(null, 0);
        var reflog = ((Result.Success<List<GitReflogService.ReflogEntry>>) outRef).value();

        assertEquals("commit (initial): Init", reflog.getLast().message());
        assertEquals("0000000000000000000000000000000000000000", reflog.getLast().oldId());
        assertEquals(reflog.size(), reflog.getLast().index() + 1);
    }


    @Test
    void testReflogWithNewRepository(@TempDir Path tempDir) throws Exception {
        // Initialize new repository
        try (var git = Git.init().setDirectory(tempDir.toFile()).call()) {
            GitRepositoryService gitRepository = new GitRepositoryService();
            gitRepository.open(tempDir);
            GitReflogService reflogService = new GitReflogService(gitRepository);

            // Create initial commits
            createAndCommitFile(git, "file1.txt", "content1");
            createAndCommitFile(git, "file2.txt", "content2");
            String secondCommitId = git.getRepository().resolve("HEAD").name();
            createAndCommitFile(git, "file3.txt", "content3");

            // Verify reflog after commits
            Result<List<GitReflogService.ReflogEntry>> tmp = reflogService.getReflog(null, 0);
            assertThat(tmp).isInstanceOf(Result.Success.class);
            var reflog = ((Result.Success<List<GitReflogService.ReflogEntry>>) tmp).value();
            assertThat(reflog).hasSize(3);
            for (int i = 0; i < reflog.size(); i++) {
                assertThat(reflog.get(i).message()).contains("Add file" + (reflog.size() - i) + ".txt");
            }

            // Perform reset and verify
            git.reset().setRef(secondCommitId).setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call();
            reflog = ((Result.Success<List<GitReflogService.ReflogEntry>>) reflogService.getReflog(null, 0)).value();
            assertThat(reflog).hasSize(4);
            assertThat(reflog.getFirst().message()).contains(": updating HEAD");
            assertThat(git.log().setMaxCount(1).call().iterator().next().getFullMessage()).isEqualTo("Add file2.txt");
            assertThat(Paths.get(tempDir.toString(), "file1.txt")).exists();
            assertThat(Paths.get(tempDir.toString(), "file2.txt")).exists();
            assertThat(Paths.get(tempDir.toString(), "file3.txt")).doesNotExist();
        }

    }
}