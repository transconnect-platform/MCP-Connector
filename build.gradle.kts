plugins {
    war
    java
    alias(libs.plugins.xjc)
}

group = "io.transconnect.connector"
version = "0.0.1"
description = "MCP (Model Context Protocol) Connector for TRANSCONNECT"

base {
    archivesName.set("mcp-connector")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    providedCompile(libs.connector.api)
    providedCompile(libs.slf4j.api)

    // Connector SDK
    implementation(libs.connector.war.bridge)
    implementation(libs.connector.yaml.descriptor)
    implementation(libs.connector.jaxb)

    // MCP
    implementation(platform(libs.mcp.bom))
    implementation(libs.mcp)

    // Preconditions
    implementation(libs.guava)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation(testFixtures(libs.connector.api))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.slf4j.simple)
}

xjc {
    xsdDir.set(file("$projectDir/src/main/resources/schema"))
}

// add WAR artifact to runtimeOnly configuration
artifacts {
    add("runtimeOnly", tasks.war)
}

// Replace the version in the Connector Descriptor
tasks.processResources {
    filesMatching("McpClientConnector.yaml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}
