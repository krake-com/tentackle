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

package org.tentackle.dbms;

import org.tentackle.log.Logger;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

/**
 * A visitor to ignore duplicates within a persistence operation.<br>
 * Used to avoid duplicate key errors, for example, if components are referenced more than once
 * within the same root entity.
 *
 * @author harald
 */
public class IgnoreDuplicatesPersistenceVisitor extends PersistenceVisitor {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(IgnoreDuplicatesPersistenceVisitor.class);


  // not final: deserialized by TRIP (JEP-500)
  @SuppressWarnings("serial")
  private Set<AbstractDbObject<?>> objectSet;         // all persisted objects so far
  private boolean persistenceOperationAllowed;        // true if allowed

  /**
   * Creates the visitor.
   */
  public IgnoreDuplicatesPersistenceVisitor() {
    objectSet = new HashSet<>();
  }


  /**
   * Visits all objects.
   * <p>
   * This is the dispatched method!
   *
   * @param object the object
   * @param modType the modification type
   */
  public void visit(AbstractDbObject<?> object, ModificationType modType) {
    persistenceOperationAllowed = objectSet.add(object);
  }

  /**
   * Checks whether the object has already been persisted.
   *
   * @param object the persistable object
   * @param modType the modification type
   * @return true if the object has not been persisted already
   */
  @Override
  public boolean isPersistenceOperationAllowed(AbstractDbObject<?> object, ModificationType modType) {
    if (!persistenceOperationAllowed) {
      LOGGER.info(() -> object.toGenericString() + " already persisted -> '" + modType + "' ignored");
    }
    return persistenceOperationAllowed;
  }

}
