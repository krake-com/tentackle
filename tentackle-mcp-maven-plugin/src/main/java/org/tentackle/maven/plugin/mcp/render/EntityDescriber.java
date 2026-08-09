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

package org.tentackle.maven.plugin.mcp.render;

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parse.Document;
import org.tentackle.model.print.EntityPrinter;
import org.tentackle.model.print.PrintConfiguration;
import org.tentackle.sql.Backend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Describes one entity.
 * <p>
 * The primary representation is the canonical model definition: the densest, least ambiguous description of an
 * entity that exists, and the language the caller has to write back. Everything the DSL cannot express because
 * it is computed from the whole model graph is added as an explicit section.
 *
 * @author harald
 */
public class EntityDescriber {

  /** The sections that can be rendered; in the order they are rendered. */
  public static final String[] SECTIONS = {
      "definition", "attributes", "relations", "indexes", "inheritance", "aggregate",
      "references", "embedding", "udk", "ddl"
  };

  /** The sections rendered when the caller names none. */
  public static final List<String> DEFAULT_SECTIONS = List.of("definition", "inheritance", "references");

  private final Entity entity;
  private final Scope scope;
  private final Collection<String> sections;
  private final ModelDefaults modelDefaults;
  private final Collection<Backend> backends;

  /**
   * Creates a describer.
   *
   * @param entity the entity to describe
   * @param scope the scope of the member sections
   * @param sections the sections to render, see {@link #SECTIONS}
   * @param modelDefaults the model defaults, null if none
   * @param backends the backends for the ddl section, empty if none configured
   */
  public EntityDescriber(Entity entity, Scope scope, Collection<String> sections,
                         ModelDefaults modelDefaults, Collection<Backend> backends) {
    this.entity = entity;
    this.scope = scope;
    this.sections = sections;
    this.modelDefaults = modelDefaults;
    this.backends = backends;
    for (String section : sections) {
      if (!List.of(SECTIONS).contains(section.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("unknown section '" + section + "', expected one of " +
                                           String.join(", ", SECTIONS));
      }
    }
  }

  /**
   * Renders the description.
   *
   * @return the description
   * @throws ModelException if the model is inconsistent
   */
  public String describe() throws ModelException {
    StringBuilder buf = new StringBuilder(2048);
    appendHeader(buf);
    for (String section : SECTIONS) {
      if (contains(section)) {
        switch (section) {
          case "definition" -> appendDefinition(buf);
          case "attributes" -> appendAttributes(buf);
          case "relations" -> appendRelations(buf);
          case "indexes" -> appendIndexes(buf);
          case "inheritance" -> appendInheritance(buf);
          case "aggregate" -> appendAggregate(buf);
          case "references" -> appendReferences(buf);
          case "embedding" -> appendEmbedding(buf);
          case "udk" -> appendUniqueDomainKey(buf);
          case "ddl" -> appendDdl(buf);
          default -> throw new IllegalStateException("unhandled section " + section);
        }
      }
    }
    return buf.toString();
  }


  private boolean contains(String section) {
    return sections.stream().anyMatch(s -> s.equalsIgnoreCase(section));
  }

  private void appendSection(StringBuilder buf, String title) {
    if (!buf.isEmpty()) {
      buf.append('\n');
    }
    buf.append("## ").append(title).append('\n');
  }

  private void appendHeader(StringBuilder buf) {
    buf.append(entity.getName()).append("  (id ").append(entity.getClassId());
    // an entity mapped into another entity's table has no table name of its own: name the provider instead
    Entity tableProvider = entity.getTableProvidingEntity();
    if (tableProvider == null) {
      buf.append(", no table of its own");
    }
    else {
      buf.append(", table ").append(tableProvider.getTableName())
         .append(", alias ").append(tableProvider.getTableAlias());
      if (tableProvider != entity) {
        buf.append(" (provided by ").append(tableProvider.getName()).append(')');
      }
    }
    buf.append(", integrity ").append(entity.getIntegrity()).append(')');
    List<String> flags = Flags.of(entity);
    if (!flags.isEmpty()) {
      buf.append("\nflags:   ").append(Flags.join(flags));
    }
    if (entity.getOptions().getComment() != null) {
      buf.append("\ncomment: ").append(entity.getOptions().getComment());
    }
    SourceInfo sourceInfo = entity.getSourceInfo();
    if (sourceInfo != null && sourceInfo.getFileName() != null) {
      // where the '@> ... @<' block is written: the file to edit, which no other section reveals
      buf.append("\nsource:  ").append(sourceInfo.getFileName()).append(':').append(sourceInfo.getLineNumber());
    }
    buf.append("\nscope of the member sections below: ").append(scope).append('\n');
  }

  private void appendDefinition(StringBuilder buf) {
    appendSection(buf, "definition (canonical model-definition source)");
    PrintConfiguration configuration =
        new PrintConfiguration(false, false, false, modelDefaults, List.of(), 2);
    buf.append(stripOriginInfo(new EntityPrinter(entity, configuration).print()));
  }

  /**
   * Removes the leading origin comment.
   * <p>
   * It names the file the block was extracted from, which the header already reports and which must not end up
   * in a definition that is pasted back into a source file.
   *
   * @param definition the printed definition
   * @return the definition without its origin line
   */
  private String stripOriginInfo(String definition) {
    if (definition.startsWith(Document.ORIGIN_INFO_LEAD)) {
      int index = definition.indexOf('\n');
      if (index >= 0) {
        return definition.substring(index + 1).stripLeading();
      }
    }
    return definition;
  }

  private void appendAttributes(StringBuilder buf) throws ModelException {
    appendSection(buf, "attributes (" + scope + ')');
    TextTable table = new TextTable("NAME", "COLUMN", "TYPE", "SIZE", "FLAGS", "COMMENT");
    for (Attribute attribute : scope.attributes(entity)) {
      table.add(attribute.getName(),
                attribute.getColumnName(),
                attribute.getJavaType(),
                size(attribute),
                Flags.join(Flags.of(attribute)),
                attribute.getOptions().getComment());
    }
    buf.append(table.isEmpty() ? "none\n" : table.toString());
  }

  private String size(Attribute attribute) {
    if (attribute.getSize() == null) {
      return "";
    }
    return attribute.getScale() == null
           ? attribute.getSize().toString()
           : attribute.getSize() + "," + attribute.getScale();
  }

  private void appendRelations(StringBuilder buf) {
    appendSection(buf, "relations (" + scope + ')');
    TextTable table = new TextTable("NAME", "TYPE", "TARGET", "SELECT", "FLAGS", "COMMENT");
    for (Relation relation : scope.relations(entity)) {
      table.add(relation.getName(),
                relation.getRelationType(),
                relation.getForeignEntity() == null ? relation.getClassName() : relation.getForeignEntity().getName(),
                relation.getSelectionType(),
                Flags.join(Flags.of(relation)),
                relation.getComment());
    }
    buf.append(table.isEmpty() ? "none\n" : table.toString());
  }

  private void appendIndexes(StringBuilder buf) {
    appendSection(buf, "indexes (" + scope + ')');
    TextTable table = new TextTable("NAME", "UNIQUE", "COLUMNS", "FILTER");
    for (Index index : scope.indexes(entity)) {
      List<String> columns = new ArrayList<>();
      for (IndexAttribute indexAttribute : index.getAttributes()) {
        columns.add(indexAttribute.getColumnName() + (indexAttribute.isDescending() ? " desc" : ""));
      }
      table.add(index.getName(),
                index.isUnique() ? "unique" : "",
                String.join(", ", columns),
                index.getFilterCondition());
    }
    buf.append(table.isEmpty() ? "none\n" : table.toString());
  }

  private void appendInheritance(StringBuilder buf) {
    appendSection(buf, "inheritance");
    buf.append("type:       ").append(entity.getInheritanceType());
    if (entity.getHierarchyInheritanceType() != entity.getInheritanceType()) {
      buf.append("  (hierarchy: ").append(entity.getHierarchyInheritanceType()).append(')');
    }
    List<Entity> superEntities = entity.getSuperEntities();
    buf.append("\nsuper:      ")
       .append(superEntities.isEmpty() ? "none"
                                       : String.join(" > ", superEntities.stream().map(Entity::getName).toList()));
    buf.append("\ntable from: ").append(entity.getTableProvidingEntity() == null
                                        ? entity.getName() : entity.getTableProvidingEntity().getName());
    List<Entity> subEntities = entity.getAllSubEntities();
    buf.append("\nsub:        ").append(subEntities.isEmpty() ? "none" : subEntities.size() + " below").append('\n');
    appendTree(buf, entity, 1);
    List<Entity> leaves = entity.getLeafEntities();
    if (!leaves.isEmpty() && !(leaves.size() == 1 && leaves.getFirst() == entity)) {
      buf.append("leaves:     ").append(names(leaves)).append('\n');
    }
  }

  private void appendTree(StringBuilder buf, Entity parent, int level) {
    for (Entity sub : parent.getSubEntities()) {
      buf.repeat("  ", level).append("+- ").append(sub.getName());
      if (sub.isAbstract()) {
        buf.append(" (abstract)");
      }
      buf.append('\n');
      appendTree(buf, sub, level + 1);
    }
  }

  private void appendAggregate(StringBuilder buf) {
    appendSection(buf, "aggregate");
    buf.append("root entity:  ").append(entity.isRootEntity() ? "yes" : "no");
    Entity root = entity.getRootEntity();
    buf.append("\nroot:         ").append(root == null ? "none (not a component)" : root.getName());
    Set<Entity> roots = entity.getRootEntities();
    if (roots.size() > 1) {
      buf.append("\nroots:        ").append(names(roots)).append("  (ambiguous: reachable from several roots)");
    }
    Set<Entity> components = entity.getAllComponents();
    buf.append("\ncomponents:   ").append(components.isEmpty() ? "none" : names(components));
    buf.append("\ncomposite:    ").append(entity.isComposite() ? "yes" : "no");
    Boolean cascaded = entity.isDeletionCascaded();
    buf.append("\ndelete:       ").append(cascaded == null ? "unspecified" : cascaded ? "cascaded" : "not cascaded");

    Set<List<Relation>> paths = entity.getAllCompositePaths();
    buf.append("\n\ncomposite paths from the root(s) to this entity (").append(paths.size()).append("):");
    if (paths.isEmpty()) {
      buf.append("\nnone\n");
    }
    else {
      for (List<Relation> path : paths) {
        buf.append("\n  ").append(Entities.describePath(path));
      }
      buf.append('\n');
    }
  }

  private void appendReferences(StringBuilder buf) {
    appendSection(buf, "referenced by (" + scope + ')');
    TextTable table = new TextTable("ENTITY", "RELATION", "TYPE", "FLAGS");
    for (Relation relation : scope.referencingRelations(entity)) {
      table.add(relation.getEntity().getName(),
                relation.getName(),
                relation.getRelationType(),
                Flags.join(Flags.of(relation)));
    }
    buf.append(table.isEmpty() ? "none\n" : table.toString());

    List<Relation> deep = entity.getDeepReferences();
    if (!deep.isEmpty()) {
      buf.append("\ndeep references (a reference to this entity from outside its aggregate):\n");
      for (Relation relation : deep) {
        buf.append("  ").append(Entities.describe(relation)).append('\n');
      }
    }
  }

  private void appendEmbedding(StringBuilder buf) {
    appendSection(buf, "embedding");
    buf.append("embedded:  ").append(entity.isEmbedded() ? "yes" : "no");
    Set<Entity> embedding = entity.getEmbeddingEntities();
    buf.append("\nembedded in: ").append(embedding.isEmpty() ? "nothing" : names(embedding));
    Set<List<Relation>> paths = entity.getEmbeddingPaths();
    buf.append("\npaths (").append(paths.size()).append("):");
    if (paths.isEmpty()) {
      buf.append("\nnone\n");
    }
    else {
      for (List<Relation> path : paths) {
        buf.append("\n  ").append(Entities.describePath(path));
      }
      buf.append('\n');
    }
  }

  private void appendUniqueDomainKey(StringBuilder buf) throws ModelException {
    appendSection(buf, "unique domain key");
    List<Attribute> udk = entity.getUniqueDomainKey();
    if (udk == null || udk.isEmpty()) {
      buf.append("none\n");
    }
    else {
      TextTable table = new TextTable("ATTRIBUTE", "COLUMN", "TYPE");
      for (Attribute attribute : udk) {
        table.add(attribute.getName(), attribute.getColumnName(), attribute.getJavaType());
      }
      buf.append(table);
    }
  }

  private void appendDdl(StringBuilder buf) throws ModelException {
    appendSection(buf, "ddl");
    if (backends.isEmpty()) {
      buf.append("no backends configured: set the plugin's 'backends' parameter to render DDL\n");
      return;
    }
    if (!Entities.providesTable(entity)) {
      // rendering it anyway would print a CREATE TABLE the build never generates, with a null table name
      Entity tableProvider = entity.getTableProvidingEntity();
      if (tableProvider == null) {
        buf.append("no table of its own: this entity is not mapped to a table\n");
      }
      else if (tableProvider != entity) {
        buf.append("no table of its own: stored in the table of ").append(tableProvider.getName())
           .append(", describe that entity to see its ddl\n");
      }
      else {
        buf.append("declared as 'provided': the table is maintained externally and no ddl is generated for it\n");
      }
      return;
    }
    for (Backend backend : backends) {
      buf.append("-- ").append(backend.getName()).append('\n');
      buf.append(entity.sqlCreateTable(backend)).append('\n');
      for (Index index : entity.getTableIndexes()) {
        buf.append(index.sqlCreateIndex(backend, entity)).append('\n');
      }
    }
  }

  private String names(Collection<Entity> entities) {
    return String.join(", ", Entities.sorted(entities).stream().map(Entity::getName).toList());
  }

}
