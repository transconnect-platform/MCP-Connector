/*
 * (c) Copyright 2026 SQL Projekt AG. All rights reserved.
 */

package io.transconnect.connector.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

/**
 * In-process MCP server (HTTP with SSE) on a random local port, providing a few tools to test the connector.
 */
final class McpTestServer implements AutoCloseable {

    private final Tomcat tomcat = new Tomcat();

    private final McpSyncServer mcpServer;

    McpTestServer() throws Exception {
        HttpServletSseServerTransportProvider transport =
                HttpServletSseServerTransportProvider.builder().messageEndpoint("/mcp/message").build();

        mcpServer = McpServer.sync(transport)
                .serverInfo("mcp-test-server", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .toolCall(
                        tool("echo", "Returns the given text", Map.of("text", "string")),
                        (exchange, request) -> text(String.valueOf(request.arguments().get("text"))))
                .toolCall(
                        tool("add", "Adds two numbers", Map.of("a", "number", "b", "number")),
                        (exchange, request) -> text(String.valueOf(((Number) request.arguments().get("a")).longValue()
                                + ((Number) request.arguments().get("b")).longValue())))
                .toolCall(
                        tool("not", "Negates a boolean", Map.of("value", "boolean")),
                        (exchange, request) -> text(String.valueOf(!(Boolean) request.arguments().get("value"))))
                .toolCall(
                        tool("image", "Returns an image instead of text", Map.of()),
                        (exchange, request) -> CallToolResult.builder()
                                .addContent(new ImageContent(null, "iVBORw0KGgo=", "image/png", null))
                                .build())
                .build();

        tomcat.setBaseDir(Files.createTempDirectory("mcp-test-tomcat").toString());
        tomcat.setPort(0);
        Context context = tomcat.addContext("", null);
        Tomcat.addServlet(context, "mcp", transport).setAsyncSupported(true);
        context.addServletMapping("/*", "mcp");
        tomcat.getConnector(); // creates the default HTTP connector
        tomcat.start();
    }

    /** Base URL of the server, as configured in the connector's {@code server} property. */
    String url() {
        return "http://localhost:" + tomcat.getConnector().getLocalPort();
    }

    @Override
    public void close() throws Exception {
        mcpServer.closeGracefully();
        tomcat.stop();
        tomcat.destroy();
    }

    private static Tool tool(String name, String description, Map<String, String> parameterTypes) {
        Map<String, Object> properties = new LinkedHashMap<>();
        parameterTypes.forEach((param, type) -> properties.put(param, Map.of("type", type)));
        return Tool.builder(name, Map.of("type", "object", "properties", properties))
                .description(description)
                .build();
    }

    private static CallToolResult text(String text) {
        return CallToolResult.builder().addTextContent(text).build();
    }
}
