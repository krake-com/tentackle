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

import org.tentackle.model.Entity;

/**
 * Prints the model of an entity.
 *
 * @author harald
 */
public class EntityPrinter {

  private final Entity entity;
  private final PrintConfiguration configuration;

  /**
   * Creates an entity printer.
   *
   * @param entity the entity to print
   * @param configuration the printing configuration
   */
  public EntityPrinter(Entity entity, PrintConfiguration configuration) {
    this.entity = entity;
    this.configuration = configuration;
  }

  /**
   * Creates a pretty print of the entity model.
   *
   * @return the printed entity model
   */
  public String print() {
    StringBuilder buf = new StringBuilder();

    if (configuration.isPrintingAsComment()) {
      buf.append("/**\n");
      if (configuration.isUsingVariables()) {
        buf.append(" * @> $mapping\n");
      }
      else {
        buf.append(" * @> ").append(entity.getTableName()).append(".map");
      }
    }

    if (configuration.isPrintingAsComment()) {
      buf.append("*/\n");
    }

    return buf.toString();
  }

}
