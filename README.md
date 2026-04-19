# MCP Client Connector

[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.8-green.svg)](https://gradle.org/)

A TRANSCONNECT connector for integrating with Model Context Protocol (MCP) servers, enabling TRANSCONNECT to interact with various tools and data sources.

## Overview

The MCP Client Connector implements the [Model Context Protocol](https://modelcontextprotocol.io) as a TRANSCONNECT consumer connector. It provides seamless integration between TRANSCONNECT workflows and MCP servers, enabling access to MCP tools and resources.

### What is MCP?

The Model Context Protocol (MCP) is an open protocol that standardizes how applications provide context to Large Language Models (LLMs). This connector allows TRANSCONNECT to act as an MCP client, consuming capabilities from any MCP-compliant server.

## Features

- **Execute tools on MCP servers**: Invoke tools provided by MCP servers with parameter support
- **List available tools from MCP servers**: Discover and enumerate all available tools
- **Support for text content results**: Handle text-based responses from tool executions
- **TRANSCONNECT Integration**: Native integration with TRANSCONNECT's connector framework

## Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd mcp-connector
   ```

2. **Build the connector**
   ```bash
   ./gradlew build
   ```

3. **Deploy to TRANSCONNECT**
    - The WAR file will be in `build/libs`
    - Copy the file to `TC_USER_HOME/connectors`

## Requirements

- Java 17 or higher
- Gradle 8.8 or higher
- TRANSCONNECT Connector SDK 0.9.6+

## Building

Build the connector using Gradle:

```bash
./gradlew build
```

This will create a WAR file in `build/libs/` that can be deployed to TRANSCONNECT.

## Project Structure

```
mcp-connector/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/transconnect/connector/mcp/
│   │   │       ├── McpClientConnector.java      # Main connector implementation
│   │   │       ├── McpClientConnection.java     # Connection handler
│   │   │       ├── Transport.java               # Transport abstraction
│   │   │       └── MarshallingFactory.java      # Message serialization
│   │   └── resources/
│   │       ├── McpClientConnector.yaml          # Connector descriptor
│           └── schema/                          # XML schemas
├── build.gradle.kts                             # Build configuration
├── gradle/libs.versions.toml                    # Dependency versions
└── settings.gradle.kts                          # Gradle settings
```

## Configuration

The connector is configured using the TRANSCONNECT Connector YAML descriptor located at `src/main/resources/McpClientConnector.yaml`.

## Development

1. Build the WAR file:
   ```bash
   ./gradlew build
   ```

2. Deploy the generated WAR file from `build/libs/` to your TRANSCONNECT instance

3. Configure the connector through the TRANSCONNECT UI

## Testing

For testing you can use the [MCP Everywhere Server](https://github.com/modelcontextprotocol/servers/tree/main/src/everything) by setting the connector property `server` to

```
npx -y @modelcontextprotocol/server-everything stdio
```

## Open Points

- [ ] Check if `TextContent` contains well-known formats such as JSON
    - [ ] Transform to XML instead of dumping everything as CDATA

## License

This project is licensed under the MIT License.

Developed by SQL Projekt AG.

See [LICENSE.md](LICENSE.md) file for details.