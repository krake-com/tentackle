/**
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

// Created on August 22, 2002, 8:28 PM

package org.tentackle.swing.rdc;

import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Session;

import java.io.Serializable;

/**
 * Transfer data for an {@link PersistentDomainObject}.
 * <p>
 * Used in drag and drop.
 *
 * @author harald
 * @param <T> the pdo type
 */
public class PdoTransferData<T extends PersistentDomainObject<T>> implements Serializable {

  private static final long serialVersionUID = 1L;

  private final long   id;              // the object id
  private final String className;       // the name of the pdo class


  /**
   * Creates a transfer data object.
   *
   * @param className the name of the PDO class
   * @param id the object ID
   */
  public PdoTransferData(String className, long id) {
    this.className = className;
    this.id        = id;
  }

  /**
   * Creates a transfer data object.
   *
   * @param obj the database object
   */
  public PdoTransferData(T obj) {
    this(obj.getClassBaseName(), obj.getId());
  }



  /**
   * Gets the object ID of the database object.
   *
   * @return the object ID
   */
  public long getId()  {
    return id;
  }


  /**
   * Gets the basename of the database object class.
   *
   * @return the class basenanme
   */
  public String getClassName() {
    return className;
  }


  /**
   * Selects the object from the session backend and sets its context.
   *
   * @param session the session
   * @return the database object, null if no such object
   */
  @SuppressWarnings("unchecked")
  public T getPdo(Session session) {
    return (T) Pdo.create(className, session).select(id);
  }

}
