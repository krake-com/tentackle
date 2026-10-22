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

package org.tentackle.maven.plugin.wizard.fx;

import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.maven.plugin.wizard.pdodata.DataNode;

import java.util.function.Predicate;

/**
 * Table configuration extended by a {@link Predicate}.
 *
 * @param <T> the node type
 */
public class DataNodeTableConfiguration<T extends DataNode> extends DefaultTableConfiguration<T> {

  private Predicate<T> predicate;

  /**
   * Creates a configuration.
   *
   * @param objectClass the object class
   * @param name the table's name, null if basename of the object class
   */
  public DataNodeTableConfiguration(Class<T> objectClass, String name) {
    super(objectClass, name);
  }

  /**
   * Gets the predicate.
   *
   * @return null if none
   */
  public Predicate<T> getPredicate() {
    return predicate;
  }

  /**
   * Sets the predicate.<br>
   * Usually {@code "node -> ((DataDiff) node).isDifferent()"}.
   *
   * @param predicate the predicate, null if none
   */
  public void setPredicate(Predicate<T> predicate) {
    this.predicate = predicate;
  }

}
