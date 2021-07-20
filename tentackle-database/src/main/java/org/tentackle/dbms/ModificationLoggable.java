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

import org.tentackle.misc.Identifiable;
import org.tentackle.misc.SerialNumbered;
import org.tentackle.session.SessionDependable;

/**
 * Objects that can be modification-logged.
 *
 * @author harald
 */
public interface ModificationLoggable extends Identifiable, SerialNumbered, SessionDependable {

  /**
   * Creates a {@link ModificationLog}.
   *
   * @param modType the modification type
   * @return the created ModificationLog ready for saveObject()
   */
  default ModificationLog createModificationLog(ModificationType modType) {
    return ModificationLogFactory.getInstance().createModificationLog(this, modType);
  }

}
