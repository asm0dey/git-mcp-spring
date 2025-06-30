package com.github.asm0dey.git_mcp_spring.service;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class GitReflogServiceTest {

    @Test
    void getReflog() {
        GitRepositoryResource gitRepository = new GitRepositoryResource("this", Paths.get("/home/finkel/work_self/git-mcp-spring"));
        gitRepository.open();
        GitReflogService reflogService = new GitReflogService(gitRepository);
        System.out.println(reflogService.getReflog(null, 0));

    }
}