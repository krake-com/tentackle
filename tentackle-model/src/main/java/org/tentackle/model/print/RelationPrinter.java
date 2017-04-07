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

import org.tentackle.common.StringHelper;
import org.tentackle.model.Relation;


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
    if (!StringHelper.isAllWhitespace(relation.getComment())) {
      if (configuration.isPrintingAsComment()) {
        buf.append(" * ");
      }
      buf.append("# ").append(relation.getComment()).append('\n');
    }
    if (configuration.isPrintingAsComment()) {
      buf.append(" * ");
    }
    buf.append(relation.getClassName()).append(":\n");

    // @todo...
    return buf.toString();
  }

}
