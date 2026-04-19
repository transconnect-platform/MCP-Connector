package io.transconnect.connector.mcp;

import io.transconnect.connector.MockConfig;
import io.transconnect.connector.MockContext;
import io.transconnect.connector.MockMessage;
import io.transconnect.connector.MockWritableMessage;
import io.transconnect.connector.api.Configuration;
import io.transconnect.connector.api.Context;
import io.transconnect.connector.extension.jaxb.JaxbExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class McpClientConnectionTest {
    private Context ctx;

    @BeforeEach
    void setUp() {
        Configuration cfg = new MockConfig(Map.of(
                "transport", "STDIO",
                "server", "npx -y @modelcontextprotocol/server-everything stdio"
        ));
        ctx = new MockContext(cfg, List.of(new JaxbExtension()));
    }

    @Test
    void connect() throws Exception {
        try (McpClientConnection cut = new McpClientConnection(ctx)) {
            cut.connect();
            assertTrue(cut.isConnected());
        }
    }

    @Test
    void executeEcho() throws Exception{
        try (McpClientConnection cut = new McpClientConnection(ctx)) {
            cut.connect();
            assertTrue(cut.isConnected());
            MockWritableMessage mockWritableMessage = new MockWritableMessage();
            cut.execute(URI.create("EXECUTE"), new MockMessage("""
                    <ROOT xmlns="http://transconnect.io/connector/mcp/execute/in">
                        <TOOL NAME="echo">
                            <PARAM NAME="message" TYPE="string">Hello World</PARAM>
                        </TOOL>
                    </ROOT>
                    """), mockWritableMessage);
            assertEquals("""
                               <?xml version="1.0" encoding="UTF-8"?>
                               <ROOT xmlns="http://transconnect.io/connector/mcp/execute/out">
                                  <RETURN NAME="echo">
                                     <DATA><![CDATA[Echo: Hello World]]></DATA>
                                  </RETURN>
                               </ROOT>
                               """, new String(mockWritableMessage.getBody()));
        }
    }

    @Test
    void executeGetTinyImage() throws Exception{
        try (McpClientConnection cut = new McpClientConnection(ctx)) {
            cut.connect();
            assertTrue(cut.isConnected());
            MockWritableMessage mockWritableMessage = new MockWritableMessage();
            cut.execute(URI.create("EXECUTE"), new MockMessage("""
                    <ROOT xmlns="http://transconnect.io/connector/mcp/execute/in">
                        <TOOL NAME="get-tiny-image"/>
                    </ROOT>
                    """), mockWritableMessage);

            assertEquals("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ROOT xmlns="http://transconnect.io/connector/mcp/execute/out">
                       <RETURN NAME="get-tiny-image">
                          <DATA><![CDATA[Here's the image you requested:The image above is the MCP logo.]]></DATA>
                       </RETURN>
                    </ROOT>
                    """, new String(mockWritableMessage.getBody()));

            assertEquals(1, mockWritableMessage.getAttachmentIds().size());

            String expectedAttachmentName = "image_0.png";
            assertEquals(expectedAttachmentName, mockWritableMessage.getAttachmentIds().iterator().next());

            byte[] attachmentData = mockWritableMessage.getAttachment(expectedAttachmentName).readAllBytes();
            assertEquals(4033, attachmentData.length);
        }
    }

    @Test
    void executeGetEmbeddedTextResource() throws Exception{
        try (McpClientConnection cut = new McpClientConnection(ctx)) {
            cut.connect();
            assertTrue(cut.isConnected());
            MockWritableMessage mockWritableMessage = new MockWritableMessage();
            cut.execute(URI.create("EXECUTE"), new MockMessage("""
                    <ROOT xmlns="http://transconnect.io/connector/mcp/execute/in">
                        <TOOL NAME="get-resource-reference">
                            <PARAM NAME="resourceType" TYPE="string">Text</PARAM>
                            <PARAM NAME="resourceId" TYPE="number">1</PARAM>
                        </TOOL>
                    </ROOT>
                    """), mockWritableMessage);

            assertEquals("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ROOT xmlns="http://transconnect.io/connector/mcp/execute/out">
                       <RETURN NAME="get-resource-reference">
                          <DATA><![CDATA[Returning resource reference for Resource 1:You can access this resource using the URI: demo://resource/dynamic/text/1]]></DATA>
                       </RETURN>
                    </ROOT>
                    """, new String(mockWritableMessage.getBody()));

            assertEquals(1, mockWritableMessage.getAttachmentIds().size());

            String expectedAttachmentName = "resource_0";
            assertEquals(expectedAttachmentName, mockWritableMessage.getAttachmentIds().iterator().next());

            String attachmentData = new String(mockWritableMessage.getAttachment(expectedAttachmentName).readAllBytes());
            assertThat(attachmentData).contains("Resource 1: This is a plaintext resource created at");
        }
    }

    @Test
    void executeGetEmbeddedBlobResource() throws Exception{
        try (McpClientConnection cut = new McpClientConnection(ctx)) {
            cut.connect();
            assertTrue(cut.isConnected());
            MockWritableMessage mockWritableMessage = new MockWritableMessage();
            cut.execute(URI.create("EXECUTE"), new MockMessage("""
                    <ROOT xmlns="http://transconnect.io/connector/mcp/execute/in">
                        <TOOL NAME="get-resource-reference">
                            <PARAM NAME="resourceType" TYPE="string">Blob</PARAM>
                            <PARAM NAME="resourceId" TYPE="number">1</PARAM>
                        </TOOL>
                    </ROOT>
                    """), mockWritableMessage);

            assertEquals("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ROOT xmlns="http://transconnect.io/connector/mcp/execute/out">
                       <RETURN NAME="get-resource-reference">
                          <DATA><![CDATA[Returning resource reference for Resource 1:You can access this resource using the URI: demo://resource/dynamic/blob/1]]></DATA>
                       </RETURN>
                    </ROOT>
                    """, new String(mockWritableMessage.getBody()));

            assertEquals(1, mockWritableMessage.getAttachmentIds().size());

            String expectedAttachmentName = "resource_0";
            assertEquals(expectedAttachmentName, mockWritableMessage.getAttachmentIds().iterator().next());

            String attachmentData = new String(Base64.getDecoder().decode(mockWritableMessage.getAttachment(expectedAttachmentName).readAllBytes()));
            assertThat(attachmentData).contains("Resource 1: This is a base64 blob created at");
        }
    }
}