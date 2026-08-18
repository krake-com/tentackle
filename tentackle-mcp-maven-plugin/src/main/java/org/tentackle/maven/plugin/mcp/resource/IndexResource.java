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

package org.tentackle.maven.plugin.mcp.resource;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.extensions.mcp.server.McpResource;
import io.helidon.extensions.mcp.server.McpResourceRequest;
import io.helidon.extensions.mcp.server.McpResourceResult;

import org.tentackle.maven.plugin.mcp.ModelSession;
import org.tentackle.maven.plugin.mcp.render.Entities;
import org.tentackle.maven.plugin.mcp.render.TextTable;
import org.tentackle.model.Entity;

/**
 * Serves the entity index.
 * <p>
 * The same information {@code model_list_entities} returns, offered as a resource so that a client can attach
 * it to a conversation up front instead of spending a tool call on it.
 *
 * @author harald
 */
public class IndexResource implements McpResource {

  /** The resource URI. */
  public static final String URI = "tentackle-model://index";

  private final ModelSession session;

  /**
   * Creates the resource.
   *
   * @param session the model session
   */
  public IndexResource(ModelSession session) {
    this.session = session;
  }

  @Override
  public String uri() {
    return URI;
  }

  @Override
  public String name() {
    return "model-index";
  }

  @Override
  public String description() {
    return "All entities of the loaded model with their class id, table name and super entity.";
  }

  @Override
  public MediaType mediaType() {
    return MediaTypes.TEXT_PLAIN;
  }

  @Override
  public McpResourceResult resource(McpResourceRequest request) {
    try {
      return McpResourceResult.create(session.call(() -> {
        TextTable table = new TextTable("ENTITY", "ID", "TABLE", "EXTENDS");
        for (Entity entity : Entities.sorted(session.getModel().getAllEntities())) {
          table.add(entity.getName(),
                    entity.getClassId(),
                    entity.getTableName(),
                    entity.getSuperEntity() == null ? "" : entity.getSuperEntity().getName());
        }
        return table.toString();
      }));
    }
    catch (Exception ex) {
      return McpResourceResult.create("the model could not be read: " + ex.getMessage());
    }
  }

}
