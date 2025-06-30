package com.github.asm0dey.git_mcp_spring;

import com.github.asm0dey.git_mcp_spring.service.GitLogService;
import com.github.asm0dey.git_mcp_spring.service.GitReflogService;
import com.github.asm0dey.git_mcp_spring.service.GitRepositoryService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GitMcpSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitMcpSpringApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider weatherTools(
            GitRepositoryService repo,
            GitLogService log,
            GitReflogService reflog) {
        return MethodToolCallbackProvider.builder().toolObjects(repo, reflog, log).build();
    }

}
