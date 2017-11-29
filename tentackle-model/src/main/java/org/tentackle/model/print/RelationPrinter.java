/*
 * Tentackle - http://www.tentackle.org
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

import java.util.List;
import java.util.Objects;
import org.tentackle.common.StringHelper;
import org.tentackle.model.AccessScope;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.impl.RelationImpl;


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
   * @return the pretty printed index
   */
  public String print() {
    StringBuilder buf = new StringBuilder();
    if (!StringHelper.isAllWhitespace(relation.getComment()) &&
        !relation.getComment().equals(relation.getName())) {
      if (configuration.isPrintingAsComment()) {
        buf.append(" * ");
      }
      buf.append("# ").append(relation.getComment()).append('\n');
    }
    if (configuration.isPrintingAsComment()) {
      buf.append(" * ");
    }
    buf.append(relation.getClassName()).append(":\n");

    boolean needComma = false;

    if (relation.getRelationType() != RelationType.OBJECT || relation.isReversed() ||
        relation.isComposite() || relation.isReferenced() || relation.isProcessed() ||
        relation.isSerialized() && !relation.isComposite() ||
        relation.isClearOnRemoteSave() ||
        relation.isTracked() && !relation.getEntity().isTracked()) {
      // need relation line
      lead(buf).append(RelationImpl.RELATION).append(" =");
      if (relation.isComposite()) {
        buf.append(' ').append(RelationImpl.COMPOSITE);
      }
      if (relation.isTracked() && !relation.getEntity().isTracked()) {
        buf.append(' ').append(RelationImpl.TRACKED);
      }
      if (relation.isReferenced()) {
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
      if (relation.isReversed()) {
        buf.append(' ').append(RelationImpl.REVERSED);
      }
      else {
        buf.append(' ').append(relation.getRelationType().toString().toLowerCase());
      }
      needComma = true;
    }

    if (relation.getSelectionType() != SelectionType.LAZY && !relation.isSelectionCached() ||
        relation.getSelectionType() != SelectionType.ALWAYS && relation.isSelectionCached() ||
        relation.isSelectionCached() || relation.getSelectionWurbletArguments() != null) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.SELECT).append(" =");
      boolean selectionTypePrinted = false;
      if (relation.isSelectionCached()) {
        if (relation.getSelectionType() != SelectionType.ALWAYS) {
          buf.append(' ').append(relation.getSelectionType().toString().toLowerCase());
          selectionTypePrinted = true;
        }
        buf.append(' ').append(RelationImpl.CACHED);
      }
      else if (relation.getSelectionType() != SelectionType.LAZY) {
        buf.append(' ').append(relation.getSelectionType().toString().toLowerCase());
        selectionTypePrinted = true;
      }
      if (relation.getSelectionWurbletArguments() != null) {
        if (!selectionTypePrinted) {
          buf.append(' ').append(relation.getSelectionType().toString().toLowerCase());
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
            (configuration.getModelDefaults() == null ||
             configuration.getModelDefaults().getDeletionCascaded() == null ||
             !configuration.getModelDefaults().getDeletionCascaded())) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.DELETE).append(" =");
      if (relation.isDeletionCascaded() &&
            (configuration.getModelDefaults() == null ||
             configuration.getModelDefaults().getDeletionCascaded() == null ||
             !configuration.getModelDefaults().getDeletionCascaded())) {
        buf.append(' ').append(RelationImpl.CASCADE);
      }
      needComma = true;
    }

    if (relation.getLinkMethodName() != null || relation.getLinkMethodIndex() != null) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.LINK).append(" =");
      if (relation.getLinkMethodName() != null) {
        buf.append(' ').append(relation.getLinkMethodName());
      }
      if (relation.getLinkMethodIndex() != null) {
        buf.append(' ').append(relation.getLinkMethodIndex());
      }
      needComma = true;
    }

    if (relation.getMethodArgs().size() > 1) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.ARGS).append(" =");
      boolean firstSkipped = false;
      for (MethodArgument arg: relation.getMethodArgs()) {
        if (firstSkipped) {
          buf.append(' ').append(arg.getForeignAttribute()).append('[').append(arg.getMethodArgument()).append(']');
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
      needComma = true;
    }

    if (relation.getAccessScope() != AccessScope.PUBLIC) {
      if (needComma) {
        buf.append(",\n");
      }
      lead(buf).append(RelationImpl.SCOPE).append(" = ").append(relation.getAccessScope().toString().toLowerCase());
      needComma = true;
    }

    List<String> annotations = relation.getAnnotations();
    if (annotations != null && !annotations.isEmpty()) {
      for (String annotation : annotations) {
        if (needComma) {
          buf.append(",\n");
        }
        lead(buf).append(new AnnotationPrinter(annotation).print());
      }
    }

    if (needComma) {
      buf.append('\n');
    }

    return buf.toString();
  }


  private StringBuilder lead(StringBuilder buf) {
    if (configuration.isPrintingAsComment()) {
      buf.append(" * ");
    }
    buf.append("    ");
    return buf;
  }

}
