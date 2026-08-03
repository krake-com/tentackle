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
import org.tentackle.common.ServiceFactory;
import org.tentackle.misc.IdSerialTuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;


interface TableSerialHistoryHolder {
  TableSerialHistory INSTANCE = ServiceFactory.createService(TableSerialHistory.class, TableSerialHistory.class);
}


/**
 * Keeps track of all committed or rolled back deletions, updates, or inserts in memory.<br>
 * Applies to entities with a tableserial.<br>
 * When loading the tableserials from the database via {@link AbstractDbObject#selectExpiredTableSerials(long, long)},
 * gaps will be present for deletions, rolled back updates, or inserts and PDOs updated more than once
 * since the last query.
 * Those {@code id/tableserial} tuples are picked up by the {@link TableSerialExpirationBacklog} to fill
 * the gaps in its expiration sets avoiding unnecessary cache invalidations.<br>
 * An {@link IdSerialTuple}s with a positive tableserial and an ID of 0 represents an insert or update.
 * A negative tableserial with a positive ID describes a deletion.
 * The history is only maintained by the server connected to the database. Intermediate servers or clients
 * retrieve their expiration sets from the remote server.<br>
 * Consulting the expiration backlog usually happens when the {@link org.tentackle.session.ModificationTracker}
 * detects a modification and invokes the corresponding listeners, such as PDO-caches, which in turn
 * ask the expiration backlog for the modifications.<br>
 * If, for some reason, the modifications are counted for an entity type but the expiration backlog is never
 * consulted (no PdoCache?), the oldest history entries will be silently removed if the maximum size is reached.
 * This may be ok if the entity is updated frequently but not queried often via the cache,
 * for example. Otherwise, the maximum size of the history should be increased for the given entity type (tablename).
 *
 * @see ModificationTally
 */
@Service(TableSerialHistory.class)   // defaults to self
public class TableSerialHistory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static TableSerialHistory getInstance() {
    return TableSerialHistoryHolder.INSTANCE;
  }

  private static final int DEFAULT_MAX_SIZE = 1024;     // should be enough for the 99% case (master data)


  private static class History {

    private final TreeMap<Long, IdSerialTuple> history;   // abs(tableserial):tuple
    private int maxSize;

    private History(int maxSize) {
      this.maxSize = maxSize;
      history = new TreeMap<>();
    }

    private synchronized void add(IdSerialTuple tuple) {
      long tableSerial = tuple.getSerial();
      long id = tuple.getId();
      if (tableSerial == 0 ||
          tableSerial < 0 && id == 0) {
        throw new IllegalArgumentException("invalid history entry: id=" + id + ", tableSerial=" + tableSerial);
      }

      history.put(Math.abs(tableSerial), tuple);

      while (history.size() > maxSize) {
        // could be more than 1 if maxSize was reduced
        history.remove(history.firstKey());
      }
    }

    private void add(long id, long tableSerial) {
      add(new IdSerialTuple(id, tableSerial));
    }

  }

  private final int maxSize;                        // max. number of history entries per tablename
  private final Map<String, History> tableMap;      // [tablename:history]


  /**
   * Creates the history singleton.
   *
   * @param maxSize the initial max. number of history entries per tablename
   * @see #setMaxSize(String, int)
   */
  public TableSerialHistory(int maxSize) {
    this.maxSize = maxSize;
    tableMap = new ConcurrentHashMap<>();
  }

  /**
   * Creates the history singleton with a default size.
   */
  public TableSerialHistory() {
    this(DEFAULT_MAX_SIZE);
  }


  /**
   * Memorizes a history entry.<br>
   *
   * @param tableName the tablename
   * @param id the object's ID, 0 for an update or insert
   * @param tableSerial the object's tableserial, negative if deletion
   */
  public void memorize(String tableName, long id, long tableSerial) {
    getHistory(tableName).add(id, tableSerial);
  }

  /**
   * Memorizes a history entry.<br>
   *
   * @param tableName the tablename
   * @param tuple the id/tableserial tuple
   */
  public void memorize(String tableName, IdSerialTuple tuple) {
    getHistory(tableName).add(tuple);
  }

  /**
   * Memorizes history entries described by id/tableserial tuples.
   *
   * @param tableName the tablename
   * @param tuples the gaps
   */
  public void memorize(String tableName, Collection<IdSerialTuple> tuples) {
    History history = getHistory(tableName);
    synchronized (history) {
      tuples.forEach(history::add);
    }
  }

  /**
   * Retrieves history entries and optionally removes them.
   *
   * @param tableName the tablename
   * @param minSerial the lower serial bound of the query (minSerial &lt; tableSerial)
   * @param maxSerial the upper serial bound of the query (tableSerial &le; maxSerial)
   * @param forget true to remove the returned entries from memory
   * @return the non-empty list of gaps sorted by abs(tableserial), null for none
   */
  public Collection<IdSerialTuple> retrieve(String tableName,
                                                         long minSerial, long maxSerial,
                                                         boolean forget) {
    History history = tableMap.get(tableName);
    if (history != null) {
      synchronized (history) {
        Collection<IdSerialTuple> tuples = new ArrayList<>();
        Iterator<Map.Entry<Long, IdSerialTuple>> iter =
          history.history.subMap(minSerial, false, maxSerial, true)
                         .entrySet().iterator();
        while (iter.hasNext()) {
          tuples.add(iter.next().getValue());
          if (forget) {
            iter.remove();
          }
        }
        return tuples.isEmpty() ? null : tuples;
      }
    }
    return null;
  }

  /**
   * Sets the maximum size of the history.
   *
   * @param tableName the tablename
   * @param maxSize the new size
   */
  public void setMaxSize(String tableName, int maxSize) {
    History history = tableMap.get(tableName);
    if (history != null) {
      synchronized (history) {
        history.maxSize = maxSize;
      }
    }
  }

  /**
   * Gets the maximum size of the history.
   *
   * @param tableName the tablename
   * @return the size
   */
  public int getNaxSize(String tableName) {
    History history = tableMap.get(tableName);
    if (history != null) {
      synchronized (history) {
        return history.maxSize;
      }
    }
    return 0;
  }

  /**
   * Gets the number of history entries currently available.
   *
   * @param tableName the tablename
   * @return the number of history entries
   */
  public synchronized int getSize(String tableName) {
    History history = tableMap.get(tableName);
    if (history != null) {
      synchronized (history) {
        return history.history.size();
      }
    }
    return 0;
  }


  private History getHistory(String tableName) {
    return tableMap.computeIfAbsent(tableName, _ -> new History(maxSize));
  }

}
