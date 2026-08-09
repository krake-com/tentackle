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

import io.helidon.extensions.mcp.server.McpPrompt;
import io.helidon.extensions.mcp.server.McpPromptArgument;
import io.helidon.extensions.mcp.server.McpPromptRequest;
import io.helidon.extensions.mcp.server.McpPromptResult;
import java.util.List;

/**
 * Guides an agent through adding an entity to the model.
 * <p>
 * The order matters more than the syntax: look at a comparable entity first, validate the candidate second,
 * write the file last. An agent that writes first produces a definition that fails in the next reactor build.
 *
 * @author harald
 */
public class NewEntityPrompt implements McpPrompt {

  /** The prompt name. */
  public static final String NAME = "new-entity";

  /**
   * Creates the prompt.
   */
  public NewEntityPrompt() {
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Add a new entity to the Tentackle model, from looking at comparable entities to validating the " +
           "definition before writing it.";
  }

  @Override
  public List<McpPromptArgument> arguments() {
    return List.of(
        McpPromptArgument.builder()
            .name("entity")
            .description("The name of the entity to add, for instance 'Invoice'.")
            .required(true)
            .build(),
        McpPromptArgument.builder()
            .name("purpose")
            .description("What the entity is for, in one sentence.")
            .required(false)
            .build(),
        McpPromptArgument.builder()
            .name("root")
            .description("The aggregate root the entity belongs to, if it is a component.")
            .required(false)
            .build());
  }

  @Override
  public McpPromptResult prompt(McpPromptRequest request) {
    String entity = request.arguments().get("entity").asString().orElse("the new entity");
    String purpose = request.arguments().get("purpose").asString().orElse(null);
    String root = request.arguments().get("root").asString().orElse(null);

    StringBuilder buf = new StringBuilder(1024);
    buf.append("Add the entity ").append(entity).append(" to the Tentackle model.");
    if (purpose != null) {
      buf.append(" Purpose: ").append(purpose).append('.');
    }
    if (root != null) {
      buf.append(" It is a component of the aggregate root ").append(root).append('.');
    }
    buf.append("""


        Work in this order and do not skip a step:

        1. Call model_overview to see the project's model defaults, integrity mode and aggregate roots. \
        Whatever the defaults already imply must not be repeated in the definition.
        2. Call model_list_entities and model_describe_entity on one or two comparable entities. Match their \
        conventions for table names, class ids, column names and options rather than inventing new ones.
        3. Read the tentackle-model://grammar/model-definition resource for any syntax you are not certain of.
        4. Pick a free class id. Check it with model_list_entities -- a duplicate id is the most common \
        mistake and the model will reject it.
        5. Draft the definition and call model_validate with it as 'source'. Fix what it reports and call it \
        again until it is consistent. Nothing is written until you write it.
        6. Only then create the PDO interface and paste the definition into its '@> ... @<' block. Use \
        model_source with format 'compact' and asComment 'true' on a comparable entity to see the exact shape \
        the block should have.
        7. Run the build so the wurblets generate the persistence and domain layers.""");

    return McpPromptResult.create(buf.toString());
  }

}
