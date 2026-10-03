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
import org.tentackle.model.Entity;

/**
 * Serves the model source of one entity, addressed by a URI template.
 *
 * @author harald
 */
public class EntityResource implements McpResource {

  /** The resource URI template. */
  public static final String URI = "tentackle-model://entity/{name}";

  private final ModelSession session;

  /**
   * Creates the resource.
   *
   * @param session the model session
   */
  public EntityResource(ModelSession session) {
    this.session = session;
  }

  @Override
  public String uri() {
    return URI;
  }

  @Override
  public String name() {
    return "model-entity-source";
  }

  @Override
  public String description() {
    return "The model definition of one entity, as loaded from its model file. Substitute the entity name, " +
           "'table:<tableName>' or 'id:<classId>' for {name}.";
  }

  @Override
  public MediaType mediaType() {
    return MediaTypes.TEXT_PLAIN;
  }

  @Override
  public McpResourceResult resource(McpResourceRequest request) {
    String name = request.parameters().get("name").asString().orElse(null);
    if (name == null) {
      return McpResourceResult.create("no entity name in the uri");
    }
    try {
      return McpResourceResult.create(session.call(() -> {
        Entity entity = Entities.resolve(session.getModel(), name);
        String source = session.getModelSource(entity);
        return source == null ? "no source loaded for entity " + entity.getName() : source;
      }));
    }
    catch (Exception ex) {
      String message = ex.getMessage();
      return McpResourceResult.create(message == null || message.isBlank() ? ex.toString() : message);
    }
  }

}
