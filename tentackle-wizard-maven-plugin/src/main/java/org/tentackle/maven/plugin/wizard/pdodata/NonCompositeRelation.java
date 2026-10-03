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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.model.Relation;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Info for a non-composite object relation.<br>
 * Used for members such as blahId or N:M relations.<br>
 * The related PDOs must provide a unique domain key.
 */
public class NonCompositeRelation {

  private final Relation relation;                        // the 1:1 object relation or the composite list relation for N:M links
  private final PersistentDomainObject<?> relatedPdo;     // the related PDO

  /**
   * Creates the relation info.
   *
   * @param relation the 1:1 object relation, or the composite list relation for N:M links
   * @param relatedPdo the related PDO, must provide a unique domain key
   */
  public NonCompositeRelation(Relation relation, PersistentDomainObject<?> relatedPdo) {
    this.relation = relation;
    this.relatedPdo = relatedPdo;
  }

  /**
   * Gets the relation.
   *
   * @return the 1:1 object relation, or the composite list relation for N:M links
   */
  public Relation getRelation() {
    return relation;
  }

  /**
   * Gets the related PDO.
   *
   * @return the PDO, never null
   */
  public PersistentDomainObject<?> getRelatedPdo() {
    return relatedPdo;
  }

  @Override
  public String toString() {
    return relatedPdo.getEffectiveClass().getSimpleName() + " " + relatedPdo.getUniqueDomainKey();
  }

}
