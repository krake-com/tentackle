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
import org.tentackle.misc.SerialNumbered;
import org.tentackle.session.SessionDependable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation of a modification log factory.
 *
 * @author harald
 */
@Service(ModificationLogFactory.class)
public class DefaultModificationLogFactory implements ModificationLogFactory {

  // holds the serialization-enabled flag for each backend identity
  private final ConcurrentMap<Integer, ModificationLogSerializer> serializationEnabled = new ConcurrentHashMap<>();

  /**
   * Creates the modification log factory.
   */
  public DefaultModificationLogFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setSerializer(int backendIdentity, ModificationLogSerializer serializer) {
    serializationEnabled.put(backendIdentity, serializer);
  }

  @Override
  public ModificationLogSerializer getSerializer(int backendIdentity) {
    return serializationEnabled.get(backendIdentity);
  }

  @Override
  public ModificationLog createModificationLog() {
    return new ModificationLog();
  }

  @Override
  public ModificationLog createModificationLog(Db db) {
    return new ModificationLog(db);
  }

  @Override
  public ModificationLog createModificationLog(Db db, ModificationType modType) {
    return new ModificationLog(db, modType);
  }

  @Override
  public <T extends SessionDependable & SerialNumbered> ModificationLog createModificationLog(T object, ModificationType modType) {
    Db db = (Db) object.getSession();
    ModificationLog modlog = new ModificationLog(db, modType);
    modlog.setObject(object);
    if (modType.isSerializable()) {
      db.assertNotRemote();
      ModificationLogSerializer serializer = getSerializer(db.getBackendIdentity());
      if (serializer != null) {
        synchronized (serializer) {
          modlog.reserveId();   // the order of IDs and serialization must be in sync!
          modlog.setObjectData(serializer.serialize(object));
        }
      }
    }
    return modlog;
  }

  @Override
  public ModificationLog createModificationLog(ModificationLog template, ModificationType modType) {
    return new ModificationLog(template, modType);
  }

}
