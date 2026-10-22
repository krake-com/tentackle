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

import org.tentackle.misc.Convertible;
import org.tentackle.session.PersistenceException;

import java.io.Serializable;

/**
 * The modification type.<br>
 * The default types are defined in {@link DbModificationType}, which is an enum.
 * Since enums cannot be extended, we're using this interface instead.
 * Application-specific modification types can be introduced as follows:
 * <ul>
 *   <li>Create another enum implementing {@link ModificationType}.
 *   Make sure to use other character values than in {@link DbModificationType}!</li>
 *   <li>Create a subclass of {@link ModificationTypeFactory}, annotated with
 *   {@code @Service(ModificationTypeFactory.class)}, and override the create method.</li>
 * </ul>
 */
public interface ModificationType extends Convertible<Character>, Serializable {

  /**
   * Converts an external character value to a {@link ModificationType}.
   * <p>
   * Throws a {@link PersistenceException} if no valid type for the given character.
   *
   * @param modType the character value
   * @return the modification type, never null
   */
  static ModificationType toInternal(Character modType) {
    return ModificationTypeFactory.getInstance().create(modType);
  }

  /**
   * Gets the default modification type.
   *
   * @return always throws a {@link PersistenceException} because there is no default
   */
  static ModificationType getDefault() {
    throw new PersistenceException("there is no default modification type");
  }

  /**
   * Returns whether the type refers to an object state that
   * benefits from serialization via the {@link ModificationLog}'s {@code objectData} field.
   *
   * @return true if serializable
   */
  boolean isSerializable();

}
