package com.github.asm0dey.git_mcp_spring;

import com.github.asm0dey.git_mcp_spring.config.GitRepositoryConfig;
import com.github.asm0dey.git_mcp_spring.service.GitBisectService;
import com.github.asm0dey.git_mcp_spring.service.GitBranchService;
import com.github.asm0dey.git_mcp_spring.service.GitCommitOperationsService;
import com.github.asm0dey.git_mcp_spring.service.GitCommitService;
import com.github.asm0dey.git_mcp_spring.service.GitRebaseService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(GitRepositoryConfig.class)
public class GitMcpSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitMcpSpringApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider weatherTools(GitBranchService gitBranchService,
                                             GitCommitOperationsService gitCommitOperationsService,
                                             GitCommitService gitCommitService,
                                             GitRebaseService gitRebaseService,
                                             GitBisectService gitBisectService) {
        return MethodToolCallbackProvider.builder().toolObjects(gitBranchService,
                gitCommitOperationsService,
                gitCommitService,
                gitRebaseService,
                gitBisectService).build();
    }

}
