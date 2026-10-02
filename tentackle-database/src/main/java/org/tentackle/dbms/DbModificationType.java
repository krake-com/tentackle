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

/**
 * The persistence layer's modification types.<br>
 * The external character values are stored in the modification log, see {@link ModificationLog}.
 */
public enum DbModificationType implements ModificationType {

  /** begin transaction. */
  BEGIN('B'),

  /** commit transaction. */
  COMMIT('C'),

  /** insert object. */
  INSERT('I'),

  /** update object. */
  UPDATE('U'),

  /** delete object. */
  DELETE('D');

  private final Character modType;

  DbModificationType(Character modType) {
    this.modType = modType;
  }

  @Override
  public Character toExternal() {
    return modType;
  }

  /**
   * Maps a character value to a modification type.
   *
   * @param modType the character value
   * @return the modification type, null if no such type
   */
  public static DbModificationType toInternalImpl(Character modType) {
    for (DbModificationType modificationType : DbModificationType.values()) {
      if (modificationType.modType.equals(modType)) {
        return modificationType;
      }
    }
    return null;    // no exception! (see ModificationTypeFactory)
  }

}
