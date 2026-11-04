/*
 * (c) Copyright 2026 SQL Projekt AG. All rights reserved.
 */

package io.transconnect.connector.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the connector against a real MCP server (HTTP with SSE), see {@link McpTestServer}.
 */
class McpClientConnectionTest {

    private static final String EXECUTE_NS = "http://transconnect.io/connector/mcp/execute/in";

    private static McpTestServer server;

    private MockContext context;

    private JaxbExtension jaxb;

    @BeforeAll
    static void startServer() throws Exception {
        server = new McpTestServer();
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.close();
    }

    @BeforeEach
    void createContext() throws TransconnectConnectorException {
        // mutable, MockConfig.setValue writes into this map
        MockConfig config = new MockConfig(new HashMap<>(Map.of(
                "transport", Transport.HTTP_SSE.name(), "server", server.url(), "requestTimeout", 10_000L)));
        context = new MockContext(config, new ExtensionManager(McpClientConnector.class).getExtensions());
        jaxb = context.getExtension(JaxbExtension.class).orElseThrow();
    }

    @Test
    void connectAndClose() throws Exception {
        ConsumerConnection connection = new McpClientConnector().createConnection(context);
        assertFalse(connection.isConnected());

        connection.connect();
        assertTrue(connection.isConnected());

        connection.close();
    }

    @Test
    void listToolsReturnsAllToolsOfTheServer() throws Exception {
        MockWritableMessage output =
                execute("listtools", "<ROOT xmlns=\"http://transconnect.io/connector/mcp/listtools/in\"/>");

        var tools = jaxb.unmarshalMessage(
                        toMessage(output), io.transconnect.connector.mcp.listtools.out.ROOT.class, null, null)
                .getRETURN()
                .getTOOL();
        assertEquals(List.of("echo", "add", "not", "image", "env", "sleep"), tools.stream().map(t -> t.getNAME()).toList());
        assertEquals("Adds two numbers", tools.get(1).getDESCRIPTION());
    }

    @Test
    void executeRunsAllToolsInOrderWithTypedParameters() throws Exception {
        MockWritableMessage output = execute("execute", """
                <ROOT xmlns="%s">
                    <TOOL NAME="echo"><PARAM NAME="text" TYPE="string">Hello MCP</PARAM></TOOL>
                    <TOOL NAME="add">
                        <PARAM NAME="a" TYPE="number">2</PARAM>
                        <PARAM NAME="b" TYPE="number">3</PARAM>
                    </TOOL>
                    <TOOL NAME="not"><PARAM NAME="value" TYPE="boolean">true</PARAM></TOOL>
                </ROOT>""".formatted(EXECUTE_NS));

        var returns = unmarshalExecuteResult(output);
        assertEquals(List.of("echo", "add", "not"), returns.stream().map(r -> r.getNAME()).toList());
        assertEquals(List.of("Hello MCP", "5", "false"), returns.stream().map(r -> r.getDATA()).toList());
    }

    @Test
    void executeWithNonTextResultReturnsEmptyData() throws Exception {
        MockWritableMessage output =
                execute("execute", "<ROOT xmlns=\"%s\"><TOOL NAME=\"image\"/></ROOT>".formatted(EXECUTE_NS));

        var ret = unmarshalExecuteResult(output).get(0);
        assertEquals("image", ret.getNAME());
        assertEquals("", ret.getDATA());
    }

    @Test
    void executeWithUnsupportedParameterTypeFails() {
        String input = "<ROOT xmlns=\"%s\"><TOOL NAME=\"echo\"><PARAM NAME=\"text\" TYPE=\"object\">{}</PARAM></TOOL></ROOT>"
                .formatted(EXECUTE_NS);

        var exception = assertThrows(IllegalArgumentException.class, () -> execute("execute", input));
        assertTrue(exception.getMessage().contains("Unsupported parameter type 'object'"), exception.getMessage());
    }

    @Test
    void executeFailsWhenToolTakesLongerThanRequestTimeout() {
        ((MockConfig) context.getConfiguration()).setValue("requestTimeout", 500L);
        String input = "<ROOT xmlns=\"%s\"><TOOL NAME=\"sleep\"><PARAM NAME=\"millis\" TYPE=\"number\">3000</PARAM></TOOL></ROOT>"
                .formatted(EXECUTE_NS);

        var exception = assertThrows(RuntimeException.class, () -> execute("execute", input));
        assertTrue(exception.toString().contains("Timeout"), exception::toString);
    }

    @Test
    void unknownInteractionFails() {
        assertThrows(TransconnectConnectorException.class, () -> execute("unknown", "<ROOT/>"));
    }

    private MockWritableMessage execute(String interaction, String input) throws Exception {
        MockWritableMessage output = new MockWritableMessage();
        try (ConsumerConnection connection = new McpClientConnector().createConnection(context)) {
            connection.connect();
            connection.execute(URI.create(interaction), new MockMessage(input), output);
        }
        return output;
    }

    private List<io.transconnect.connector.mcp.execute.out.ROOT.RETURN> unmarshalExecuteResult(
            MockWritableMessage output) throws TransconnectConnectorException {
        return jaxb.unmarshalMessage(toMessage(output), io.transconnect.connector.mcp.execute.out.ROOT.class, null, null)
                .getRETURN();
    }

    private static MockMessage toMessage(MockWritableMessage output) {
        return new MockMessage(new String(output.getBody(), StandardCharsets.UTF_8));
    }
}
