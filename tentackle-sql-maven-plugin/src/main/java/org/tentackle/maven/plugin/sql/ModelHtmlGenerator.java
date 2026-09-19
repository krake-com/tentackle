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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders a loaded model as cross-linked HTML pages, one per entity, plus an index page.<br>
 * The relationship sections mirror the logic of the {@code ModelComment} wurblet
 * ({@code tentackle-wurblets/src/main/wurblets/org/tentackle/wurblet/ModelComment.wrbl} and its
 * {@code ModelCommentSupport} helper) - same sections, same ordering, same wording - except that
 * every entity name mentioned is rendered as a hyperlink to that entity's own page instead of
 * plain text. Additionally, each page shows a table of the entity's attributes.
 *
 * @author harald
 */
class ModelHtmlGenerator {

  private final Collection<Entity> entities;
  private final File outputDir;
  private final String title;
  private final File cssFile;
  private final Backend backend;


  /**
   * Creates the generator.
   *
   * @param entities the entities to render, one page each
   * @param outputDir the directory to write the pages into
   * @param title the heading for the index page
   * @param cssFile an optional project-specific stylesheet, copied verbatim to {@code style.css};
   *                if null, a default theme is generated instead
   * @param backend the backend the attribute tables' SQL types are rendered for
   */
  ModelHtmlGenerator(Collection<Entity> entities, File outputDir, String title, File cssFile, Backend backend) {
    this.entities = entities;
    this.outputDir = outputDir;
    this.title = title;
    this.cssFile = cssFile;
    this.backend = backend;
  }


  /**
   * Renders the stylesheets, one page per entity, and the index page.
   *
   * @throws IOException if writing a file failed
   */
  void generate() throws IOException {
    writeBaseStylesheet();
    writeThemeStylesheet();
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

    StringBuilder html = new StringBuilder();
    appendPageHeader(html, entity.getName());
    appendEntityMeta(html, entity);
    appendAttributesTable(html, entity);

    html.append("<h2>Relationships</h2>\n");

    // referencing relations, skipping composite back-links from our own components
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

    html.append("<h3>Referenced by</h3>\n");
    if (refRels.isEmpty()) {
      html.append("<p>Not referenced by any other entity.</p>\n");
    }
    else {
      html.append("<ul>\n");
      for (Relation rel : refRels) {
        appendReferencedBy(html, entity, rel);
      }
      html.append("</ul>\n");
    }

    if (entity.isRootEntity()) {
      html.append("<p class=\"flag\">This is a root entity.</p>\n");
    }

    if (!compositeRelations.isEmpty()) {
      html.append("<h3>Composite structure</h3>\n");
      appendComponents(html, new HashSet<>(), compositeRelations, subEntities);
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

    html.append("<h3>References</h3>\n");
    if (outRels.isEmpty()) {
      html.append("<p>Not referencing other entities.</p>\n");
    }
    else {
      appendNonCompositeRelations(html, entity, outRels, true);
    }

    if (entity.isComposite()) {
      List<Relation> deepRels = entity.getDeepReferencesToComponents();
      html.append("<h3>Deep references to components</h3>\n");
      if (deepRels.isEmpty()) {
        html.append("<p>Components are not deeply referenced.</p>\n");
      }
      else {
        appendNonCompositeRelations(html, entity, deepRels, false);
      }
    }

    if (!compositePaths.isEmpty()) {
      Set<Entity> roots = new TreeSet<>(Comparator.comparing(Entity::getName));
      Set<Entity> parents = new TreeSet<>(Comparator.comparing(Entity::getName));
      for (List<Relation> compositePath : compositePaths) {
        roots.add(compositePath.get(0).getEntity());
        parents.add(compositePath.get(compositePath.size() - 1).getEntity());
      }
      html.append("<h3>Composite membership</h3>\n<p>Component of: ");
      appendEntityList(html, parents);
      html.append("<br>Belongs to the root ").append(roots.size() > 1 ? "entities" : "entity").append(": ");
      appendEntityList(html, roots);
      html.append("</p>\n");
    }

    if (!entity.isEmbedded()) {
      html.append("<h3>Inheritance</h3>\n");
      if (subEntities.isEmpty()) {
        html.append("<p>Not extended.</p>\n");
      }
      else {
        html.append("<p>Extended by:</p>\n");
        appendSubEntities(html, subEntities);
      }
    }

    appendPageFooter(html);

    writeFile(new File(outputDir, entity.getName() + ".html"), html.toString());
  }


  private void appendEntityMeta(StringBuilder html, Entity entity) {
    String comment = entity.getOptions().getLongComment();
    if (comment == null || comment.isBlank()) {
      comment = entity.getOptions().getComment();
    }
    if (comment != null && !comment.isBlank()) {
      html.append("<p class=\"comment\">").append(escape(comment)).append("</p>\n");
    }
    html.append("<table class=\"meta\">\n");
    appendMetaRow(html, "Table", entity.getTableName());
    appendMetaRow(html, "Schema", entity.getSchemaName());
    if (entity.getClassId() != 0) {
      appendMetaRow(html, "Class ID", String.valueOf(entity.getClassId()));
    }
    if (entity.getSuperEntity() != null) {
      html.append("<tr><td>Extends</td><td>").append(link(entity.getSuperEntity())).append("</td></tr>\n");
    }
    html.append("</table>\n");
  }

  private void appendMetaRow(StringBuilder html, String label, String value) {
    if (value != null && !value.isBlank()) {
      html.append("<tr><td>").append(escape(label)).append("</td><td>").append(escape(value)).append("</td></tr>\n");
    }
  }

  private void appendAttributesTable(StringBuilder html, Entity entity) throws ModelException {
    List<Attribute> declaredAttributes = new ArrayList<>();
    List<Attribute> implicitAttributes = new ArrayList<>();
    for (Attribute attribute : entity.getAttributesIncludingInherited()) {
      (attribute.isImplicit() ? implicitAttributes : declaredAttributes).add(attribute);
    }

    html.append("<h2>Attributes</h2>\n");
    appendAttributeTable(html, declaredAttributes, "No attributes.");

    html.append("<h2>Framework Attributes</h2>\n")
        .append("<p class=\"comment\">Attributes added implicitly by the framework - identity, ")
        .append("optimistic-locking serial, class id, root/component bookkeeping - not declared in ")
        .append("the model source.</p>\n");
    appendAttributeTable(html, implicitAttributes, "No framework attributes.");
  }

  private void appendAttributeTable(StringBuilder html, List<Attribute> attributes, String emptyText) throws ModelException {
    if (attributes.isEmpty()) {
      html.append("<p>").append(escape(emptyText)).append("</p>\n");
      return;
    }
    html.append("<table>\n<tr><th>Name</th><th>Column</th><th>Java Type</th><th>SQL Type</th><th>Comment</th></tr>\n");
    for (Attribute attribute : attributes) {
      String javaType;
      try {
        javaType = attribute.getJavaType();
      }
      catch (ModelException mex) {
        javaType = "?";
      }
      html.append("<tr><td>").append(escape(attribute.getName())).append("</td><td>")
          .append(escape(attribute.getColumnName())).append("</td><td>")
          .append(escape(javaType)).append("</td><td>")
          .append(escape(getSqlType(attribute))).append("</td><td>")
          .append(escape(attribute.getOptions().getComment())).append("</td></tr>\n");
    }
    html.append("</table>\n");
  }

  private String getSqlType(Attribute attribute) throws ModelException {
    DataType<?> dataType = attribute.getEffectiveDataType();
    int columnCount = dataType.getColumnCount(backend);
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < columnCount; i++) {
      if (i > 0) {
        buf.append(", ");
      }
      int size = dataType.getSize(backend, i, attribute.getSize());
      int scale = dataType.getScale(backend, i, attribute.getScale());
      buf.append(backend.columnTypeToString(dataType.getSqlType(backend, i), size, scale));
    }
    return buf.toString();
  }


  // ---------------------------------------------------------------------- ported from ModelCommentSupport

  /**
   * Mirrors {@code ModelCommentSupport.printVia}.
   */
  private void appendVia(StringBuilder html, Relation relation) {
    if (relation.isEmbedding()) {
      html.append(" embedded");
    }
    else {
      html.append(" via ");
      if (relation.getAttribute() != null) {
        html.append(escape(relation.getAttribute().getName()));
      }
      else if (relation.getForeignEntity() != null && relation.getForeignAttribute() != null) {
        String entityName = relation.getForeignEntity().getName();
        if (relation.getForeignEntity().getTableAlias() != null) {
          entityName = relation.getForeignEntity().getTableAlias();
        }
        html.append(escape(entityName)).append('.').append(escape(relation.getForeignAttribute().getName()));
      }
      else if (!relation.getMethodArgs().isEmpty()) {
        html.append(escape(String.valueOf(relation.getMethodArgs().get(0))));
      }
      else {
        html.append('?');
      }
      if (relation.getMethodArgs().size() > 1) {
        boolean firstSkipped = false;
        for (MethodArgument arg : relation.getMethodArgs()) {
          if (firstSkipped) {
            html.append(" &amp; ").append(escape(String.valueOf(arg)));
          }
          firstSkipped = true;
        }
      }
    }
  }

  /**
   * Mirrors {@code ModelCommentSupport.printReferencedBy}.
   */
  private void appendReferencedBy(StringBuilder html, Entity entity, Relation relation) {
    Entity referencingEntity = relation.getEntity();
    html.append("<li>").append(link(referencingEntity));
    appendRootEntities(html, referencingEntity, referencingEntity.getRootEntities());
    if (relation.isDeepReference()) {
      html.append(" deeply");
    }

    Relation nmRel = relation.getNmRelation();
    if (nmRel == null && relation.getForeignRelation() != null) {
      nmRel = relation.getForeignRelation().getNmRelation();
    }

    if (nmRel != null && !entity.equals(relation.getForeignEntity())) {
      html.append(" via ").append(link(relation.getForeignEntity()));
      html.append(" as ").append(escape(relation.getName()));
      html.append(" [N:M]");
    }
    else {
      if (relation.isComposite() && !relation.isEmbedding()) {
        html.append(" composite");
      }
      appendVia(html, relation);
      if (!relation.getName().equalsIgnoreCase(referencingEntity.getName())) {
        html.append(" as ").append(escape(relation.getVariableName()));
      }
      if (relation.getRelationType() == RelationType.LIST && !relation.isReversed()) {
        html.append(" [1:N]");
      }
      else {
        if (relation.isReversed()) {
          html.append(" reversed");
        }
        html.append(" [1:1]");
      }
    }
    html.append("</li>\n");
  }

  /**
   * Mirrors {@code ModelCommentSupport.printComponents}.
   */
  private void appendComponents(StringBuilder html, Set<Relation> allRelations,
                                 Collection<Relation> compositeRelations, Collection<Entity> subEntities) {
    if (compositeRelations.isEmpty()) {
      return;
    }

    html.append("<ul>\n");
    for (Relation relation : compositeRelations) {

      boolean relationNotAlreadyPrinted = allRelations.add(relation);
      Entity component = relation.getForeignEntity();

      if (relationNotAlreadyPrinted) {
        for (Entity sub : subEntities) {
          if (!sub.getComponents().isEmpty()) {
            html.append("<li>^ ").append(link(sub)).append("\n");
            List<Relation> subCompositeRelations = new ArrayList<>();
            for (Relation subRelation : sub.getRelationsIncludingInherited()) {
              if (subRelation.isComposite()) {
                subCompositeRelations.add(subRelation);
              }
            }
            appendComponents(html, allRelations, subCompositeRelations, sub.getSubEntities());
            html.append("</li>\n");
          }
        }
      }

      html.append("<li>+ ").append(link(component));

      appendVia(html, relation);

      if (!relation.getName().equalsIgnoreCase(component.getName())) {
        html.append(" as ").append(escape(StringHelper.firstToLower(relation.getName())));
      }

      if (relation.getNmRelation() != null) {
        html.append(" [N:M] to ");
        Entity nmEntity = relation.getNmRelation().getForeignEntity();
        html.append(link(nmEntity));
        if (relation.getNmMethodName() != null &&
            !relation.getNmMethodName().equalsIgnoreCase(nmEntity.getName())) {
          html.append(" as ").append(escape(StringHelper.firstToLower(relation.getNmMethodName())));
        }
      }
      else {
        html.append(relation.getRelationType() == RelationType.LIST ? " [1:N]" : " [1:1]");
      }

      List<Relation> componentCompositeRelations = new ArrayList<>();
      for (Relation subRelation : component.getRelationsIncludingInherited()) {
        if (subRelation.isComposite()) {
          componentCompositeRelations.add(subRelation);
        }
      }

      if (relationNotAlreadyPrinted) {
        html.append('\n');
        appendComponents(html, allRelations, componentCompositeRelations, component.getSubEntities());
      }
      else if (relation.getForeignEntity().isComposite()) {
        html.append(" ...\n");
      }
      html.append("</li>\n");
    }
    html.append("</ul>\n");
  }

  /**
   * Mirrors {@code ModelCommentSupport.printSubEntities}.
   */
  private void appendSubEntities(StringBuilder html, Collection<Entity> subEntities) {
    if (subEntities.isEmpty()) {
      return;
    }
    html.append("<ul>\n");
    for (Entity sub : subEntities) {
      html.append("<li>^ ").append(link(sub));
      appendSubEntities(html, sub.getSubEntities());
      html.append("</li>\n");
    }
    html.append("</ul>\n");
  }

  /**
   * Mirrors {@code ModelCommentSupport.printNonCompositeRelations}.
   */
  private void appendNonCompositeRelations(StringBuilder html, Entity entity, List<Relation> relations,
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

    html.append("<ul>\n");
    for (Relation relation : sortedRelations) {
      html.append("<li>").append(link(relation.getForeignEntity()));
      if (rootsFromForeignEntity) {
        appendRootEntities(html, entity, relation.getForeignEntity().getRootEntities());
      }
      if (!relation.getEntity().equals(entity)) {
        html.append(" from ").append(link(relation.getEntity()));
        if (!rootsFromForeignEntity) {
          appendRootEntities(html, relation.getEntity(), relation.getEntity().getRootEntities());
        }
      }
      appendVia(html, relation);
      if (relation.isReversed()) {
        html.append(" reversed");
      }
      if (relation.isDeepReference()) {
        html.append(" deeply");
      }
      if (relation.getRelationType() == RelationType.LIST && !relation.isReversed()) {
        html.append(" [1:N]");
      }
      else {
        Relation nmRel = relation.getDefiningNmRelation();
        if (nmRel != null && entity.equals(nmRel.getEntity())) {
          html.append(" [N:M]");
        }
        else {
          html.append(" [1:1]");
        }
      }
      html.append("</li>\n");
    }
    html.append("</ul>\n");
  }

  /**
   * Mirrors {@code ModelCommentSupport.printRootEntities}.
   */
  private void appendRootEntities(StringBuilder html, Entity entity, Collection<Entity> rootEntities) {
    List<Entity> roots = new ArrayList<>(rootEntities);
    if (roots.size() == 1 && entity.equals(roots.get(0))) {
      roots.clear();
    }
    if (!roots.isEmpty()) {
      roots.sort(Comparator.comparing(Entity::getName));
      html.append(" (");
      appendEntityList(html, roots);
      html.append(')');
    }
  }

  private void appendEntityList(StringBuilder html, Collection<Entity> entityList) {
    boolean needComma = false;
    for (Entity e : entityList) {
      if (needComma) {
        html.append(", ");
      }
      needComma = true;
      html.append(link(e));
    }
  }


  // ------------------------------------------------------------------------------------- index page

  private void renderIndex() throws IOException {
    List<Entity> sorted = new ArrayList<>(entities);
    sorted.sort(Comparator.comparing(Entity::getName));

    StringBuilder html = new StringBuilder();
    appendPageHeader(html, title, false);

    List<Entity> roots = new ArrayList<>();
    for (Entity entity : sorted) {
      if (entity.isRootEntity()) {
        roots.add(entity);
      }
    }
    if (!roots.isEmpty()) {
      html.append("<h2>Aggregates</h2>\n<ul>\n");
      Set<Entity> visited = new HashSet<>();
      for (Entity root : roots) {
        html.append("<li>").append(link(root));
        appendComponentTree(html, root, visited);
        html.append("</li>\n");
      }
      html.append("</ul>\n");
    }

    html.append("<h2>All Entities</h2>\n<ul>\n");
    for (Entity entity : sorted) {
      html.append("<li>").append(link(entity));
      String comment = entity.getOptions().getComment();
      if (comment != null && !comment.isBlank()) {
        html.append(" &mdash; ").append(escape(comment));
      }
      html.append("</li>\n");
    }
    html.append("</ul>\n");

    appendPageFooter(html);

    writeFile(new File(outputDir, "index.html"), html.toString());
  }

  private void appendComponentTree(StringBuilder html, Entity entity, Set<Entity> visited) {
    if (!visited.add(entity)) {
      return;
    }
    List<Entity> components = new ArrayList<>(entity.getComponents());
    if (!components.isEmpty()) {
      components.sort(Comparator.comparing(Entity::getName));
      html.append("<ul>\n");
      for (Entity component : components) {
        html.append("<li>").append(link(component));
        appendComponentTree(html, component, visited);
        html.append("</li>\n");
      }
      html.append("</ul>\n");
    }
  }


  // ------------------------------------------------------------------------------------- page mechanics

  private void appendPageHeader(StringBuilder html, String heading) {
    appendPageHeader(html, heading, true);
  }

  private void appendPageHeader(StringBuilder html, String heading, boolean withIndexLink) {
    html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>")
        .append(escape(heading))
        .append("</title>\n<link rel=\"stylesheet\" href=\"base.css\">\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n");
    if (withIndexLink) {
      html.append("<p><a href=\"index.html\">&#8592; Index</a></p>\n");
    }
    html.append("<h1>").append(escape(heading)).append("</h1>\n");
  }

  private void appendPageFooter(StringBuilder html) {
    html.append("</body>\n</html>\n");
  }

  private static String link(Entity entity) {
    String name = escape(entity.getName());
    return "<a href=\"" + name + ".html\">" + name + "</a>";
  }

  private void writeFile(File file, String content) throws IOException {
    try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
      writer.write(content);
    }
  }

  /**
   * Writes the structural stylesheet (tables, lists, spacing).<br>
   * Always generated, regardless of {@link #cssFile} - a project overrides the *look* via
   * {@code style.css}, not the layout. Colors and fonts are taken from the
   * {@code --tentackle-doc-*} custom properties so a project's stylesheet can override just those,
   * or override selectors outright for full control since {@code style.css} is loaded after this one.
   */
  private void writeBaseStylesheet() throws IOException {
    String css =
        "body {\n" +
        "  font-family: var(--tentackle-doc-font, sans-serif);\n" +
        "  margin: 2em;\n" +
        "  color: var(--tentackle-doc-text-color, #222);\n" +
        "  background: var(--tentackle-doc-background-color, #fff);\n" +
        "}\n" +
        "table { border-collapse: collapse; margin: 0.5em 0 1.5em; }\n" +
        "table.meta td { padding: 2px 8px; }\n" +
        "table:not(.meta) th, table:not(.meta) td {\n" +
        "  border: 1px solid var(--tentackle-doc-border-color, #ccc);\n" +
        "  padding: 4px 8px;\n" +
        "  text-align: left;\n" +
        "}\n" +
        "table:not(.meta) th {\n" +
        "  background-color: var(--tentackle-doc-table-header-background-color, transparent);\n" +
        "  color: var(--tentackle-doc-table-header-text-color, inherit);\n" +
        "}\n" +
        "h1 { color: var(--tentackle-doc-heading-color, #333); }\n" +
        "h2, h3 {\n" +
        "  color: var(--tentackle-doc-accent-color, var(--tentackle-doc-heading-color, #333));\n" +
        "  border-top: 1px solid var(--tentackle-doc-accent-color, transparent);\n" +
        "  border-bottom: 1px solid var(--tentackle-doc-accent-color, transparent);\n" +
        "  padding: 0.2em 0;\n" +
        "}\n" +
        "a { color: var(--tentackle-doc-link-color, #0b5fa5); text-decoration: none; }\n" +
        "a:hover, a:focus {\n" +
        "  color: var(--tentackle-doc-accent-color, var(--tentackle-doc-link-color, #0b5fa5));\n" +
        "  text-decoration: underline;\n" +
        "}\n" +
        ".comment { font-style: italic; color: var(--tentackle-doc-comment-color, #555); }\n";
    writeFile(new File(outputDir, "base.css"), css);
  }

  /**
   * Writes the theme stylesheet (colors, fonts) as {@code style.css}.<br>
   * Copied verbatim from {@link #cssFile} if given, otherwise the default theme, which mimics the
   * brand of <a href="https://tentackle.org/">tentackle.org</a> itself - dark blue headings and
   * links, the signature orange-framed section headings, Roboto - including its dark-mode palette,
   * so a project can start from a copy of this and override only what it wants to change.
   */
  private void writeThemeStylesheet() throws IOException {
    if (cssFile != null) {
      Files.copy(cssFile.toPath(), new File(outputDir, "style.css").toPath(), StandardCopyOption.REPLACE_EXISTING);
      return;
    }
    String css =
        "/*\n" +
        " * Default theme - mimics tentackle.org (see src/mkdocs/theme/stylesheets/extra.css and\n" +
        " * src/site/resources/css/site.css for the same palette applied to the docs site and the\n" +
        " * generated Maven site). Provide a project-specific stylesheet via the document goal's\n" +
        " * cssFile parameter to replace this.\n" +
        " */\n" +
        ":root {\n" +
        "  --tentackle-doc-font: \"Roboto\", \"Helvetica Neue\", Helvetica, Arial, sans-serif;\n" +
        "  --tentackle-doc-text-color: #1a1a1a;\n" +
        "  --tentackle-doc-background-color: #ffffff;\n" +
        "  --tentackle-doc-border-color: #dddddd;\n" +
        "  --tentackle-doc-heading-color: #00305e;\n" +
        "  --tentackle-doc-accent-color: #f18319;\n" +
        "  --tentackle-doc-link-color: #1663b0;\n" +
        "  --tentackle-doc-comment-color: #555555;\n" +
        "  --tentackle-doc-table-header-background-color: #00305e;\n" +
        "  --tentackle-doc-table-header-text-color: #ffffff;\n" +
        "}\n" +
        "@media (prefers-color-scheme: dark) {\n" +
        "  :root {\n" +
        "    --tentackle-doc-text-color: #dcdcdc;\n" +
        "    --tentackle-doc-background-color: #1a1c22;\n" +
        "    --tentackle-doc-border-color: #444444;\n" +
        "    --tentackle-doc-heading-color: #e8e8e8;\n" +
        "    --tentackle-doc-accent-color: #f6a24f;\n" +
        "    --tentackle-doc-link-color: #5b8fc7;\n" +
        "    --tentackle-doc-comment-color: #a8a8a8;\n" +
        "    --tentackle-doc-table-header-background-color: #00203f;\n" +
        "    --tentackle-doc-table-header-text-color: #dcdcdc;\n" +
        "  }\n" +
        "}\n";
    writeFile(new File(outputDir, "style.css"), css);
  }

  private static String escape(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder buf = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> buf.append("&amp;");
        case '<' -> buf.append("&lt;");
        case '>' -> buf.append("&gt;");
        case '"' -> buf.append("&quot;");
        default -> buf.append(c);
      }
    }
    return buf.toString();
  }

}
