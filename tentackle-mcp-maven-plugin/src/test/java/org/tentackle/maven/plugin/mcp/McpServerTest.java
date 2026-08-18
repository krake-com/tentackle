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

import org.tentackle.model.ModelManager;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.helidon.webserver.WebServer;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Drives the server over HTTP, the way an MCP client does.
 * <p>
 * The unit tests call the tools directly; this one is what proves the wiring: that Helidon serves the MCP
 * endpoint, that the tools are announced with their schemas, and that a call reaches the model and comes back.
 */
public class McpServerTest {

  private static final String PATH = "/mcp";

  private ModelSession session;
  private WebServer server;
  private McpTestClient client;

  @BeforeClass
  public void startServer() throws Exception {
    session = ModelToolTest.createSession("mcp-server-test");
    server = WebServer.builder()
        .host("localhost")
        .port(0)      // ephemeral: parallel builds must not collide on a fixed port
        .routing(routing -> routing.addFeature(
            McpFeatureFactory.create(session, PATH, "tentackle-model-test", "1.0", 1000)))
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
  public void announcesEveryTool() throws Exception {
    String body = client.rpc("tools/list", "{}");
    for (String tool : new String[] { "model_overview", "model_list_entities", "model_find",
                                      "model_describe_entity", "model_source", "model_paths",
                                      "model_ddl", "model_validate", "model_reload" }) {
      assertTrue(body.contains("\"" + tool + '"'), tool + " is missing from tools/list");
    }
    assertTrue(body.contains("\"inputSchema\""), body);
  }

  @Test
  public void announcesWhichToolsOnlyReadTheModel() throws Exception {
    String body = client.rpc("tools/list", "{}");
    assertTrue(annotationsOf(body, "model_overview").contains("\"readOnlyHint\":true"), body);
    assertTrue(annotationsOf(body, "model_describe_entity").contains("\"readOnlyHint\":true"), body);
    // the two that reload the model change what the other tools see, even though they write nothing
    assertTrue(annotationsOf(body, "model_reload").contains("\"readOnlyHint\":false"), body);
    assertTrue(annotationsOf(body, "model_validate").contains("\"readOnlyHint\":false"), body);
  }

  /**
   * Gets the annotations a tool is announced with.
   *
   * @param toolsList the body of a tools/list response
   * @param toolName the tool name
   * @return the annotations object as JSON
   */
  private String annotationsOf(String toolsList, String toolName) {
    int tool = toolsList.indexOf("\"name\":\"" + toolName + '"');
    assertTrue(tool > 0, toolName + " is missing from tools/list");
    int annotations = toolsList.indexOf("\"annotations\":{", tool);
    assertTrue(annotations > 0, toolName + " is announced without annotations");
    return toolsList.substring(annotations, toolsList.indexOf('}', annotations) + 1);
  }

  @Test
  public void announcesTheResourcesAndThePrompt() throws Exception {
    assertTrue(client.rpc("resources/list", "{}").contains("tentackle-model://index"));
    assertTrue(client.rpc("resources/templates/list", "{}").contains("tentackle-model://entity/{name}"));
    assertTrue(client.rpc("prompts/list", "{}").contains("new-entity"));
  }

  @Test
  public void servesTheGrammarResource() throws Exception {
    String body = client.rpc("resources/read", "{\"uri\":\"tentackle-model://grammar/model-definition\"}");
    assertTrue(body.contains("Model Definition"), "the packaged grammar must be served, got: " +
                                                  body.substring(0, Math.min(400, body.length())));
    assertFalse(body.contains("not packaged with this plugin"), "the grammar is missing from the jar");
  }

  @Test
  public void servesAnEntityByResourceTemplate() throws Exception {
    String body = client.rpc("resources/read", "{\"uri\":\"tentackle-model://entity/Product\"}");
    assertTrue(body.contains("name := Product"), body);
  }

  @Test
  public void answersAToolCall() throws Exception {
    String body = client.callTool("model_overview", "{}");
    assertTrue(body.contains("entities:       4"), body);
    assertTrue(body.contains("\"isError\":false"), body);
  }

  @Test
  public void passesArgumentsThrough() throws Exception {
    String body = client.callTool("model_describe_entity",
                                  "{\"entity\":\"Order\",\"sections\":[\"aggregate\"],\"scope\":\"own\"}");
    assertTrue(body.contains("Customer"), body);
    assertTrue(body.contains("## aggregate"), body);
  }

  @Test
  public void acceptsABareStringWhereTheSchemaAsksForAnArray() throws Exception {
    // agents send a single value unwrapped often enough that failing the call would be a regular occurrence
    String body = client.callTool("model_find", "{\"pattern\":\"customer_id\",\"kinds\":\"column\"}");
    assertTrue(body.contains("\"isError\":false"), body);
    assertTrue(body.contains("customer_id"), body);
  }

  @Test
  public void acceptsACommaSeparatedStringWhereTheSchemaAsksForAnArray() throws Exception {
    String body = client.callTool("model_describe_entity", "{\"entity\":\"Order\",\"sections\":\"aggregate,udk\"}");
    assertTrue(body.contains("\"isError\":false"), body);
    assertTrue(body.contains("## aggregate"), body);
    assertTrue(body.contains("## unique domain key"), body);
  }

  @Test
  public void acceptsANumberAndABooleanSentAsStrings() throws Exception {
    String body = client.callTool("model_list_entities", "{\"limit\":\"2\"}");
    assertTrue(body.contains("\"isError\":false"), body);
    assertTrue(body.contains("4 of 4 entities, showing the first 2"), body);

    String source = client.callTool("model_source",
                                    "{\"entity\":\"Product\",\"format\":\"compact\",\"asComment\":\"true\"}");
    assertTrue(source.contains("@>"), source);
  }

  @Test
  public void reportsAnArgumentOfTheWrongTypeAsAToolError() throws Exception {
    String body = client.callTool("model_list_entities", "{\"limit\":\"many\"}");
    assertTrue(body.contains("\"isError\":true"), body);
    assertTrue(body.contains("not a number"), body);
  }

  @Test
  public void reportsAToolFailureAsAToolError() throws Exception {
    String body = client.callTool("model_describe_entity", "{\"entity\":\"NoSuchEntity\"}");
    assertTrue(body.contains("\"isError\":true"), body);
    assertTrue(body.contains("model_describe_entity failed"), body);
  }

  @Test
  public void validatesACandidateOverTheWire() throws Exception {
    String body = client.callTool("model_validate",
                                  "{\"source\":\"name := Wire\\ntable := wire\\nid := 9001\\nintegrity := none\\n" +
                                  "alias := wire\\n\\n## attributes\\nString(10)  a  acol  a column\\n\"}");
    assertTrue(body.contains("duplicate entity id 9001"), body);
  }

}
