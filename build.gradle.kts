plugins {
    java
    application
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native")
}

group = "com.github.asm0dey"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.1.0"

dependencies {
    // JGit for Git operations
    implementation(libs.org.eclipse.jgit)
    implementation(libs.spring.ai.starter.mcp.server.webmvc)

    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.assertj.core)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("com.github.asm0dey.git_mcp_spring.GitMcpSpringApplication")
}
