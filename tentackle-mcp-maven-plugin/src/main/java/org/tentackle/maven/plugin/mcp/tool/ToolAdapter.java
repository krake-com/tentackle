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

import io.helidon.extensions.mcp.server.McpTool;
import io.helidon.extensions.mcp.server.McpToolAnnotations;
import io.helidon.extensions.mcp.server.McpToolRequest;
import io.helidon.extensions.mcp.server.McpToolResult;

import org.tentackle.maven.plugin.mcp.ModelSession;
import org.tentackle.model.ModelException;

import java.util.Optional;

/**
 * Presents a {@link ModelTool} to Helidon.
 * <p>
 * Runs the tool under the session lock and turns a failure into a tool error rather than a protocol error: a
 * malformed argument or an inconsistent model is something the caller can act on, and it should read the reason
 * instead of a stack trace.
 * <p>
 * Also carries the tool's annotations, so that a client can tell which tools only look at the model.
 *
 * @author harald
 */
public class ToolAdapter implements McpTool {

  private final ModelTool tool;
  private final ModelSession session;

  /**
   * Creates an adapter.
   *
   * @param tool the tool
   * @param session the model session
   */
  public ToolAdapter(ModelTool tool, ModelSession session) {
    this.tool = tool;
    this.session = session;
  }

  @Override
  public String name() {
    return tool.getName();
  }

  @Override
  public Optional<String> title() {
    return Optional.of(tool.getTitle());
  }

  @Override
  public String description() {
    return tool.getDescription();
  }

  @Override
  public String schema() {
    return tool.getInputSchema();
  }

  @Override
  public Optional<McpToolAnnotations> annotations() {
    return Optional.of(McpToolAnnotations.builder()
        .readOnlyHint(tool.isReadOnly())
        .destructiveHint(false)     // the server writes nothing: the worst a tool does is reload the model
        .idempotentHint(true)
        .openWorldHint(false)       // the loaded model is all there is
        .build());
  }

  @Override
  public McpToolResult tool(McpToolRequest request) {
    try {
      ToolArguments args = ToolArguments.of(request.arguments());
      return McpToolResult.create(session.call(() -> tool.execute(session, args)));
    }
    catch (ModelException | RuntimeException ex) {
      // every failure is the caller's to read, including the ones nobody expected: a stack trace in the
      // protocol channel tells an agent nothing it can act on
      return McpToolResult.builder()
          .addTextContent(tool.getName() + " failed: " + message(ex))
          .error(true)
          .build();
    }
  }

  private String message(Exception ex) {
    String message = ex.getMessage();
    return message == null || message.isBlank() ? ex.toString() : message;
  }

}
