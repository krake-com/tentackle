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

import java.util.ArrayList;
import java.util.List;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;

/**
 * Pretty prints the section containing the attributes.
 *
 * @author harald
 */
public class AttributeSectionPrinter {

  private final List<AttributePrinter> printers;
  private final PrintConfiguration configuration;

  /**
   * Creates a section printer.
   *
   * @param entity the entity
   * @param configuration the configuration
   */
  public AttributeSectionPrinter(Entity entity, PrintConfiguration configuration) {
    this.configuration = configuration;
    printers = new ArrayList<>();
    for (Attribute attribute: entity.getAttributes()) {
      if (!attribute.isImplicit()) {
        printers.add(new AttributePrinter(attribute, configuration));
      }
    }
  }

  /**
   * Pretty prints the attribute section.
   *
   * @return the printed section
   */
  public String print() {
    int maxTypeLen = 0;
    int maxNameLen = 0;
    int maxColumnLen = 0;
    for (AttributePrinter printer: printers) {
      String str = printer.printType();
      if (maxTypeLen < str.length()) {
        maxTypeLen = str.length();
      }
      str = printer.printJavaName();
      if (maxNameLen < str.length()) {
        maxNameLen = str.length();
      }
      str = printer.printColumnName();
      if (maxColumnLen < str.length()) {
        maxColumnLen = str.length();
      }
    }
    maxTypeLen += configuration.columnGap();
    maxNameLen += configuration.columnGap();
    maxColumnLen += configuration.columnGap();

    StringBuilder buf = new StringBuilder();
    for (AttributePrinter printer: printers) {
      if (configuration.asComment()) {
        buf.append(" * ");
      }
      buf.append(StringHelper.fillRight(printer.printType(), maxTypeLen, ' '))
         .append(StringHelper.fillRight(printer.printJavaName(), maxNameLen, ' '))
         .append(StringHelper.fillRight(printer.printColumnName(), maxColumnLen, ' '))
         .append(printer.printComment())
         .append('\n');
    }
    return buf.toString();
  }

}
