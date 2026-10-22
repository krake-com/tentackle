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

public enum AdditionalModificationType implements ModificationType {

  /** begin transaction. */
  SOMETHING('a'),

  /** commit transaction. */
  DIFFERENT('d');

  private final Character modType;

  AdditionalModificationType(Character modType) {
    this.modType = modType;
  }

  @Override
  public Character toExternal() {
    return modType;
  }

  @Override
  public boolean isSerializable() {
    return false;
  }

  public static AdditionalModificationType toInternalImpl(Character modType) {
    for (AdditionalModificationType modificationType : AdditionalModificationType.values()) {
      if (modificationType.modType.equals(modType)) {
        return modificationType;
      }
    }
    return null;    // no exception! (see ModificationTypeFactory)
  }

}
