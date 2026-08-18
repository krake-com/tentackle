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
import org.tentackle.maven.plugin.mcp.render.Entities;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;
import org.tentackle.model.print.EntityPrinter;
import org.tentackle.model.print.PrintConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * Returns the model definition of an entity as text.
 * <p>
 * {@code raw} is what the file holds, {@code canonical} and {@code compact} are printed from the resolved model
 * and are therefore the form to imitate when writing a new definition.
 *
 * @author harald
 */
public class SourceTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_source";

  /** The available formats. */
  public static final String[] FORMATS = { "raw", "canonical", "compact" };

  /**
   * Creates the tool.
   */
  public SourceTool() {
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "Entity model source";
  }

  @Override
  public String getDescription() {
    return """
        Returns the model definition of an entity as text. 'raw' is the source exactly as loaded, 'canonical' \
        is printed from the resolved model with everything spelled out, and 'compact' omits what the project's \
        model defaults already imply -- which is what you would actually write into a PDO interface. Set \
        'asComment' to wrap the result in a '@> ... @<' block ready to paste, and 'useVariables' to keep the \
        $classname, $classid and $tablename placeholders.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder()
        .requiredString("entity", "The entity name, 'table:<tableName>' or 'id:<classId>'.")
        .enumeration("format", "How the definition is rendered.", "canonical", FORMATS)
        .bool("asComment", "Wrap the result in a '@> ... @<' comment block.", false)
        .bool("useVariables", "Keep the $classname, $classid, $tablename, $mapping and $integrity variables.",
              false)
        .build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    Entity entity = Entities.resolve(session.getModel(), args.requiredString("entity"));
    String format = args.string("format", "canonical").toLowerCase(Locale.ROOT);
    boolean asComment = args.bool("asComment", false);
    boolean useVariables = args.bool("useVariables", false);

    if (!List.of(FORMATS).contains(format)) {
      throw new IllegalArgumentException("unknown format '" + format + "', expected one of " +
                                         String.join(", ", FORMATS));
    }

    if ("raw".equals(format)) {
      String source = session.getModelSource(entity);
      if (source == null) {
        throw new IllegalArgumentException("no source loaded for entity " + entity.getName());
      }
      return source;
    }

    PrintConfiguration configuration = new PrintConfiguration("compact".equals(format), asComment, useVariables,
                                                              session.getModel().getModelDefaults(), List.of(), 2);
    return new EntityPrinter(entity, configuration).print();
  }

}
