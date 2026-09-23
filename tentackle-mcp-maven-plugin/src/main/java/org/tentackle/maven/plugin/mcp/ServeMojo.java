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

import io.helidon.extensions.mcp.server.McpServerFeature;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import org.tentackle.model.ModelException;

import java.util.concurrent.CountDownLatch;

/**
 * Serves the model to coding agents over MCP.
 * <p>
 * Blocks until the server is stopped with {@code Ctrl-C}.
 *
 * @author harald
 */
@Mojo(name = "serve", aggregator = true)
public class ServeMojo extends AbstractMcpMojo {

  /** The address to bind to. */
  @Parameter(defaultValue = "localhost", property = "tentackle.mcp.host")
  private String host;

  /** The port to listen on. */
  @Parameter(defaultValue = "8770", property = "tentackle.mcp.port")
  private int port;

  /** The path of the MCP endpoint. */
  @Parameter(defaultValue = "/mcp", property = "tentackle.mcp.path")
  private String path;

  /** The server name reported to the client during initialization. */
  @Parameter(defaultValue = "tentackle-model")
  private String serverName;

  /** The minimum delay between two checks for changed model files in milliseconds. */
  @Parameter(defaultValue = "1000")
  private long stalenessCheckMillis;

  /** The maximum number of rows a tool may return. */
  @Parameter(defaultValue = "1000")
  private int maxResults;


  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    ModelSession session = new ModelSession(createModelConfiguration(), stalenessCheckMillis);
    try {
      session.load();
    }
    catch (ModelException mex) {
      throw new MojoFailureException("loading the model failed: " + mex.getMessage(), mex);
    }

    for (String warning : session.getWarnings()) {
      getLogger().warn(warning);
    }

    try {
      getLogger().info("{} entities loaded from {}",
                       session.getModel().getAllEntities().size(), String.join(", ", session.getConfiguration().modelDirs()));
    }
    catch (ModelException mex) {
      throw new MojoFailureException("cannot determine entities: " + mex.getMessage(), mex);
    }

    LogConfig.configureRuntime();
    McpServerFeature feature = McpFeatureFactory.create(session, path, serverName,
                                                        getMavenProject().getVersion(), maxResults);

    WebServer server = WebServer.builder()
        .host(host)
        .port(port)
        .routing(routing -> routing.addFeature(feature))
        .build()
        .start();

    String url = "http://" + host + ':' + server.port() + path;
    getLogger().info("MCP endpoint: {}", url);
    getLogger().info("claude mcp add --transport http {} {}", serverName, url);
    getLogger().info("stop with Ctrl-C");

    awaitShutdown();
    server.stop();
  }

  /**
   * Blocks until the JVM is asked to terminate.
   */
  protected void awaitShutdown() {
    CountDownLatch latch = new CountDownLatch(1);
    Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown, "tentackle-mcp-shutdown"));
    try {
      latch.await();
    }
    catch (InterruptedException ix) {
      Thread.currentThread().interrupt();
    }
  }

}
