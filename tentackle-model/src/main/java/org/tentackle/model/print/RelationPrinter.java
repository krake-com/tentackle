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

package org.tentackle.model.print;

import org.tentackle.common.StringHelper;
import org.tentackle.model.AccessScope;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.impl.RelationImpl;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Prints the model spec of a relation.
 *
 * @author harald
 */
public class RelationPrinter {

  private final Relation relation;
  private final PrintConfiguration configuration;

  /**
   * Creates a relation printer.
   *
   * @param relation the relation
   * @param configuration the printing configuration
   */
  public RelationPrinter(Relation relation, PrintConfiguration configuration) {
    this.relation = relation;
    this.configuration = configuration;
  }


  /**
   * Prints the relation.
   *
   * @return the pretty printed relation
   */
  public String print() {
    StringBuilder buf = new StringBuilder();
    if (!StringHelper.isAllWhitespace(relation.getComment()) &&
        !relation.getComment().equals(relation.getName())) {
      if (configuration.asComment()) {
        buf.append(" * ");
      }
      buf.append("# ").append(relation.getComment()).append('\n');
    }
    if (configuration.asComment()) {
      buf.append(" * ");
    }
    buf.append(relation.getClassName()).append(":\n");

    boolean needComma = false;

    // suppress "relation = serialized object" in compact mode, and always for embedding relations
    boolean skipCompact = (configuration.compact() && !relation.isComposite() && relation.isSerialized() &&
                           relation.getRelationType() == RelationType.OBJECT &&
                           !relation.isProcessed() && !relation.isReferenced() &&
                           !relation.isClearOnRemoteSave() && !relation.isImmutable() &&
                           !relation.isShallow() && !relation.isPartOfNormText() &&
                           !relation.isReversed()) ||
                          relation.isEmbedding();

    if (!skipCompact &&
        (relation.getRelationType() != RelationType.OBJECT || relation.isReversed() ||
         relation.isComposite() || relation.isReferenced() || relation.isProcessed() ||
         relation.isSerialized() && !relation.isComposite() ||
         relation.isClearOnRemoteSave() || relation.isPartOfNormText() ||
         relation.isShallow() || relation.isImmutable() ||
         relation.isTracked() && !relation.getEntity().isTracked())) {
      // need relation line
      lead(buf).append(RelationImpl.RELATION).append(" =");
      if (relation.isComposite()) {
        buf.append(' ').append(RelationImpl.COMPONENT);
      }
      if (relation.isTracked() && !relation.getEntity().isTracked()) {
        buf.append(' ').append(RelationImpl.TRACKED);
      }
      if (relation.isReferenced() &&
          (!configuration.compact() ||
           // see ModelImpl.updateRelations(EntityInfo)
           relation.getLinkMethodName() == null || !relation.isComposite() || relation.getForeignEntity() == null)) {
        buf.append(' ').append(RelationImpl.REFERENCED);
      }
      if (relation.isProcessed()) {
        buf.append(' ').append(RelationImpl.PROCESSED);
      }
      if (relation.isSerialized() && !relation.isComposite()) {
        buf.append(' ').append(RelationImpl.SERIALIZED);
      }
      if (relation.isClearOnRemoteSave()) {
        buf.append(' ').append(RelationImpl.REMOTECLEAR);
      }
      if (relation.isImmutable()) {
        buf.append(' ').append(RelationImpl.IMMUTABLE);
      }
      if (relation.isShallow()) {
        buf.append(' ').append(RelationImpl.SHALLOW);
      }
      if (relation.isPartOfNormText()) {
        buf.append(' ').append(RelationImpl.PART_OF_NORMTEXT);
      }
      if (relation.isReversed()) {
        buf.append(' ').append(RelationImpl.REVERSED);
      }
      else if (!configuration.compact() || relation.getRelationType() != RelationType.OBJECT) {
        buf.append(' ').append(relation.getRelationType().toString().toLowerCase(Locale.ROOT));
      }
      needComma = true;
    }

    // skip "select = cached" if foreign entity is providing a cache and not the NM parent
    // skip "select = embedded" if foreign entity is embedded
    skipCompact = false;
    if (configuration.compact()) {
      Relation parentRelation = relation.getForeignEntity().getRelation(relation.getEntity().getName(), true);
      if (parentRelation == null) {
        // check by entity instead of name (in case relation name was changed)
        List<Relation> parentRelations = relation.getForeignEntity().getRelations(relation.getEntity(), true);
        if (parentRelations.size() == 1) {
          parentRelation = parentRelations.get(0);
        }
      }
      skipCompact = (relation.isSelectionCached() && relation.getForeignEntity().getOptions().isCached() &&
                     (parentRelation == null || parentRelation.getNmRelation() == null)) ||
                    (relation.isEmbedding() && relation.getForeignEntity().isEmbedded());
    }

    if (!skipCompact &&
        (relation.getSelectionType() != SelectionType.LAZY && !relation.isSelectionCached() ||
         relation.getSelectionType() != SelectionType.ALWAYS && relation.isSelectionCached() ||
         relation.isSelectionCached() || relation.getSelectionWurbletArguments() != null)) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.SELECT).append(" =");
      boolean selectionTypePrinted = false;
      if (relation.isSelectionCached()) {
        if (relation.getSelectionType() != SelectionType.ALWAYS) {
          appendSelectionType(buf, relation);
          selectionTypePrinted = true;
        }
        buf.append(' ').append(RelationImpl.CACHED);
      }
      else if (relation.getSelectionType() != SelectionType.LAZY) {
        appendSelectionType(buf, relation);
        selectionTypePrinted = true;
      }
      if (relation.getSelectionWurbletArguments() != null) {
        if (!selectionTypePrinted) {
          appendSelectionType(buf, relation);
        }
        if (relation.getSelectionWurbletArguments().startsWith("|")) {
          buf.append(' ');
        }
        else {
          buf.append(" | ");
        }
        buf.append(relation.getSelectionWurbletArguments());
      }
      needComma = true;
    }

    if (relation.isDeletionFromMainClass() ||
        relation.isDeletionCascaded() &&
        (configuration.modelDefaults() == null ||
         configuration.modelDefaults().getDeletionCascaded() == null ||
         !configuration.modelDefaults().getDeletionCascaded())) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.DELETE).append(" =");
      if (relation.isDeletionCascaded() &&
          (configuration.modelDefaults() == null ||
           configuration.modelDefaults().getDeletionCascaded() == null ||
           !configuration.modelDefaults().getDeletionCascaded())) {
        buf.append(' ').append(RelationImpl.CASCADE);
      }
      needComma = true;
    }

    // skip "link = set<Entity>"
    skipCompact = configuration.compact() && relation.getLinkMethodName() != null &&
                  relation.getLinkMethodName().equals("set" + relation.getEntity()) &&
                  relation.getLinkMethodIndex() == null;

    if (!skipCompact &&
        (relation.getLinkMethodName() != null || relation.getLinkMethodIndex() != null)) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.LINK).append(" =");
      if (relation.getLinkMethodName() != null) {
        buf.append(' ');
        if (configuration.compact() && relation.getLinkMethodName().equals("set" + relation.getEntity())) {
          buf.append("set");
        }
        else {
          buf.append(relation.getLinkMethodName());
        }
      }
      if (relation.getLinkMethodIndex() != null) {
        buf.append(' ').append(relation.getLinkMethodIndex());
      }
      needComma = true;
    }

    if (relation.getCountAttribute() != null) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.COUNT).append(" = ").append(relation.getCountAttribute());
      needComma = true;
    }

    if (relation.getMethodName() != null &&
        (relation.getRelationType() == RelationType.OBJECT ||
         !relation.getMethodName().equalsIgnoreCase(relation.getEntity().getName() + "Id"))) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.METHOD).append(" = ").append(relation.getMethodName());
      needComma = true;
    }

    if (relation.getMethodArgs().size() > 1) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.ARGS).append(" =");
      boolean firstSkipped = false;
      for (MethodArgument arg : relation.getMethodArgs()) {
        if (firstSkipped) {
          buf.append(' ').append(arg.getForeignAttributeName()).append('[').append(arg.getMethodArgument()).append(']');
        }
        firstSkipped = true;
      }
      needComma = true;
    }

    if (!Objects.equals(relation.getClassName(), relation.getName())) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.NAME).append(" = ").append(relation.getName());
      needComma = true;
    }

    if (relation.getNmName() != null) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.NM).append(" = ").append(relation.getNmName());
      if (relation.getNmMethodName() != null) {
        buf.append(' ').append(relation.getNmMethodName());
      }
      if (relation.getNmScope() != relation.getAccessScope()) {
        buf.append(' ').append(relation.getNmScope().toString().toLowerCase(Locale.ROOT));
      }
      needComma = true;
    }

    if (relation.isEmbedding() &&
        !(StringHelper.toLower(relation.getName()) + "_").equals(relation.getColumnPrefix())) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.PREFIX).append(" =");
      if (!StringHelper.isAllWhitespace(relation.getColumnPrefix())) {
        buf.append(' ').append(relation.getColumnPrefix());
      }
      needComma = true;
    }

    if (relation.getAccessScope() != AccessScope.PUBLIC) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.SCOPE).append(" = ").append(relation.getAccessScope().toString().toLowerCase(Locale.ROOT));
      needComma = true;
    }

    List<String> annotations = relation.getAnnotations();
    if (annotations != null) {
      for (String annotation : annotations) {
        if (needComma) {
          buf.append(",\n");
        }
        lead(buf).append(new AnnotationPrinter(annotation).print());
        needComma = true;
      }
    }
    List<String> stereotypes = relation.getStereotypes();
    if (stereotypes != null) {
      for (String stereotype : stereotypes) {
        if (needComma) {
          buf.append(",\n");
        }
        lead(buf).append('#').append(stereotype);
        needComma = true;
      }
    }

    if (needComma) {
      buf.append('\n');
    }

    return buf.toString();
  }


  /**
   * Appends the indentation of a relation property line.
   *
   * @param buf the buffer to append to
   * @return the buffer
   */
  private StringBuilder lead(StringBuilder buf) {
    if (configuration.asComment()) {
      buf.append(" * ");
    }
    buf.append("    ");
    return buf;
  }

  /**
   * Appends the selection strategy, followed by the eager depth if it is not the default.
   *
   * @param buf the buffer to append to
   * @param relation the relation
   */
  private void appendSelectionType(StringBuilder buf, Relation relation) {
    buf.append(' ').append(relation.getSelectionType().toString().toLowerCase(Locale.ROOT));
    if (relation.getSelectionType() == SelectionType.EAGER && relation.getEagerDepth() > 1) {
      buf.append(RelationImpl.DEPTH_SEPARATOR).append(relation.getEagerDepth());
    }
  }

}
