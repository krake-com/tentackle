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
      if (configuration.isPrintingAsComment()) {
        buf.append(" * ");
      }
      buf.append("# ").append(index.getComment()).append('\n');
    }
    if (configuration.isPrintingAsComment()) {
      buf.append(" * ");
    }
    if (index.isUnique()) {
      buf.append("unique ");
    }
    buf.append("index ").append(index.getName()).append(" := ");
    boolean needComma = false;
    for (IndexAttribute attr: index.getAttributes()) {
      if (needComma) {
        buf.append(", ");
      }
      else {
        needComma = true;
      }
      if (attr.isDescending()) {
        buf.append('-');
      }
      else {
        buf.append('+');
      }
      buf.append(attr.getAttribute().getColumnName());
    }
    if (index.getFilterCondition() != null) {
      buf.append(" | ").append(index.getFilterCondition());
    }
    buf.append('\n');
    return buf.toString();
  }

}
