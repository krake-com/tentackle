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

package org.tentackle.maven.plugin.sql;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders a loaded model as cross-linked pages, one per entity, plus an index page.<br>
 * The relationship sections mirror the logic of the {@code ModelComment} wurblet
 * ({@code tentackle-wurblets/src/main/wurblets/org/tentackle/wurblet/ModelComment.wrbl} and its
 * {@code ModelCommentSupport} helper) - same sections, same ordering, same wording - except that
 * every entity name mentioned is rendered as a link to that entity's own page instead of
 * plain text. Additionally, each page shows a table of the entity's attributes.
 * <p>
 * This class holds the format-independent traversal of the model. The concrete markup is left to the
 * subclasses via the rendering primitives, so all formats provide the same content and the same
 * navigation between the pages.
 *
 * @author harald
 */
abstract class AbstractModelDocGenerator {

  private final Collection<Entity> entities;
  private final File outputDir;
  private final String title;
  private final Backend backend;
  private final boolean mapSchemas;


  /**
   * Creates the generator.
   *
   * @param entities the entities to render, one page each
   * @param outputDir the directory to write the pages into
   * @param title the heading for the index page
   * @param backend the backend the attribute tables' SQL types are rendered for
   * @param mapSchemas whether schema-qualified table names were folded into flat table names; if
   *                   true, the "Table" row already shows the effective, flattened table name and
   *                   the "Schema" row is omitted, since {@link Entity#getSchemaName()} still
   *                   reflects the original, no longer relevant schema in that case (same caveat
   *                   {@code CreateSqlMojo} and the model's migrators work around)
   */
  AbstractModelDocGenerator(Collection<Entity> entities, File outputDir, String title, Backend backend,
                            boolean mapSchemas) {
    this.entities = entities;
    this.outputDir = outputDir;
    this.title = title;
    this.backend = backend;
    this.mapSchemas = mapSchemas;
  }


  /**
   * Renders the format-specific assets, one page per entity, and the index page.
   *
   * @throws IOException if writing a file failed
   */
  void generate() throws IOException {
    writeAssets();
    for (Entity entity : entities) {
      try {
        renderEntityPage(entity);
      }
      catch (ModelException mex) {
        throw new IOException("rendering " + entity.getName() + " failed", mex);
      }
    }
    renderIndex();
  }


  /**
   * Gets the output directory.
   *
   * @return the directory the pages are written to
   */
  File getOutputDir() {
    return outputDir;
  }

  /**
   * Writes a file in UTF-8.
   *
   * @param file the file
   * @param content the file's content
   * @throws IOException if writing failed
   */
  void writeFile(File file, String content) throws IOException {
    try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
      writer.write(content);
    }
  }


  // ------------------------------------------------------------------------------------- rendering primitives

  /**
   * Gets the file extension of the pages including the leading dot.
   *
   * @return the extension
   */
  abstract String fileExtension();

  /**
   * Writes additional files the pages depend on, such as stylesheets.
   *
   * @throws IOException if writing failed
   */
  abstract void writeAssets() throws IOException;

  /**
   * Escapes free text for the target format.
   *
   * @param text the text, null if none
   * @return the escaped text, never null
   */
  abstract String escape(String text);

  /**
   * Renders a link to the given entity's page.
   *
   * @param entity the entity
   * @return the link
   */
  abstract String link(Entity entity);

  /**
   * Starts a page.
   *
   * @param buf the page buffer
   * @param heading the page's title and top-level heading
   * @param withIndexLink true to add a link back to the index page
   */
  abstract void pageHeader(StringBuilder buf, String heading, boolean withIndexLink);

  /**
   * Finishes a page.
   *
   * @param buf the page buffer
   */
  abstract void pageFooter(StringBuilder buf);

  /**
   * Renders a section heading.
   *
   * @param buf the page buffer
   * @param level the heading level, 2 or 3
   * @param text the unescaped heading text
   */
  abstract void heading(StringBuilder buf, int level, String text);

  /**
   * Starts a paragraph.
   *
   * @param buf the page buffer
   */
  abstract void beginParagraph(StringBuilder buf);

  /**
   * Ends a paragraph.
   *
   * @param buf the page buffer
   */
  abstract void endParagraph(StringBuilder buf);

  /**
   * Renders a line break within a paragraph.
   *
   * @param buf the page buffer
   */
  abstract void lineBreak(StringBuilder buf);

  /**
   * Renders a paragraph holding a descriptive comment.
   *
   * @param buf the page buffer
   * @param text the unescaped comment text
   */
  abstract void comment(StringBuilder buf, String text);

  /**
   * Renders a paragraph holding a flag, such as "This is a root entity."
   *
   * @param buf the page buffer
   * @param text the unescaped text
   */
  abstract void flag(StringBuilder buf, String text);

  /**
   * Starts a list, possibly nested within the current list item.
   *
   * @param buf the page buffer
   */
  abstract void beginList(StringBuilder buf);

  /**
   * Ends a list.
   *
   * @param buf the page buffer
   */
  abstract void endList(StringBuilder buf);

  /**
   * Starts a list item.
   *
   * @param buf the page buffer
   */
  abstract void beginItem(StringBuilder buf);

  /**
   * Ends a list item.
   *
   * @param buf the page buffer
   */
  abstract void endItem(StringBuilder buf);

  /**
   * Renders a newline within a list item, if meaningful for the format.
   *
   * @param buf the page buffer
   */
  abstract void itemNewline(StringBuilder buf);

  /**
   * Starts the key/value table of an entity's metadata.
   *
   * @param buf the page buffer
   */
  abstract void beginMetaTable(StringBuilder buf);

  /**
   * Renders a row of the metadata table.
   *
   * @param buf the page buffer
   * @param label the unescaped label
   * @param renderedValue the already escaped or linked value
   */
  abstract void metaRow(StringBuilder buf, String label, String renderedValue);

  /**
   * Ends the metadata table.
   *
   * @param buf the page buffer
   */
  abstract void endMetaTable(StringBuilder buf);

  /**
   * Starts a table with a header row.
   *
   * @param buf the page buffer
   * @param headers the unescaped column headers
   */
  abstract void beginTable(StringBuilder buf, String... headers);

  /**
   * Renders a table row.
   *
   * @param buf the page buffer
   * @param cells the unescaped cell values
   */
  abstract void tableRow(StringBuilder buf, String... cells);

  /**
   * Ends a table.
   *
   * @param buf the page buffer
   */
  abstract void endTable(StringBuilder buf);

  /**
   * Renders a block of SQL code.
   *
   * @param buf the page buffer
   * @param sql the unescaped SQL code
   */
  abstract void codeBlock(StringBuilder buf, String sql);

  /**
   * Renders a dash separating an item from its description.
   *
   * @return the dash
   */
  abstract String dash();


  // ------------------------------------------------------------------------------------- entity page

  private void renderEntityPage(Entity entity) throws IOException, ModelException {

    List<Relation> referencingRelations = entity.getReferencingRelationsIncludingInherited();
    List<Entity> subEntities = entity.getSubEntities();
    Set<List<Relation>> compositePaths = entity.getTopSuperEntity().getCompositePaths();
    List<Relation> compositeRelations = new ArrayList<>();
    for (Relation relation : entity.getRelationsIncludingInherited()) {
      if (relation.isComposite()) {
        compositeRelations.add(relation);
      }
    }

    StringBuilder buf = new StringBuilder();
    pageHeader(buf, entity.getName(), true);
    appendEntityMeta(buf, entity);
    appendAttributesTable(buf, entity);
    appendIndexesTable(buf, entity);

    heading(buf, 2, "Relationships");

    // referencing relations, skipping composite backlinks from our own components
    // (mirrors ModelComment.wrbl's refRels computation)
    List<Relation> refRels = new ArrayList<>();
    for (Relation rel : referencingRelations) {
      Relation foreignRelation = rel.getForeignRelation();
      if (foreignRelation == null || !foreignRelation.getEntity().equals(entity) || !foreignRelation.isComposite()) {
        refRels.add(rel);
      }
      else {
        Relation nmRel = rel.getDefiningNmRelation();
        if (nmRel != null) {
          refRels.add(nmRel);
        }
      }
    }

    heading(buf, 3, "Referenced by");
    if (refRels.isEmpty()) {
      paragraph(buf, "Not referenced by any other entity.");
    }
    else {
      beginList(buf);
      for (Relation rel : refRels) {
        appendReferencedBy(buf, entity, rel);
      }
      endList(buf);
    }

    if (entity.isRootEntity()) {
      flag(buf, "This is a root entity.");
    }

    if (!compositeRelations.isEmpty()) {
      heading(buf, 3, "Composite structure");
      appendComponents(buf, new HashSet<>(), compositeRelations, subEntities);
    }

    // outgoing non-composite relations from this entity or any of its components (mirrors ModelComment.wrbl)
    List<Relation> outRels = new ArrayList<>();
    Set<Entity> components = new HashSet<>();
    components.add(entity);
    components.addAll(entity.getSuperEntities());
    for (Entity component : entity.getAllComponents()) {
      components.add(component);
      components.addAll(component.getSuperEntities());
    }
    for (Entity component : components) {
      for (Relation relation : component.getRelationsIncludingSubEntities()) {
        if (!relation.isComposite() &&
            !components.contains(relation.getForeignEntity()) &&
            (relation.getForeignRelation() == null || !relation.getForeignRelation().isComposite())) {
          outRels.add(relation);
        }
      }
    }

    heading(buf, 3, "References");
    if (outRels.isEmpty()) {
      paragraph(buf, "Not referencing other entities.");
    }
    else {
      appendNonCompositeRelations(buf, entity, outRels, true);
    }

    if (entity.isComposite()) {
      List<Relation> deepRels = entity.getDeepReferencesToComponents();
      heading(buf, 3, "Deep references to components");
      if (deepRels.isEmpty()) {
        paragraph(buf, "Components are not deeply referenced.");
      }
      else {
        appendNonCompositeRelations(buf, entity, deepRels, false);
      }
    }

    if (!compositePaths.isEmpty()) {
      Set<Entity> roots = new TreeSet<>(Comparator.comparing(Entity::getName));
      Set<Entity> parents = new TreeSet<>(Comparator.comparing(Entity::getName));
      for (List<Relation> compositePath : compositePaths) {
        roots.add(compositePath.getFirst().getEntity());
        parents.add(compositePath.getLast().getEntity());
      }
      heading(buf, 3, "Composite membership");
      beginParagraph(buf);
      buf.append(escape("Component of: "));
      appendEntityList(buf, parents);
      lineBreak(buf);
      buf.append(escape("Belongs to the root " + (roots.size() > 1 ? "entities" : "entity") + ": "));
      appendEntityList(buf, roots);
      endParagraph(buf);
    }

    if (!entity.isEmbedded()) {
      heading(buf, 3, "Inheritance");
      if (subEntities.isEmpty()) {
        paragraph(buf, "Not extended.");
      }
      else {
        paragraph(buf, "Extended by:");
        appendSubEntities(buf, subEntities);
      }
    }

    pageFooter(buf);

    writeFile(new File(outputDir, entity.getName() + fileExtension()), buf.toString());
  }


  private void appendEntityMeta(StringBuilder buf, Entity entity) {
    String comment = entity.getOptions().getLongComment();
    if (comment == null || comment.isBlank()) {
      comment = entity.getOptions().getComment();
    }
    if (comment != null && !comment.isBlank()) {
      comment(buf, comment);
    }
    beginMetaTable(buf);
    appendMetaRow(buf, "Table", entity.getTableNameWithoutSchema());
    appendMetaRow(buf, "Schema", mapSchemas ? null : entity.getSchemaName());
    if (entity.getClassId() != 0) {
      appendMetaRow(buf, "Class ID", String.valueOf(entity.getClassId()));
    }
    if (entity.getSuperEntity() != null) {
      metaRow(buf, "Extends", link(entity.getSuperEntity()));
    }
    endMetaTable(buf);
  }

  private void appendMetaRow(StringBuilder buf, String label, String value) {
    if (value != null && !value.isBlank()) {
      metaRow(buf, label, escape(value));
    }
  }

  private void appendAttributesTable(StringBuilder buf, Entity entity) throws ModelException {
    List<Attribute> declaredAttributes = new ArrayList<>();
    List<Attribute> implicitAttributes = new ArrayList<>();
    for (Attribute attribute : entity.getAttributesIncludingInherited()) {
      (attribute.isImplicit() ? implicitAttributes : declaredAttributes).add(attribute);
    }

    heading(buf, 2, "Attributes");
    appendAttributeTable(buf, declaredAttributes, "No attributes.");

    heading(buf, 2, "Framework Attributes");
    comment(buf, "Attributes added implicitly by the framework - identity, " +
                 "optimistic-locking serial, class id, root/component bookkeeping - not declared in " +
                 "the model source.");
    appendAttributeTable(buf, implicitAttributes, "No framework attributes.");
  }

  private void appendAttributeTable(StringBuilder buf, List<Attribute> attributes, String emptyText) throws ModelException {
    if (attributes.isEmpty()) {
      paragraph(buf, emptyText);
      return;
    }
    beginTable(buf, "Name", "Column", "Java Type", "SQL Type", "Comment");
    for (Attribute attribute : attributes) {
      String javaType;
      try {
        javaType = attribute.getJavaType();
      }
      catch (ModelException mex) {
        javaType = "?";
      }
      tableRow(buf, attribute.getName(), attribute.getColumnName(), javaType, getSqlType(attribute),
               attribute.getOptions().getComment());
    }
    endTable(buf);
  }

  private String getSqlType(Attribute attribute) throws ModelException {
    DataType<?> dataType = attribute.getEffectiveDataType();
    int columnCount = dataType.getColumnCount(backend);
    StringBuilder sqlType = new StringBuilder();
    for (int i = 0; i < columnCount; i++) {
      if (i > 0) {
        sqlType.append(", ");
      }
      int size = dataType.getSize(backend, i, attribute.getSize());
      int scale = dataType.getScale(backend, i, attribute.getScale());
      sqlType.append(backend.columnTypeToString(dataType.getSqlType(backend, i), size, scale));
    }
    if (!attribute.isNullable()) {
      sqlType.append(" NOT NULL");
    }
    return sqlType.toString();
  }

  private void appendIndexesTable(StringBuilder buf, Entity entity) {
    List<Index> indexes = entity.getTableIndexes();
    heading(buf, 2, "Indexes");
    if (indexes.isEmpty()) {
      paragraph(buf, "No additional indexes.");
      return;
    }
    StringBuilder sql = new StringBuilder();
    for (Index index : indexes) {
      sql.append(index.sqlCreateIndex(backend, entity));
    }
    codeBlock(buf, sql.toString());
  }

  private void paragraph(StringBuilder buf, String text) {
    beginParagraph(buf);
    buf.append(escape(text));
    endParagraph(buf);
  }


  // ---------------------------------------------------------------------- ported from ModelCommentSupport

  /**
   * Mirrors {@code ModelCommentSupport.printVia}.
   */
  private void appendVia(StringBuilder buf, Relation relation) {
    if (relation.isEmbedding()) {
      buf.append(escape(" embedded"));
    }
    else {
      buf.append(escape(" via "));
      if (relation.getAttribute() != null) {
        buf.append(escape(relation.getAttribute().getName()));
      }
      else if (relation.getForeignEntity() != null && relation.getForeignAttribute() != null) {
        String entityName = relation.getForeignEntity().getName();
        if (relation.getForeignEntity().getTableAlias() != null) {
          entityName = relation.getForeignEntity().getTableAlias();
        }
        buf.append(escape(entityName)).append(escape(".")).append(escape(relation.getForeignAttribute().getName()));
      }
      else if (!relation.getMethodArgs().isEmpty()) {
        buf.append(escape(String.valueOf(relation.getMethodArgs().get(0))));
      }
      else {
        buf.append(escape("?"));
      }
      if (relation.getMethodArgs().size() > 1) {
        boolean firstSkipped = false;
        for (MethodArgument arg : relation.getMethodArgs()) {
          if (firstSkipped) {
            buf.append(escape(" & ")).append(escape(String.valueOf(arg)));
          }
          firstSkipped = true;
        }
      }
    }
  }

  /**
   * Mirrors {@code ModelCommentSupport.printReferencedBy}.
   */
  private void appendReferencedBy(StringBuilder buf, Entity entity, Relation relation) {
    Entity referencingEntity = relation.getEntity();
    beginItem(buf);
    buf.append(link(referencingEntity));
    appendRootEntities(buf, referencingEntity, referencingEntity.getRootEntities());
    if (relation.isDeepReference()) {
      buf.append(escape(" deeply"));
    }

    Relation nmRel = relation.getNmRelation();
    if (nmRel == null && relation.getForeignRelation() != null) {
      nmRel = relation.getForeignRelation().getNmRelation();
    }

    if (nmRel != null && !entity.equals(relation.getForeignEntity())) {
      buf.append(escape(" via ")).append(link(relation.getForeignEntity()));
      buf.append(escape(" as ")).append(escape(relation.getName()));
      buf.append(escape(" [N:M]"));
    }
    else {
      if (relation.isComposite() && !relation.isEmbedding()) {
        buf.append(escape(" composite"));
      }
      appendVia(buf, relation);
      if (!relation.getName().equalsIgnoreCase(referencingEntity.getName())) {
        buf.append(escape(" as ")).append(escape(relation.getVariableName()));
      }
      if (relation.getRelationType() == RelationType.LIST && !relation.isReversed()) {
        buf.append(escape(" [1:N]"));
      }
      else {
        if (relation.isReversed()) {
          buf.append(escape(" reversed"));
        }
        buf.append(escape(" [1:1]"));
      }
    }
    endItem(buf);
  }

  /**
   * Mirrors {@code ModelCommentSupport.printComponents}.
   */
  private void appendComponents(StringBuilder buf, Set<Relation> allRelations,
                                Collection<Relation> compositeRelations, Collection<Entity> subEntities) {
    if (compositeRelations.isEmpty()) {
      return;
    }

    beginList(buf);
    for (Relation relation : compositeRelations) {

      boolean relationNotAlreadyPrinted = allRelations.add(relation);
      Entity component = relation.getForeignEntity();

      if (relationNotAlreadyPrinted) {
        for (Entity sub : subEntities) {
          if (!sub.getComponents().isEmpty()) {
            beginItem(buf);
            buf.append(escape("^ ")).append(link(sub));
            itemNewline(buf);
            List<Relation> subCompositeRelations = new ArrayList<>();
            for (Relation subRelation : sub.getRelationsIncludingInherited()) {
              if (subRelation.isComposite()) {
                subCompositeRelations.add(subRelation);
              }
            }
            appendComponents(buf, allRelations, subCompositeRelations, sub.getSubEntities());
            endItem(buf);
          }
        }
      }

      beginItem(buf);
      buf.append(escape("+ ")).append(link(component));

      appendVia(buf, relation);

      if (!relation.getName().equalsIgnoreCase(component.getName())) {
        buf.append(escape(" as ")).append(escape(StringHelper.firstToLower(relation.getName())));
      }

      if (relation.getNmRelation() != null) {
        appendNmTarget(buf, relation);
      }
      else {
        buf.append(escape(relation.getRelationType() == RelationType.LIST ? " [1:N]" : " [1:1]"));
      }

      List<Relation> componentCompositeRelations = new ArrayList<>();
      for (Relation subRelation : component.getRelationsIncludingInherited()) {
        if (subRelation.isComposite()) {
          componentCompositeRelations.add(subRelation);
        }
      }

      if (relationNotAlreadyPrinted) {
        itemNewline(buf);
        appendComponents(buf, allRelations, componentCompositeRelations, component.getSubEntities());
      }
      else if (relation.getForeignEntity().isComposite()) {
        buf.append(escape(" ..."));
        itemNewline(buf);
      }
      endItem(buf);
    }
    endList(buf);
  }

  /**
   * Appends the opposite side of an N:M composite relation, as {@code ModelCommentSupport.printComponents} does.
   */
  private void appendNmTarget(StringBuilder buf, Relation relation) {
    buf.append(escape(" [N:M] to "));
    Entity nmEntity = relation.getNmRelation().getForeignEntity();
    buf.append(link(nmEntity));
    if (relation.getNmMethodName() != null &&
        !relation.getNmMethodName().equalsIgnoreCase(nmEntity.getName())) {
      buf.append(escape(" as ")).append(escape(StringHelper.firstToLower(relation.getNmMethodName())));
    }
  }

  /**
   * Mirrors {@code ModelCommentSupport.printSubEntities}.
   */
  private void appendSubEntities(StringBuilder buf, Collection<Entity> subEntities) {
    if (subEntities.isEmpty()) {
      return;
    }
    beginList(buf);
    for (Entity sub : subEntities) {
      beginItem(buf);
      buf.append(escape("^ ")).append(link(sub));
      appendSubEntities(buf, sub.getSubEntities());
      endItem(buf);
    }
    endList(buf);
  }

  /**
   * Mirrors {@code ModelCommentSupport.printNonCompositeRelations}.
   */
  private void appendNonCompositeRelations(StringBuilder buf, Entity entity, List<Relation> relations,
                                           boolean rootsFromForeignEntity) {
    List<Relation> sortedRelations = new ArrayList<>();
    for (Relation relation : relations) {
      if (!relation.isComposite() && !relation.getForeignEntity().equals(entity)) {
        sortedRelations.add(relation);
      }
    }
    if (sortedRelations.isEmpty()) {
      return;
    }
    sortedRelations.sort(Comparator.comparing(r -> r.getForeignEntity().getName()));

    beginList(buf);
    for (Relation relation : sortedRelations) {
      beginItem(buf);
      buf.append(link(relation.getForeignEntity()));
      if (rootsFromForeignEntity) {
        appendRootEntities(buf, entity, relation.getForeignEntity().getRootEntities());
      }
      if (!relation.getEntity().equals(entity)) {
        buf.append(escape(" from ")).append(link(relation.getEntity()));
        if (!rootsFromForeignEntity) {
          appendRootEntities(buf, relation.getEntity(), relation.getEntity().getRootEntities());
        }
      }
      appendVia(buf, relation);
      if (relation.isReversed()) {
        buf.append(escape(" reversed"));
      }
      if (relation.isDeepReference()) {
        buf.append(escape(" deeply"));
      }
      if (relation.getRelationType() == RelationType.LIST && !relation.isReversed()) {
        buf.append(escape(" [1:N]"));
      }
      else {
        Relation nmRel = relation.getDefiningNmRelation();
        if (nmRel != null && entity.equals(nmRel.getEntity())) {
          buf.append(escape(" [N:M]"));
        }
        else {
          buf.append(escape(" [1:1]"));
        }
      }
      endItem(buf);
    }
    endList(buf);
  }

  /**
   * Mirrors {@code ModelCommentSupport.printRootEntities}.
   */
  private void appendRootEntities(StringBuilder buf, Entity entity, Collection<Entity> rootEntities) {
    List<Entity> roots = new ArrayList<>(rootEntities);
    if (roots.size() == 1 && entity.equals(roots.get(0))) {
      roots.clear();
    }
    if (!roots.isEmpty()) {
      roots.sort(Comparator.comparing(Entity::getName));
      buf.append(escape(" ("));
      appendEntityList(buf, roots);
      buf.append(escape(")"));
    }
  }

  private void appendEntityList(StringBuilder buf, Collection<Entity> entityList) {
    boolean needComma = false;
    for (Entity e : entityList) {
      if (needComma) {
        buf.append(escape(", "));
      }
      needComma = true;
      buf.append(link(e));
    }
  }


  // ------------------------------------------------------------------------------------- index page

  private void renderIndex() throws IOException {
    List<Entity> sorted = new ArrayList<>(entities);
    sorted.sort(Comparator.comparing(Entity::getName));

    StringBuilder buf = new StringBuilder();
    pageHeader(buf, title, false);

    List<Entity> roots = new ArrayList<>();
    for (Entity entity : sorted) {
      if (entity.isRootEntity() && !entity.getComponents().isEmpty()) {
        roots.add(entity);
      }
    }
    if (!roots.isEmpty()) {
      heading(buf, 2, "Aggregates");
      beginList(buf);
      Set<Entity> visited = new HashSet<>();
      for (Entity root : roots) {
        beginItem(buf);
        buf.append(link(root));
        appendComponentTree(buf, root, visited);
        endItem(buf);
      }
      endList(buf);
    }

    heading(buf, 2, "All Entities");
    beginList(buf);
    for (Entity entity : sorted) {
      beginItem(buf);
      buf.append(link(entity));
      String comment = entity.getOptions().getComment();
      if (comment != null && !comment.isBlank()) {
        buf.append(dash()).append(escape(comment));
      }
      endItem(buf);
    }
    endList(buf);

    pageFooter(buf);

    writeFile(new File(outputDir, "index" + fileExtension()), buf.toString());
  }

  private void appendComponentTree(StringBuilder buf, Entity entity, Set<Entity> visited) {
    if (!visited.add(entity)) {
      return;
    }
    List<Relation> compositeRelations = new ArrayList<>();
    for (Relation relation : entity.getRelations()) {
      if (relation.isComposite()) {
        compositeRelations.add(relation);
      }
    }
    if (!compositeRelations.isEmpty()) {
      compositeRelations.sort(Comparator.comparing((Relation r) -> r.getForeignEntity().getName())
                                        .thenComparing(Relation::getName));
      beginList(buf);
      for (Relation relation : compositeRelations) {
        Entity component = relation.getForeignEntity();
        beginItem(buf);
        buf.append(link(component));
        if (relation.getNmRelation() != null) {
          appendNmTarget(buf, relation);
        }
        appendComponentTree(buf, component, visited);
        endItem(buf);
      }
      endList(buf);
    }
  }

}
