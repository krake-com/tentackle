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

import io.helidon.extensions.mcp.server.McpServerConfig;
import io.helidon.extensions.mcp.server.McpServerFeature;

import org.tentackle.maven.plugin.mcp.resource.EntityResource;
import org.tentackle.maven.plugin.mcp.resource.GrammarResource;
import org.tentackle.maven.plugin.mcp.resource.IndexResource;
import org.tentackle.maven.plugin.mcp.resource.NewEntityPrompt;
import org.tentackle.maven.plugin.mcp.tool.ModelTool;
import org.tentackle.maven.plugin.mcp.tool.ToolAdapter;
import org.tentackle.maven.plugin.mcp.tool.ToolRegistry;

/**
 * Assembles the MCP server feature.
 * <p>
 * Kept out of the mojo so that a test can build the same feature without a maven session.
 *
 * @author harald
 */
public final class McpFeatureFactory {

  private McpFeatureFactory() {
  }

  /**
   * Creates the feature.
   *
   * @param session the model session
   * @param path the path of the MCP endpoint
   * @param name the server name reported during initialization
   * @param version the server version reported during initialization
   * @param maxResults the hard upper bound for the number of rows a tool may return
   * @return the feature
   */
  public static McpServerFeature create(ModelSession session, String path, String name, String version,
                                        int maxResults) {
    McpServerConfig.Builder builder = McpServerFeature.builder()
        .path(path)
        .name(name)
        .version(version);

    for (ModelTool tool : new ToolRegistry(maxResults).getTools()) {
      builder.addTool(new ToolAdapter(tool, session));
    }

    builder.addResource(new GrammarResource());
    builder.addResource(new IndexResource(session));
    builder.addResource(new EntityResource(session));
    builder.addPrompt(new NewEntityPrompt());

    return builder.build();
  }

}
