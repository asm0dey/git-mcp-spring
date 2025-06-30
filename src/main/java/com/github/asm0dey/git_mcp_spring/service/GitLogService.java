package com.github.asm0dey.git_mcp_spring.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class GitLogService {
    private final GitRepositoryService repository;

    public GitLogService(GitRepositoryService repository) {
        this.repository = repository;
    }

    public record CommitInfo(
            String commitId,
            String shortMessage,
            String fullMessage,
            PersonInfo author,
            PersonInfo committer,
            Instant commitTime,
            List<String> tags,
            List<String> branches,
            List<FileChangeInfo> fileChanges,
            List<DiffInfo> diffs
    ) {
    }

    public record FileChangeInfo(
            String path,
            String changeType
    ) {
    }

    public record DiffInfo(
            String oldPath,
            String newPath,
            String diff
    ) {
    }

    public record PersonInfo(
            String name,
            String email,
            Instant when
    ) {
        static PersonInfo from(PersonIdent ident) {
            return new PersonInfo(
                    ident.getName(),
                    ident.getEmailAddress(),
                    ident.getWhenAsInstant()
            );
        }
    }

    public record GitLogOptions(
            boolean showAuthors,
            boolean showCommitters,
            boolean fullMessages,
            boolean showCommitIds,
            boolean showDates,
            boolean showTags,
            boolean showBranches,
            boolean showDiffs,
            boolean showFileChanges,
            int maxCount
    ) {
        public static GitLogOptions defaults() {
            return new GitLogOptions(true, false, false, true, true, false, false, false, false, 30);
        }
    }

    @Tool(name = "git_log", description = "Gets the log of commits")
    public List<CommitInfo> getLog(GitLogOptions options) throws GitAPIException {
        try (Git git = new Git(repository.getRepository())) {
            LogCommand logCommand = git.log();

            if (options.maxCount() > 0) {
                logCommand.setMaxCount(options.maxCount());
            }

            Iterable<RevCommit> logs;
            try {
                logs = logCommand.call();
            } catch (NoHeadException e) {
                return List.of();
            }

            return StreamSupport.stream(logs.spliterator(), false)
                    .map(commit -> {
                        try {
                            return toCommitInfo(commit, options, git);
                        } catch (GitAPIException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    private CommitInfo toCommitInfo(RevCommit commit, GitLogOptions options, Git git) throws GitAPIException {
        try {
            List<DiffEntry> diffs = Collections.emptyList();
            if (commit.getParentCount() > 0) {
                try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    formatter.setRepository(git.getRepository());
                    diffs = formatter.scan(commit.getParent(0).getTree(), commit.getTree());
                }
            }

            return new CommitInfo(
                    commit.getName(),
                    commit.getShortMessage(),
                    options.fullMessages() ? commit.getFullMessage() : commit.getShortMessage(),
                    PersonInfo.from(commit.getAuthorIdent()),
                    PersonInfo.from(commit.getCommitterIdent()),
                    Instant.ofEpochSecond(commit.getCommitTime()),
                    options.showTags() ? getTagsForCommit(commit, git) : List.of(),
                    options.showBranches() ? getBranchesForCommit(commit, git) : List.of(),
                    options.showFileChanges() ? diffs.stream()
                            .map(d -> new FileChangeInfo(d.getNewPath(), d.getChangeType().name()))
                            .collect(Collectors.toList()) : List.of(),
                    options.showDiffs() ? diffs.stream()
                            .map(d -> new DiffInfo(d.getOldPath(), d.getNewPath(), getDiffText(d, git.getRepository())))
                            .collect(Collectors.toList()) : List.of()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getDiffText(DiffEntry diff, Repository repository) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            formatter.format(diff);
            String diffText = out.toString(StandardCharsets.UTF_8);
            return diffText;
        } catch (IOException e) {
            return "Error getting diff: " + e.getMessage();
        }
    }

    private List<String> getTagsForCommit(RevCommit commit, Git git) throws GitAPIException {
        return git.tagList().call().stream()
                .filter(ref -> {
                    try {
                        Repository repo = repository.getRepository();
                        return repo.getRefDatabase().peel(ref).getPeeledObjectId().equals(commit.getId());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(ref -> ref.getName().replaceFirst("^refs/tags/", ""))
                .collect(Collectors.toList());
    }

    private List<String> getBranchesForCommit(RevCommit commit, Git git) throws GitAPIException {
        return git.branchList().call().stream()
                .filter(ref -> {
                    try {
                        return repository.getRepository().getRefDatabase()
                                .peel(ref).getObjectId().equals(commit.getId());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(ref -> ref.getName().replaceFirst("^refs/heads/", ""))
                .collect(Collectors.toList());
    }

    @Tool(name = "git_commit_info", description = "Gets commit information by commit ID or reference")
    public CommitInfo getCommitInfo(String commitIdOrRef, GitLogOptions options) throws GitAPIException {
        try (Git git = new Git(repository.getRepository())) {
            RevCommit commit;
            Repository repo = git.getRepository();
            try {
                commit = git.getRepository().parseCommit(repo.resolve(commitIdOrRef));
            } catch (Exception e) {
                commit = null;
            }
            if (commit == null) throw new IllegalArgumentException("Commit or reference not found: " + commitIdOrRef);
            return toCommitInfo(commit, options, git);
        }
    }
}

