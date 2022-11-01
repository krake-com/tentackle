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

package org.tentackle.ns;

import org.tentackle.common.Service;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;

import java.util.HashMap;
import java.util.Map;

/**
 * The default number source factory based on {@link DefaultNumberSource}.
 *
 * @author harald
 */
@Service(NumberSourceFactory.class)
public class DefaultNumberSourceFactory implements NumberSourceFactory {

  /**
   * Holds the number sources mapped by their unique name key.
   */
  private final Map<String,DefaultNumberSource> nsMap = new HashMap<>();

  /**
   * Creates the number source factory.
   */
  public DefaultNumberSourceFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public synchronized NumberSource getNumberSource(String pool, String realm) {
    StringBuilder buf = new StringBuilder();
    buf.append(pool);
    if (realm != null) {
      buf.append(':').append(realm);
    }
    String key = buf.toString();
    DefaultNumberSource numberSource = nsMap.get(key);
    if (numberSource == null) {
      // not in map: create a new one
      DomainContext ctx = Pdo.createDomainContext();
      NumberPool numberPool = Pdo.create(NumberPool.class, ctx).selectByNameRealm(pool, realm);
      if (numberPool != null) {
        numberSource = new DefaultNumberSource(numberPool);
        nsMap.put(key, numberSource);
      }
      else {
        throw new NumberSourceException("no such pool: " + key);
      }
    }
    return numberSource;
  }

}
