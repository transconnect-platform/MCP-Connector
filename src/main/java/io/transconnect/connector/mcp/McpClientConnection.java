/*
 * (c) Copyright 2025 SQL Projekt AG. All rights reserved.
 */

package io.transconnect.connector.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents;
import io.transconnect.connector.api.Configuration;
import io.transconnect.connector.api.Context;
import io.transconnect.connector.api.TransconnectConnectorException;
import io.transconnect.connector.api.consumer.ConsumerConnection;
import io.transconnect.connector.api.message.Message;
import io.transconnect.connector.api.message.WritableMessage;
import io.transconnect.connector.extension.jaxb.JaxbExtension;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientConnection implements ConsumerConnection {
    private static final Logger LOG = LoggerFactory.getLogger(McpClientConnection.class);

    private final Configuration configuration;

    private final String uriExecute = "EXECUTE";

    private final String uriListTools = "LISTTOOLS";

    private final JaxbExtension jaxbExtension;

    private McpSyncClient mcpSyncClient;

    /**
     * Constructs a new McpClientConnection with the specified context.
     *
     * @param context the context containing configuration and other necessary parameters
     * @throws IllegalArgumentException if the context or its configuration is null
     */
    public McpClientConnection(Context context) {
        Preconditions.checkArgument(context != null, "context must not be null");
        Preconditions.checkArgument(context.getConfiguration() != null, "configuration must not be null");
        this.configuration = context.getConfiguration();
        this.jaxbExtension = context.getExtension(JaxbExtension.class)
                .orElseThrow(() -> new IllegalStateException("JaxbExtension is required but not available"));
    }

    @Override
    public void connect() throws TransconnectConnectorException {
        Transport transport =
                Transport.valueOf(configuration.getString("transport").orElseThrow());

        LOG.info("Connecting to MCP server using transport: {}", transport);

        McpClientTransport mcpTransport =
                switch (transport) {
                    case STDIO -> connectStdio();
                    case HTTP_SSE -> connectHttpSse();
                    case STREAMABLE_HTTP ->
                        throw new UnsupportedOperationException("STREAMABLE_HTTP transport is not supported yet");
                };

        // Create a sync client with custom configuration
        mcpSyncClient = McpClient.sync(mcpTransport)
                .requestTimeout(Duration.ofSeconds(10))
                .capabilities(ClientCapabilities.builder()
                        .roots(true) // Enable roots capability
                        .build())
                .build();

        // initialize connection
        mcpSyncClient.initialize();

        LOG.info("MCP client initialized successfully");
    }

    @Override
    public boolean isConnected() {
        return mcpSyncClient != null && mcpSyncClient.isInitialized();
    }

    @Override
    public void execute(URI interactionId, Message input, WritableMessage output)
            throws TransconnectConnectorException {
        switch (interactionId.toString()) {
            case uriExecute -> executeInteractionExecute(input, output);
            case uriListTools -> executeInteractionListTools(input, output);
            default -> throw new TransconnectConnectorException("Unknown interaction: " + interactionId);
        }
    }

    @Override
    public void close() throws IOException {
        if (mcpSyncClient != null) {
            mcpSyncClient.close();
        }
    }

    private void executeInteractionListTools(Message input, WritableMessage output)
            throws TransconnectConnectorException {
        var resultReturn = new io.transconnect.connector.mcp.listtools.out.ROOT.RETURN();

        var listToolsResult = mcpSyncClient.listTools();
        LOG.info("Found {} tools", listToolsResult.tools().size());

        for (var tool : listToolsResult.tools()) {
            io.transconnect.connector.mcp.listtools.out.ROOT.RETURN.TOOL resultTool =
                    new io.transconnect.connector.mcp.listtools.out.ROOT.RETURN.TOOL();
            resultTool.setNAME(tool.name());
            resultTool.setDESCRIPTION(tool.description());
            resultReturn.getTOOL().add(resultTool);
        }

        var resultRoot = new io.transconnect.connector.mcp.listtools.out.ROOT();
        resultRoot.setRETURN(resultReturn);

        jaxbExtension.marshalMessage(output, resultRoot, null);
    }

    private void executeInteractionExecute(Message input, WritableMessage output)
            throws TransconnectConnectorException {
        io.transconnect.connector.mcp.execute.in.ROOT root = jaxbExtension.unmarshalMessage(
                input, io.transconnect.connector.mcp.execute.in.ROOT.class, "schema/execute_in.xsd", null);

        var resultRoot = new io.transconnect.connector.mcp.execute.out.ROOT();

        // run each tool, one after the other
        for (io.transconnect.connector.mcp.execute.in.ROOT.TOOL tool : root.getTOOL()) {
            io.transconnect.connector.mcp.execute.out.ROOT.RETURN ret = executeTool(tool, output);
            resultRoot.getRETURN().add(ret);
        }

        jaxbExtension.marshalMessage(output, resultRoot, "bindings/moxy-binding-execute.xml");
    }

    /**
     * Executes the specified tool.
     *
     * @param tool the tool to be executed
     * @param output the writable message, only used if results are written to a message attachment
     * @return the result of the tool execution
     * @throws IllegalArgumentException if an unsupported parameter type is encountered
     */
    private io.transconnect.connector.mcp.execute.out.ROOT.RETURN executeTool(
            io.transconnect.connector.mcp.execute.in.ROOT.TOOL tool, WritableMessage output) {
        LOG.info("Executing tool: {}", tool.getNAME());

        // create tool request
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode parameters = mapper.createObjectNode();
        for (io.transconnect.connector.mcp.execute.in.ROOT.TOOL.PARAM param : tool.getPARAM()) {
            // handle primitive parameter types according to JSON RPC 2.0 specification
            // (https://www.jsonrpc.org/specification#conventions)
            switch (param.getTYPE()) {
                case "string" -> parameters.put(param.getNAME(), param.getValue());
                case "number" -> parameters.put(param.getNAME(), Integer.parseInt(param.getValue()));
                case "boolean" -> parameters.put(param.getNAME(), Boolean.parseBoolean(param.getValue()));
                default -> {
                    throw new IllegalArgumentException(String.format(
                            "Unsupported parameter type '%s' for parameter '%s' in tool '%s'",
                            param.getTYPE(), param.getNAME(), tool.getNAME()));
                }
            }
        }
        CallToolRequest callToolRequest = new CallToolRequest(tool.getNAME(), parameters.toString());

        // execute tool
        CallToolResult callToolResult = mcpSyncClient.callTool(callToolRequest);

        // evaluate tool result
        int imageCount = 0;
        int resourceCount = 0;
        StringBuilder textResultBuilder = new StringBuilder();
        for (Content content : callToolResult.content()) {
            if ("text".equals(content.type())) {
                TextContent textContent = (TextContent) content;
                textResultBuilder.append(textContent.text());
            }
            else if("image".equals(content.type())) {
                ImageContent imageContent = (ImageContent) content;

                // get file extension from MIME type (e.g. "image/png" -> "png")
                String fileExtension = com.google.common.net.MediaType.parse(imageContent.mimeType()).subtype();
                String attachmentId = String.format("image_%d.%s", imageCount++, fileExtension);
                try(OutputStream outputStream = output.getAttachmentOutputStream(attachmentId)) {
                    outputStream.write(Base64.getDecoder().decode(imageContent.data()));
                } catch (IOException e) {
                    LOG.error("Unable to add attachment '{}'", attachmentId, e);
                }

            } else if("resource".equals(content.type())) {
                EmbeddedResource embeddedResource = (EmbeddedResource) content;
                String attachmentId = String.format("resource_%d", resourceCount++);
                try(OutputStream outputStream = output.getAttachmentOutputStream(attachmentId)) {
                    if (embeddedResource.resource() instanceof TextResourceContents textResourceContents) {
                        outputStream.write(textResourceContents.text().getBytes());
                    }
                    else if (embeddedResource.resource() instanceof BlobResourceContents blobResourceContents) {
                        outputStream.write(blobResourceContents.blob().getBytes());
                    }
                } catch (IOException e) {
                    LOG.error("Unable to add attachment '{}'", attachmentId, e);
                }
            } else {
                LOG.warn(
                        "Unsupported content type '{}' in tool result for tool '{}'. Result will be empty.",
                        content.type(),
                        tool.getNAME());
            }
        }

        // create the return object
        io.transconnect.connector.mcp.execute.out.ROOT.RETURN ret =
                new io.transconnect.connector.mcp.execute.out.ROOT.RETURN();
        ret.setNAME(tool.getNAME());
        ret.setDATA(textResultBuilder.toString());

        return ret;
    }

    /**
     * Connects to the MCP server using the standard input/output transport.
     *
     * @return an instance of McpClientTransport configured for standard I/O
     */
    private McpClientTransport connectStdio() {
        String serverCommand = configuration.getString("server").orElseThrow();
        List<String> serverCommandParts =
                List.of(serverCommand.split(" ")); // Ensure the command is split into arguments
        Preconditions.checkArgument(!serverCommandParts.isEmpty(), "Server command must not be empty");

        ServerParameters.Builder paramsBuilder = ServerParameters.builder(serverCommandParts.get(0))
                .args(serverCommandParts.subList(1, serverCommandParts.size()));

        // add environment variables
        Optional<String> environment = configuration.getString("environment");
        if (environment.isPresent()) {
            List<String> environmentParts = List.of(environment.get().split(";"));
            for (String env : environmentParts) {
                String[] keyValue = env.split("=");
                Preconditions.checkArgument(keyValue.length == 2, "Environment variable must be in 'key=value' format");
                paramsBuilder.addEnvVar(keyValue[0], keyValue[1]);
            }
        }

        return new StdioClientTransport(paramsBuilder.build());
    }

    /**
     * Connects to the MCP server using HTTP Server-Sent Events (SSE) transport.
     *
     * @return an instance of McpClientTransport configured for HTTP SSE
     */
    private McpClientTransport connectHttpSse() {
        String serverUri = configuration.getString("server").orElseThrow();
        Preconditions.checkArgument(!serverUri.isBlank(), "Server URI must not be empty");
        return HttpClientSseClientTransport.builder(serverUri).build();
    }
}
