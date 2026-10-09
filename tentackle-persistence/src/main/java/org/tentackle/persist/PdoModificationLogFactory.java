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

package org.tentackle.persist;

import org.tentackle.common.Service;
import org.tentackle.dbms.DefaultModificationLogFactory;
import org.tentackle.dbms.ModificationLog;
import org.tentackle.dbms.ModificationLogFactory;
import org.tentackle.dbms.ModificationType;
import org.tentackle.misc.SerialNumbered;
import org.tentackle.session.SessionDependable;

/**
 * Replaces the default modification log factory to make it PDO compatible.
 */
@Service(ModificationLogFactory.class)
public class PdoModificationLogFactory extends DefaultModificationLogFactory {

  @Override
  public <T extends SessionDependable & SerialNumbered> ModificationLog createModificationLog(T object, ModificationType modType) {
    ModificationLog modlog;
    if (object instanceof AbstractPersistentObject<?,?> po) {
      modlog = super.createModificationLog(po.getPdo(), modType);
      modlog.setObjectClassId(po.getClassId());
      long rootId = po.getRootId();
      if (rootId != 0) {
        // component
        modlog.setRootObjectId(rootId);
        modlog.setRootObjectClassId(po.getRootClassId());
      }
    }
    else {
      modlog = super.createModificationLog(object, modType);
    }
    return modlog;
  }

}
