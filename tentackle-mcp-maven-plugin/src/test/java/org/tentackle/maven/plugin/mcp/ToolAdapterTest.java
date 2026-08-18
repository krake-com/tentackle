/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.maven.plugin.mcp;

import org.tentackle.maven.plugin.mcp.tool.ModelTool;
import org.tentackle.maven.plugin.mcp.tool.SchemaBuilder;
import org.tentackle.maven.plugin.mcp.tool.ToolArguments;
import org.tentackle.maven.plugin.mcp.tool.ToolRegistry;
import org.tentackle.model.ModelManager;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.helidon.webserver.WebServer;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.testng.Assert.assertTrue;

/**
 * Tests what a client is told when a tool fails in a way nobody planned for.
 * <p>
 * A tool that throws something unforeseen must still come back as a tool error: an agent can read "failed
 * because the disk went away" and decide what to do, while a protocol error is a dead end. The tools are
 * therefore served through a registry holding deliberately broken ones.
 */
public class ToolAdapterTest {

  private static final String PATH = "/mcp";

  private ModelSession session;
  private WebServer server;
  private McpTestClient client;

  /**
   * A tool that fails with an exception the adapter does not know about.
   */
  private static class FailingTool implements ModelTool {

    private final String name;
    private final RuntimeException failure;

    FailingTool(String name, RuntimeException failure) {
      this.name = name;
      this.failure = failure;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getTitle() {
      return "Fails";
    }

    @Override
    public String getDescription() {
      return "Fails, to prove what a client is told when it does.";
    }

    @Override
    public String getInputSchema() {
      return new SchemaBuilder().build();
    }

    @Override
    public String execute(ModelSession modelSession, ToolArguments args) {
      throw failure;
    }
  }

  @BeforeClass
  public void startServer() throws Exception {
    session = ModelToolTest.createSession("mcp-adapter-test");
    ToolRegistry registry = new ToolRegistry(1000);
    registry.register(new FailingTool("model_unchecked",
                                      new UncheckedIOException(new IOException("the disk went away"))));
    registry.register(new FailingTool("model_speechless", new NullPointerException()));
    server = WebServer.builder()
        .host("localhost")
        .port(0)
        .routing(routing -> routing.addFeature(
            McpFeatureFactory.create(session, registry, PATH, "tentackle-model-test", "1.0")))
        .build()
        .start();
    client = new McpTestClient(server, PATH);
  }

  @AfterClass(alwaysRun = true)
  public void stopServer() {
    if (server != null) {
      server.stop();
    }
    if (session != null) {
      session.getModel().clearModel();
      ModelManager.getInstance().removeModel(session.getModel());
    }
  }


  @Test
  public void reportsAnUnexpectedFailureAsAToolError() throws Exception {
    String body = client.callTool("model_unchecked", "{}");
    assertTrue(body.contains("\"isError\":true"), body);
    assertTrue(body.contains("model_unchecked failed"), body);
    assertTrue(body.contains("the disk went away"), body);
  }

  @Test
  public void namesTheExceptionWhenItCarriesNoMessage() throws Exception {
    String body = client.callTool("model_speechless", "{}");
    assertTrue(body.contains("\"isError\":true"), body);
    assertTrue(body.contains("NullPointerException"), body);
  }

  @Test
  public void stillServesTheModelAfterAToolFailed() throws Exception {
    client.callTool("model_unchecked", "{}");
    assertTrue(client.callTool("model_overview", "{}").contains("entities:       4"));
  }

}
