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

package org.tentackle.pdo;

import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripReferencable;

import java.util.List;

/**
 * DTO holding the cache content for a PDO-type.<br>
 * Used when preloading a cache from a remote server.
 *
 * @param pdoClass the PDO interface
 * @param pdoList the cache content
 * @param tableInfo the table information
 * @param <T> the PDO type
 */
@TripDeduplicatable(TripDeduplicatable.NEVER)
@TripReferencable(TripReferencable.NEVER)
public record PdoCachePreloadContent<T extends PersistentDomainObject<T>>
                (Class<T> pdoClass, List<T> pdoList, PdoCachePreloadTableInfo<T>[] tableInfo) {
}
