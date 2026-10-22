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

import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.ClassId;
import org.tentackle.session.TableName;

/**
 * A root entity with a tableserial, as used by the cache tests.
 * <p>
 * The tests feed the {@link org.tentackle.pdo.PdoCache} from memory, so there is no table behind it:
 * only the tableserial matters, since it switches the cache from invalidation to surgical expiration.
 */
@TableName("democached")
@ClassId(9006)
public interface DemoCached extends PersistentDomainObject<DemoCached>, DemoCachedDomain, DemoCachedPersistence {

}
