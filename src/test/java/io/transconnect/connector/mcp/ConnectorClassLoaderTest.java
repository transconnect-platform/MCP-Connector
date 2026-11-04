/*
 * (c) Copyright 2026 SQL Projekt AG. All rights reserved.
 */

package io.transconnect.connector.mcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * TRANSCONNECT loads the connector in its own class loader while the thread context class loader cannot see the
 * connector's libraries. The MCP SDK looks up its JSON implementation with {@link java.util.ServiceLoader} via the
 * context class loader ("No McpJsonMapperSupplier available for creating McpJsonMapper").
 */
class ConnectorClassLoaderTest {

    @Test
    void mcpJsonDefaultsAreAvailableWhenContextClassLoaderCannotSeeConnectorLibraries() throws Exception {
        URL[] classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(entry -> {
                    try {
                        return Path.of(entry).toUri().toURL();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toArray(URL[]::new);

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        // fresh copy of connector and MCP SDK, nothing cached yet
        try (URLClassLoader connectorClassLoader =
                new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader())) {
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());

            Class.forName(McpClientConnection.class.getName(), true, connectorClassLoader);

            Class<?> defaults =
                    Class.forName("io.modelcontextprotocol.json.McpJsonDefaults", true, connectorClassLoader);
            assertNotNull(defaults.getMethod("getMapper").invoke(null));
            assertNotNull(defaults.getMethod("getSchemaValidator").invoke(null));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
