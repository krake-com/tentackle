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

package org.tentackle.maven.plugin.mcp.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tools this server offers, in order an agent should discover them.
 *
 * @author harald
 */
public class ToolRegistry {

  private final Map<String, ModelTool> tools;

  /**
   * Creates the registry with all built-in tools.
   *
   * @param maxResults the hard upper bound for the number of rows a tool may return
   */
  public ToolRegistry(int maxResults) {
    tools = new LinkedHashMap<>();
    register(new OverviewTool());
    register(new ListEntitiesTool(maxResults));
    register(new FindTool(maxResults));
    register(new DescribeEntityTool());
    register(new SourceTool());
    register(new PathsTool(maxResults));
    register(new DdlTool());
    register(new ValidateTool());
    register(new ReloadTool());
  }

  /**
   * Adds a tool.
   *
   * @param tool the tool
   */
  public final void register(ModelTool tool) {
    tools.put(tool.getName(), tool);
  }

  /**
   * Gets all tools in discovery order.
   *
   * @return the tools
   */
  public List<ModelTool> getTools() {
    return List.copyOf(tools.values());
  }

  /**
   * Gets a tool by name.
   *
   * @param name the tool name
   * @return the tool
   * @throws IllegalArgumentException if there is no such tool
   */
  public ModelTool getTool(String name) {
    ModelTool tool = tools.get(name);
    if (tool == null) {
      throw new IllegalArgumentException("unknown tool '" + name + "', available are " + tools.keySet());
    }
    return tool;
  }

}
