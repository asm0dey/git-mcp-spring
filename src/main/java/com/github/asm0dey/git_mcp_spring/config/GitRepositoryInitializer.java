package com.github.asm0dey.git_mcp_spring.config;

import com.github.asm0dey.git_mcp_spring.resource.GitRepositoryResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class GitRepositoryInitializer {
    private final GitRepositoryConfig gitRepositoryConfig;

    public GitRepositoryInitializer(GitRepositoryConfig gitRepositoryConfig) {
        this.gitRepositoryConfig = gitRepositoryConfig;
    }

    @Bean
    public GitRepositoryResource gitRepositoryResources() {
        GitRepositoryResource gitRepositoryResource = new GitRepositoryResource(gitRepositoryConfig.name(), Path.of(gitRepositoryConfig.path()));
        gitRepositoryResource.open();
        return gitRepositoryResource;
    }
}
