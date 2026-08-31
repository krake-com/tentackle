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
 * A root entity carrying the token-lock columns.
 * <p>
 * {@link DemoEntity} deliberately provides no token lock (the tests assert that the token-lock SQL
 * is refused without the columns) and {@link DemoComponent} has the columns but is a component,
 * whereas the token-lock operations of {@link AbstractPersistentObject} require a root entity.
 * Hence this third entity.
 */
@TableName("demolockable")
@ClassId(9005)
public interface DemoLockable extends PersistentDomainObject<DemoLockable>, DemoLockableDomain, DemoLockablePersistence {

}
