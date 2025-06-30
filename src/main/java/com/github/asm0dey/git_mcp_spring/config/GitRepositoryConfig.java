package com.github.asm0dey.git_mcp_spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "git")
public record GitRepositoryConfig(String name, String path) { }
