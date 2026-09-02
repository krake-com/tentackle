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
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;
import org.tentackle.model.impl.IndexImpl;

/**
 * Prints the model spec of an index.
 *
 * @author harald
 */
public class IndexPrinter {

  private final Index index;
  private final PrintConfiguration configuration;

  /**
   * Creates an index printer.
   *
   * @param index the index
   * @param configuration the printing configuration
   */
  public IndexPrinter(Index index, PrintConfiguration configuration) {
    this.index = index;
    this.configuration = configuration;
  }


  /**
   * Prints the index.
   *
   * @return the pretty printed index
   */
  public String print() {
    StringBuilder buf = new StringBuilder();
    if (!StringHelper.isAllWhitespace(index.getComment())) {
      if (configuration.asComment()) {
        buf.append(" * ");
      }
      buf.append("# ").append(index.getComment()).append('\n');
    }
    if (configuration.asComment()) {
      buf.append(" * ");
    }
    if (index.isOptional()) {
      buf.append("optional ");
    }
    if (index.isUnique()) {
      buf.append("unique ");
    }
    if (index instanceof IndexImpl indexImpl && indexImpl.isDeferrabilityExplicit()) {
      buf.append(switch (index.getDeferrability()) {
        case DEFERRABLE_DEFERRED -> "deferred ";
        case DEFERRABLE_IMMEDIATE -> "deferrable ";
        case NOT_DEFERRABLE -> "immediate ";
      });
    }
    buf.append("index ").append(index.getName()).append(" := ");
    boolean needComma = false;
    for (IndexAttribute indexAttribute: index.getAttributes()) {
      if (needComma) {
        buf.append(", ");
      }
      else {
        needComma = true;
      }
      if (indexAttribute.isDescending()) {
        buf.append('-');
      }
      else {
        buf.append('+');
      }
      if (indexAttribute.getFunctionName() != null) {
        buf.append(indexAttribute.getFunctionName()).append('(');
      }
      String colName = indexAttribute.getAttribute().getColumnName();
      buf.append(colName);
      if (!colName.equals(indexAttribute.getColumnName())) {
        // multi-column type
        buf.append('#').append(indexAttribute.getColumnName().substring(colName.length()));
      }
      if (indexAttribute.getFunctionName() != null) {
        buf.append(')');
      }
    }
    if (index.getFilterCondition() != null) {
      buf.append(" | ").append(index.getFilterCondition());
    }
    buf.append('\n');
    return buf.toString();
  }

}
