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

package org.tentackle.persist.trip;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PdoCacheOperation;
import org.tentackle.pdo.PdoCachePreloadContent;
import org.tentackle.persist.PdoCacheOperationPersistenceImpl;

import java.util.Collection;

/**
 * Remote delegate interface for {@link PdoCacheOperationPersistenceImpl}.
 *
 * @author  harald
 */
public interface PdoCacheOperationRemoteDelegate
       extends AbstractPersistentOperationRemoteDelegate<PdoCacheOperation, PdoCacheOperationPersistenceImpl> {

  /**
   * Gets the content of all caches to preload.<br>
   * The content includes the modification serials of the tables, which are needed
   * to keep the client's modification tracker in sync with the cache data.
   *
   * @param context the domain context, never null
   * @return the content of all caches to preload, never null
   * @see org.tentackle.persist.PreloadCache
   */
  Collection<PdoCachePreloadContent<?>> getPreloadingCachesContent(DomainContext context);

}
