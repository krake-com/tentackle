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

/**
 * Eviction strategy for the PDO-cache.
 *
 * @author harald
 */
public enum PdoCacheStrategy {

  /**
   * Preload all PDOs.<br>
   * This is the default and works for the majority of master-data entities.<br>
   * The first select from the cache will load all entities from the database
   * so that further selects will not need a physical load from the database.
   */
  PRELOAD,

  /**
   * If a PDO is not in the cache, it will be loaded from the database.<br>
   * When the maximum size of the cache is exceeded, the cache is purged completely.
   */
  FORGET,

  /**
   * If a PDO is not in the cache, it will be loaded from the database.<br>
   * When the maximum size of the cache is exceeded, the least recently used PDOs
   * are removed from the cache, according to a keep-quota.
   */
  LRU,

  /**
   * If a PDO is not in the cache, it will be loaded from the database.<br>
   * When the maximum size of the cache is exceeded, the least frequently used PDOs
   * are removed from the cache, according to a keep-quota.
   */
  LFU

}
