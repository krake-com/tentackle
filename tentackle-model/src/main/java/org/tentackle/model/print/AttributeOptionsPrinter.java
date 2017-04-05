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

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeOptions;
import org.tentackle.model.EntityOptions;

import static org.tentackle.common.Constants.BIND_MAXCOL;
import static org.tentackle.common.Constants.BIND_SCALE;

/**
 * Pretty printer for attribute options.
 *
 * @author harald
 */
public class AttributeOptionsPrinter {

  private final Attribute attribute;

  /**
   * Creates an options printer.
   *
   * @param attribute the attribute
   */
  public AttributeOptionsPrinter(Attribute attribute) {
    this.attribute = attribute;
  }

  /**
   * Prints the options string (without brackets).
   *
   * @return the option string
   */
  public String print() {
    StringBuilder buf = new StringBuilder();
    EntityOptions entityOptions = attribute.getEntity().getOptions();
    AttributeOptions options = attribute.getOptions();

    buf.append(printBindOptions());

    if (entityOptions.isBind() != options.isBind()) {
      
    }

    if (buf.length() > 0 && buf.charAt(buf.length() - 1) == ',') {
      buf.setLength(buf.length() - 1);
    }

    return buf.toString();
  }


  /**
   * Prints the bind options.
   *
   * @return the bind option string
   */
  private String printBindOptions() {
    Set<String> entityOptionSet = new HashSet<>();
    Set<String> attributeOptionSet = new HashSet<>();
    String entityOptions = attribute.getEntity().getOptions().getBindOptions();
    if (entityOptions != null && !entityOptions.isEmpty()) {
      StringTokenizer stok = new StringTokenizer(entityOptions, ",");
      while (stok.hasMoreTokens()) {
        entityOptionSet.add(stok.nextToken());
      }
    }
    String attributeOptions = attribute.getOptions().getBindOptions();
    if (attributeOptions != null && !attributeOptions.isEmpty()) {
      StringTokenizer stok = new StringTokenizer(attributeOptions, ",");
      while (stok.hasMoreTokens()) {
        String option = stok.nextToken();
        if (option.startsWith(BIND_MAXCOL)) {
          int maxCol = Integer.parseInt(option.substring(BIND_MAXCOL.length() + 1));
          if (attribute.getSize() != null && attribute.getSize() == maxCol) {
            continue;
          }
        }
        if (option.startsWith(BIND_SCALE)) {
          int scale = Integer.parseInt(option.substring(BIND_SCALE.length() + 1));
          if (attribute.getScale() != null && attribute.getScale() == scale) {
            continue;
          }
        }
        attributeOptionSet.add(option);
      }
    }
    StringBuilder buf = new StringBuilder();
    for (String option: attributeOptionSet) {
      if (!entityOptionSet.contains(option)) {
        buf.append(option).append(',');
      }
    }
    return buf.toString();
  }

}
