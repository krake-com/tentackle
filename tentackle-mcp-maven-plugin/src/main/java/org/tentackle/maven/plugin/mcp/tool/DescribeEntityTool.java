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
import org.tentackle.maven.plugin.mcp.render.EntityDescriber;
import org.tentackle.maven.plugin.mcp.render.Scope;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;

import java.util.List;

/**
 * Describes one entity.
 * <p>
 * The workhorse. Two arguments collapse what would otherwise be dozens of tools: {@code scope} selects the
 * own / inherited / sub-entity / all quartet the model API repeats for every member kind, and {@code sections}
 * selects which derived views to render.
 *
 * @author harald
 */
public class DescribeEntityTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_describe_entity";

  /**
   * Creates the tool.
   */
  public DescribeEntityTool() {
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "Describe entity";
  }

  @Override
  public String getDescription() {
    return """
        Describes one entity. The 'definition' section is the canonical model-definition source -- the exact \
        DSL you would write yourself. The other sections show what the model derived from the whole graph and \
        no file contains: the inheritance closure, the aggregate structure with its composite paths, which \
        entities reference this one, the embedding paths, the unique domain key and the DDL. Address the \
        entity by name, by 'table:<tableName>' or by 'id:<classId>'.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder()
        .requiredString("entity", "The entity name, 'table:<tableName>' or 'id:<classId>'.")
        .enumeration("scope", "Which part of the inheritance hierarchy the member sections cover.",
                     Scope.INHERITED.toString(), Scope.NAMES)
        .stringArray("sections", "The sections to render. Defaults to " +
                                 String.join(", ", EntityDescriber.DEFAULT_SECTIONS) + '.',
                     EntityDescriber.SECTIONS)
        .build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    Entity entity = Entities.resolve(session.getModel(), args.requiredString("entity"));
    Scope scope = Scope.parse(args.string("scope", null), Scope.INHERITED);
    List<String> sections = args.list("sections", EntityDescriber.DEFAULT_SECTIONS);
    return new EntityDescriber(entity, scope, sections,
                               session.getModel().getModelDefaults(), session.getBackends()).describe();
  }

}
