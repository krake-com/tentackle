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
import org.tentackle.session.PersistenceException;

@Service(ModificationTypeFactory.class)
public class ExtendedModificationTypeFactory extends ModificationTypeFactory {

  @Override
  public ModificationType create(Character modType) {
    ModificationType modificationType = AdditionalModificationType.toInternalImpl(modType);
    if (modificationType == null) {
      modificationType = DbModificationType.toInternalImpl(modType);
      if (modificationType == null) {
        throw new PersistenceException("illegal modType '" + modType + "'");
      }
    }
    return modificationType;
  }

}
