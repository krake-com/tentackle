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

import io.helidon.webserver.WebServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * A minimal MCP client over Streamable HTTP, the way a coding agent talks to the server.
 * <p>
 * Speaking the protocol by hand keeps the tests free of an MCP client library and shows what a client actually
 * sends. Everything returns the raw response body: the assertions are about what an agent reads.
 */
public class McpTestClient {

  private final URI endpoint;
  private final HttpClient client;
  private final String sessionId;

  /**
   * Connects to a running server and initializes a session.
   *
   * @param server the server
   * @param path the path of the MCP endpoint
   * @throws Exception if the session could not be opened
   */
  public McpTestClient(WebServer server, String path) throws Exception {
    this.endpoint = URI.create("http://localhost:" + server.port() + path);
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.sessionId = initialize();
  }

  /**
   * Invokes a JSON-RPC method.
   *
   * @param method the method name
   * @param params the parameters as JSON
   * @return the response body
   * @throws Exception if the call failed
   */
  public String rpc(String method, String params) throws Exception {
    HttpResponse<String> response =
        post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"" + method + "\",\"params\":" + params + '}', sessionId);
    assertEquals(response.statusCode(), 200, response.body());
    return response.body();
  }

  /**
   * Calls a tool.
   *
   * @param name the tool name
   * @param arguments the arguments as JSON
   * @return the response body
   * @throws Exception if the call failed
   */
  public String callTool(String name, String arguments) throws Exception {
    return rpc("tools/call", "{\"name\":\"" + name + "\",\"arguments\":" + arguments + '}');
  }


  private HttpRequest.Builder request() {
    return HttpRequest.newBuilder(endpoint)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream")
        .timeout(Duration.ofSeconds(30));
  }

  private HttpResponse<String> post(String body, String id) throws Exception {
    return client.send(
        request().header("Mcp-Session-Id", id).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
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

}
