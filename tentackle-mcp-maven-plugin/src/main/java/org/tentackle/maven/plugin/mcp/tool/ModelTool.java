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

import org.tentackle.maven.plugin.mcp.ModelSession;
import org.tentackle.model.ModelException;

/**
 * A tool offered to the MCP client.
 * <p>
 * Kept separate from Helidon's {@code McpTool} so that a tool is a plain function of a session and its arguments,
 * testable without a web server. {@link ToolAdapter} bridges the two.
 *
 * @author harald
 */
public interface ModelTool {

  /**
   * Gets the tool name as seen by the client.
   *
   * @return the name
   */
  String getName();

  /**
   * Gets the human-readable title.
   *
   * @return the title
   */
  String getTitle();

  /**
   * Gets the description telling an agent when to use this tool.
   *
   * @return the description
   */
  String getDescription();

  /**
   * Gets the JSON schema of the arguments.
   *
   * @return the schema
   */
  String getInputSchema();

  /**
   * Executes the tool.
   * <p>
   * Invoked with the model lock held, see {@link ModelSession#call(ModelSession.ModelOperation)}.
   *
   * @param session the model session
   * @param args the arguments, never null
   * @return the text handed to the model
   * @throws ModelException if the model is inconsistent
   * @throws IllegalArgumentException if an argument is malformed, reported to the client as a tool error
   */
  String execute(ModelSession session, ToolArguments args) throws ModelException;

  /**
   * Returns whether this tool only reads the model.
   *
   * @return true if read-only
   */
  default boolean isReadOnly() {
    return true;
  }

}
