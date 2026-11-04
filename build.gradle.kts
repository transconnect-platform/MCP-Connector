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
    implementation(libs.connector.yaml.descriptor)
    implementation(libs.connector.jaxb)

    // MCP
    implementation(platform(libs.mcp.bom))
    implementation(libs.mcp)

    // Preconditions
    implementation(libs.guava)
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
    val connectorVersion = project.version.toString()
    inputs.property("version", connectorVersion)
    filesMatching("McpClientConnector.yaml") {
        expand("version" to connectorVersion)
    }
}