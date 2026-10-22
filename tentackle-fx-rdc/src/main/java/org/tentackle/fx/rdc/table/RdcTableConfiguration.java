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
package org.tentackle.fx.rdc.table;

import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.fx.table.TableColumnConfiguration;

/**
 * PDO-aware table configuration.<br>
 * Creates {@link RdcTableColumnConfiguration}s.
 *
 * @param <S> type of the objects contained within the table's items list
 */
public class RdcTableConfiguration<S> extends DefaultTableConfiguration<S> {

  /**
   * Creates a configuration.
   *
   * @param template a template object
   * @param name the table's name, null if basename from effective class of template
   */
  public RdcTableConfiguration(S template, String name) {
    super(template, name);
  }

  /**
   * Creates a configuration.
   *
   * @param objectClass the object class
   * @param name the table's name, null if basename from effective class of template
   */
  public RdcTableConfiguration(Class<S> objectClass, String name) {
    super(objectClass, name);
  }

  @Override
  protected TableColumnConfiguration<S, ?> createTableColumnConfiguration(String name, String displayedName) {
    return new RdcTableColumnConfiguration<>(this, name, displayedName);
  }

}
