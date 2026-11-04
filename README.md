# MCP Client Connector

[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.8-green.svg)](https://gradle.org/)

A TRANSCONNECT connector for integrating with Model Context Protocol (MCP) servers, enabling TRANSCONNECT to interact with various tools and data sources.

## Overview

The MCP Client Connector implements the [Model Context Protocol](https://modelcontextprotocol.io) as a TRANSCONNECT consumer connector. It provides seamless integration between TRANSCONNECT workflows and MCP servers, enabling access to MCP tools and resources.

### What is MCP?

The Model Context Protocol (MCP) is an open protocol that standardizes how applications provide context to Large Language Models (LLMs). This connector allows TRANSCONNECT to act as an MCP client, consuming capabilities from any MCP-compliant server.

## Features

- **Execute tools on MCP servers**: Invoke one or more tools per message with `string`, `number` and `boolean` parameters
- **List available tools from MCP servers**: Discover and enumerate all available tools
- **Transports**: STDIO (the connector starts the server process) and HTTP with SSE
- **Support for text content results**: Handle text-based responses from tool executions
- **TRANSCONNECT Integration**: Native integration with TRANSCONNECT's connector framework

## Quick Start

1. **Clone the repository**
   ```bash
   git clone https://github.com/transconnect-platform/MCP-Connector.git
   cd MCP-Connector
   ```

2. **Build the connector**
   ```bash
   ./gradlew build
   ```

3. **Deploy to TRANSCONNECT**
    - The WAR file will be in `build/libs`
    - Copy the file to `TC_USER_HOME/connectors`
    - Configure the connector through the TRANSCONNECT UI (see [Configuration](#configuration))

## Requirements

- Java 17 or higher
- Gradle 9.8 or higher (the included Gradle wrapper `./gradlew` downloads it automatically)
- TRANSCONNECT Connector SDK 1.2.1+
- MCP Java SDK 2.1.0+ (bundled in the WAR file)

## Building

Build the connector and run the tests using Gradle:

```bash
./gradlew build
```

This will create a WAR file in `build/libs/` that can be deployed to TRANSCONNECT.

## Project Structure

```
MCP-Connector/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/transconnect/connector/mcp/
│   │   │       ├── McpClientConnector.java      # Main connector implementation
│   │   │       ├── McpClientConnection.java     # Connection handler (MCP client, interactions)
│   │   │       └── Transport.java               # Supported transport protocols
│   │   └── resources/
│   │       ├── McpClientConnector.yaml          # Connector descriptor
│   │       ├── META-INF/services/               # ServiceLoader registration of the connector
│   │       ├── bindings/                        # MOXy bindings (CDATA output)
│   │       └── schema/                          # XML schemas of the interactions
│   └── test/
│       └── java/
│           └── io/transconnect/connector/mcp/
│               ├── McpClientConnectionTest.java # Tests against an MCP server via HTTP with SSE
│               ├── StdioTransportTest.java      # Tests against an MCP server via STDIO
│               └── McpTestServer.java           # MCP test server providing the test tools
├── build.gradle.kts                             # Build configuration
└── gradle/libs.versions.toml                    # Dependency versions
```

## Configuration

The connector is configured using the TRANSCONNECT Connector YAML descriptor located at `src/main/resources/McpClientConnector.yaml`. It defines the following properties:

| Property         | Description                                                                                                                                                                         |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `transport`      | `STDIO` or `HTTP_SSE` (default: `STDIO`)                                                                                                                                            |
| `server`         | STDIO: command and arguments to start the server, separated by spaces. Enclose parts containing spaces in double quotes, e.g. `"C:\Program Files\nodejs\npx.cmd" -y my-server`. HTTP with SSE: the server URL |
| `environment`    | STDIO only: environment variables for the server process in the form `KEY=VALUE`, separated by semicolons                                                                           |
| `requestTimeout` | Timeout in milliseconds for all requests to the server, including tool calls (default: `10000`)                                                                                     |

### Interactions

**`listtools`** lists the tools of the server:

```xml
<ROOT xmlns="http://transconnect.io/connector/mcp/listtools/in"/>
```

**`execute`** runs the given tools one after the other. Each tool result is returned as `RETURN` element, the text content is wrapped in CDATA:

```xml
<ROOT xmlns="http://transconnect.io/connector/mcp/execute/in">
    <TOOL NAME="echo">
        <PARAM NAME="message" TYPE="string">Hello MCP</PARAM>
    </TOOL>
    <TOOL NAME="add">
        <PARAM NAME="a" TYPE="number">2</PARAM>
        <PARAM NAME="b" TYPE="number">3</PARAM>
    </TOOL>
</ROOT>
```

Supported parameter types are `string`, `number` (integer values) and `boolean`.

## Testing

`./gradlew test` runs the automated tests. They use the connector SDK test fixtures and start an MCP test server
providing a few tools, both via HTTP with SSE (embedded Tomcat) and via STDIO (separate Java process).
No external MCP server is required.

For manual testing you can use the [MCP Everything Server](https://github.com/modelcontextprotocol/servers/tree/main/src/everything) by setting the connector property `server` to

```
npx -y @modelcontextprotocol/server-everything stdio
```

## Open Points

- [ ] Add support for the Streamable HTTP transport (`STREAMABLE_HTTP`)
- [ ] Add support for other content results as `TextContent`
    - [ ] `ImageContent`
    - [ ] `EmbeddedResource`
- [ ] Check if `TextContent` contains well-known formats such as JSON
    - [ ] Transform to XML instead of dumping everything as CDATA

## License

This project is licensed under the MIT License.

Developed by SQL Projekt AG.

See [LICENSE.md](LICENSE.md) file for details.
