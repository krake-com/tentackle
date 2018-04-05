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


package org.tentackle.dbms;

import java.util.HashSet;
import java.util.Set;
import org.tentackle.log.Logger;

/**
 * A visitor to ignore duplicates within a persistence operation.<br>
 * Used to avoid duplicate key errors, for example, if components are referenced more than once
 * within the same root entity.
 *
 * @author harald
 */
public class IgnoreDuplicatesPersistenceVisitor extends PersistenceVisitor {

  private static final Logger LOGGER = Logger.get(IgnoreDuplicatesPersistenceVisitor.class);

  private static final long serialVersionUID = 8166893128074169609L;


  private final Set<AbstractDbObject<?>> objectSet;   // all persisted objects so far
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
   * @param modType the modification type (ignored)
   */
  public void visit(AbstractDbObject<?> object, Character modType) {
    persistenceOperationAllowed = objectSet.add(object);
  }


  /**
   * Checks whether object has already been persisted.
   *
   * @param object the persistable object
   * @param modType the modification type (ignored)
   * @return true if object has not been persisted already
   */
  @Override
  public boolean isPersistenceOperationAllowed(AbstractDbObject<?> object, char modType) {
    if (!persistenceOperationAllowed && LOGGER.isInfoLoggable()) {
      LOGGER.info(object.toGenericString() + " already persisted -> '" + modType + "' ignored");
    }
    return persistenceOperationAllowed;
  }

}
