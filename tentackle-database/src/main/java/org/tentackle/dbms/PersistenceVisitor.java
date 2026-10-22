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

import org.tentackle.reflect.ReflectiveVisitor;

import java.io.Serial;
import java.io.Serializable;

/**
 * A reflective visitor to add application-specific functionality to persistence operations.
 * <p>
 * Visitors can be registered at a {@link Db} and are only valid within a single transaction.
 * If present, the visitors will be consulted before each persistence operation such as
 * {@link DbModificationType#INSERT}, {@link DbModificationType#UPDATE} or {@link DbModificationType#DELETE}, whether
 * the operation is allowed or should be <em>silently</em> skipped.
 *
 * <p>
 * The methods are invoked in the following order:
 * <ol>
 * <li>{@code visit(org.tentackle.dbms.AbstractDbObject, Character)} which in turn invokes the type-specific visit method</li>
 * <li>{@link #isPersistenceOperationAllowed(AbstractDbObject, ModificationType)}</li>
 * </ol>
 *
 * A visitor may also throw a {@link org.tentackle.session.PersistenceException} if the operation must not continue.
 * <p>
 * The effective visit method for the concrete persistence class is determined by reflection.
 * The most specific method will be used, which allows using a method for a class hierarchy.
 * <p>
 * Notice that the visit method must use a Character instead of char to be found by the method dispatcher.
 *
 * @author harald
 */
public abstract class PersistenceVisitor extends ReflectiveVisitor implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a visitor.
   */
  public PersistenceVisitor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Checks if the persistence operation is allowed.
   *
   * @param object the persistable object
   * @param modType the modification type
   * @return true if allowed
   */
  public boolean isPersistenceOperationAllowed(AbstractDbObject<?> object, ModificationType modType) {
    return true;
  }

}
