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

import org.tentackle.common.ServiceFactory;


interface ModificationLogFactoryHolder {
  ModificationLogFactory INSTANCE = ServiceFactory.createService(
              ModificationLogFactory.class, DefaultModificationLogFactory.class);
}

/**
 * The {@link ModificationLog} factory.
 *
 * @author harald
 */
public interface ModificationLogFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static ModificationLogFactory getInstance() {
    return ModificationLogFactoryHolder.INSTANCE;
  }



  /**
   * Creates a modlog.
   *
   * @return the created modlog
   */
  ModificationLog createModificationLog();


  /**
   * Creates an empty modification log for a given session.<br>
   * Useful for reading the log or as a TRIP-proxy.
   *
   * @param db the session
   * @return the created modlog
   */
  ModificationLog createModificationLog(Db db);


  /**
   * Creates a modification log for a given session and modification type.
   *
   * @param db the session
   * @param modType is the modification-type
   * @return the created modlog
   */
  ModificationLog createModificationLog(Db db, ModificationType modType);


  /**
   * Creates a modification log from an object.
   *
   * @param object is the logged object
   * @param modType is the modification type (INSERT, UPDATE...)
   * @return the created modlog
   */
  ModificationLog createModificationLog(ModificationLoggable object, ModificationType modType);


  /**
   * Creates a modlog from another modlog, but a different type.
   *
   * @param template the modlog template
   * @param modType is the modification type (INSERT, UPDATE...)
   * @return the created modlog
   */
  ModificationLog createModificationLog(ModificationLog template, ModificationType modType);

}
