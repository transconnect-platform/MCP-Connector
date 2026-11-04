/*
 * (c) Copyright 2026 SQL Projekt AG. All rights reserved.
 */

package io.transconnect.connector.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.transconnect.connector.MockConfig;
import io.transconnect.connector.MockContext;
import io.transconnect.connector.MockMessage;
import io.transconnect.connector.MockWritableMessage;
import io.transconnect.connector.api.TransconnectConnectorException;
import io.transconnect.connector.api.consumer.ConsumerConnection;
import io.transconnect.connector.api.extension.ExtensionManager;
import io.transconnect.connector.extension.jaxb.JaxbExtension;
import io.transconnect.connector.mcp.execute.out.ROOT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the STDIO transport by starting {@link McpTestServer#main} as a separate Java process.
 */
class StdioTransportTest {

    @Test
    void executeToolOnStdioServerWithEnvironment() throws Exception {
        // quoted, as the Java executable and the classpath may contain spaces
        String command = "\"%s\" -cp \"%s\" %s".formatted(
                ProcessHandle.current().info().command().orElseThrow(),
                System.getProperty("java.class.path"),
                McpTestServer.class.getName());
        MockConfig config = new MockConfig(Map.of(
                "transport", Transport.STDIO.name(),
                "server", command,
                "environment", "MCP_TEST_VALUE=hello stdio",
                "requestTimeout", 30_000L));
        MockContext context =
                new MockContext(config, new ExtensionManager(McpClientConnector.class).getExtensions());

        MockWritableMessage output = new MockWritableMessage();
        try (ConsumerConnection connection = new McpClientConnector().createConnection(context)) {
            connection.connect();
            connection.execute(
                    URI.create("execute"),
                    new MockMessage("""
                            <ROOT xmlns="http://transconnect.io/connector/mcp/execute/in">
                                <TOOL NAME="env"><PARAM NAME="name" TYPE="string">MCP_TEST_VALUE</PARAM></TOOL>
                            </ROOT>"""),
                    output);
        }

        ROOT result = context.getExtension(JaxbExtension.class)
                .orElseThrow()
                .unmarshalMessage(
                        new MockMessage(new String(output.getBody(), StandardCharsets.UTF_8)), ROOT.class, null, null);
        assertEquals("hello stdio", result.getRETURN().get(0).getDATA());
    }

    @Test
    void connectReportsWhyTheServerProcessCouldNotBeStarted() throws Exception {
        // e.g. the whole command enclosed in quotes is taken as name of the executable
        MockConfig config = new MockConfig(Map.of(
                "transport", Transport.STDIO.name(),
                "server", "\"npx @modelcontextprotocol/server-filesystem /temp\"",
                "requestTimeout", 1_000L));
        MockContext context =
                new MockContext(config, new ExtensionManager(McpClientConnector.class).getExtensions());

        try (ConsumerConnection connection = new McpClientConnector().createConnection(context)) {
            var exception = assertThrows(TransconnectConnectorException.class, connection::connect);
            assertTrue(
                    exception.getMessage().contains("npx @modelcontextprotocol/server-filesystem /temp"),
                    exception.getMessage());
        }
    }
}
