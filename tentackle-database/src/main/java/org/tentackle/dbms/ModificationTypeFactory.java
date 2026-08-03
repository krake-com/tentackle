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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.session.PersistenceException;


interface ModificationTypeFactoryHolder {
  ModificationTypeFactory INSTANCE = ServiceFactory.createService(ModificationTypeFactory.class);
}


/**
 * Factory for modification types.<br>
 * Basically maps character values to modification types.
 */
@Service(ModificationTypeFactory.class)   // defaults to self
public class ModificationTypeFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static ModificationTypeFactory getInstance() {
    return ModificationTypeFactoryHolder.INSTANCE;
  }


  /**
   * Creates a modification type factory.
   */
  public ModificationTypeFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  /**
   * Maps an external character value to a modification type.<br>
   * Throws a {@link PersistenceException} if unknown external value.
   *
   * @param modType the external character value
   * @return the modification type, never null
   */
  public ModificationType create(Character modType) {
    ModificationType modificationType = DbModificationType.toInternalImpl(modType);
    if (modificationType == null) {
      throw new PersistenceException("illegal modType '" + modType + "'");
    }
    return modificationType;
  }

}
