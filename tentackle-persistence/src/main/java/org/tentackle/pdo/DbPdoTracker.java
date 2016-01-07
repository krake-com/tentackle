/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.pdo;

import org.tentackle.common.Service;
import org.tentackle.persist.ModificationTracker;

/**
 * The pdo tracker for the tentackle persistence layer.
 *
 * @author harald
 */
@Service(PdoTracker.class)
public class DbPdoTracker extends ModificationTracker implements PdoTracker {

  /**
   * Creates the Pdo tracker.
   * <p>
   * Notice that the tracker is a singleton.
   */
  public DbPdoTracker() {
    super("PDO Modification Tracker");
    setSessionClosedOnTermination(true);
  }


  @Override
  public void addModificationListener(ModificationListener listener) {
    super.addModificationListener(listener);
  }

  @Override
  public boolean removeModificationListener(ModificationListener listener) {
    return super.removeModificationListener(listener);
  }

  @Override
  public <T extends PersistentDomainObject<T>> long getSerial(Class<T> clazz) {
    return getSerial(PdoUtilities.getInstance().getTableName(clazz));
  }

  @Override
  public long getSerial(String name) {
    return getIdSerialForName(name).getSerial();
  }


}
