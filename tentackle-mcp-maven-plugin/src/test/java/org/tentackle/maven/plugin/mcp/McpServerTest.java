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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
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
  private HttpClient client;
  private String sessionId;

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
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    sessionId = initialize();
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


  private URI endpoint() {
    return URI.create("http://localhost:" + server.port() + PATH);
  }

  private HttpRequest.Builder request() {
    return HttpRequest.newBuilder(endpoint())
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream")
        .timeout(Duration.ofSeconds(30));
  }

  private String initialize() throws Exception {
    HttpResponse<String> response = client.send(
        request().POST(HttpRequest.BodyPublishers.ofString("""
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""")).build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(response.statusCode(), 200, response.body());
    assertTrue(response.body().contains("\"serverInfo\""), response.body());
    String id = response.headers().firstValue("Mcp-Session-Id").orElse(null);
    assertNotNull(id, "the server must open a session");

    // a notification carries no result, so the server acknowledges it with 202 rather than 200
    HttpResponse<String> accepted = post("""
        {"jsonrpc":"2.0","method":"notifications/initialized"}""", id);
    assertEquals(accepted.statusCode(), 202, accepted.body());
    return id;
  }

  private HttpResponse<String> post(String body, String id) throws Exception {
    return client.send(
        request().header("Mcp-Session-Id", id).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private String rpc(String method, String params) throws Exception {
    HttpResponse<String> response =
        post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"" + method + "\",\"params\":" + params + '}', sessionId);
    assertEquals(response.statusCode(), 200, response.body());
    return response.body();
  }

  private String callTool(String name, String arguments) throws Exception {
    return rpc("tools/call", "{\"name\":\"" + name + "\",\"arguments\":" + arguments + '}');
  }


  @Test
  public void announcesEveryTool() throws Exception {
    String body = rpc("tools/list", "{}");
    for (String tool : new String[] { "model_overview", "model_list_entities", "model_find",
                                      "model_describe_entity", "model_source", "model_paths",
                                      "model_ddl", "model_validate", "model_reload" }) {
      assertTrue(body.contains("\"" + tool + '"'), tool + " is missing from tools/list");
    }
    assertTrue(body.contains("\"inputSchema\""), body);
  }

  @Test
  public void announcesTheResourcesAndThePrompt() throws Exception {
    assertTrue(rpc("resources/list", "{}").contains("tentackle-model://index"));
    assertTrue(rpc("resources/templates/list", "{}").contains("tentackle-model://entity/{name}"));
    assertTrue(rpc("prompts/list", "{}").contains("new-entity"));
  }

  @Test
  public void servesTheGrammarResource() throws Exception {
    String body = rpc("resources/read", "{\"uri\":\"tentackle-model://grammar/model-definition\"}");
    assertTrue(body.contains("Model Definition"), "the packaged grammar must be served, got: " +
                                                  body.substring(0, Math.min(400, body.length())));
    assertFalse(body.contains("not packaged with this plugin"), "the grammar is missing from the jar");
  }

  @Test
  public void servesAnEntityByResourceTemplate() throws Exception {
    String body = rpc("resources/read", "{\"uri\":\"tentackle-model://entity/Product\"}");
    assertTrue(body.contains("name := Product"), body);
  }

  @Test
  public void answersAToolCall() throws Exception {
    String body = callTool("model_overview", "{}");
    assertTrue(body.contains("entities:       4"), body);
    assertTrue(body.contains("\"isError\":false"), body);
  }

  @Test
  public void passesArgumentsThrough() throws Exception {
    String body = callTool("model_describe_entity",
                           "{\"entity\":\"Order\",\"sections\":[\"aggregate\"],\"scope\":\"own\"}");
    assertTrue(body.contains("Customer"), body);
    assertTrue(body.contains("## aggregate"), body);
  }

  @Test
  public void reportsAToolFailureAsAToolError() throws Exception {
    String body = callTool("model_describe_entity", "{\"entity\":\"NoSuchEntity\"}");
    assertTrue(body.contains("\"isError\":true"), body);
    assertTrue(body.contains("model_describe_entity failed"), body);
  }

  @Test
  public void validatesACandidateOverTheWire() throws Exception {
    String body = callTool("model_validate",
                           "{\"source\":\"name := Wire\\ntable := wire\\nid := 9001\\nintegrity := none\\n" +
                           "alias := wire\\n\\n## attributes\\nString(10)  a  acol  a column\\n\"}");
    assertTrue(body.contains("duplicate entity id 9001"), body);
  }

}
