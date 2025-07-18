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

import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.misc.IdSerialTuple;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;


interface TableSerialHistoryHolder {
  TableSerialHistory INSTANCE = ServiceFactory.createService(TableSerialHistory.class, TableSerialHistory.class);
}


/**
 * Keeps track of committed deletions and committed or rolled back updates or inserts in memory.<br>
 * When loading the tableserials from the database via {@link AbstractDbObject#selectExpiredTableSerials(long, long)},
 * gaps will be present for deletions, rolled back updates or inserts and PDOs updated more than once
 * since the last query.<br>
 * This singleton maintains those modifications in memory as complementary information.<br>
 * {@link IdSerialTuple}s with a positive tableserial and an ID of 0 are for inserts or updates.
 * A negative tableserial with a positive ID describes a deletion.<br>
 * The history is only maintained by the server connected to the database and used by the
 * {@link TableSerialExpirationBacklog} to fill the gaps in its expiration sets.<br>
 * Consulting the expiration backlog usually happens when the {@link org.tentackle.session.ModificationTracker}
 * detects a modification and invokes the corresponding listeners, such as PDO-caches, which in turn
 * ask the expiration backlog for the modifications.<br>
 * If, for some reason, the modifications are counted for an entity type, but the expiration backlog is never
 * consulted (no PdoCache?), the deletions will be removed silently after a certain amount of time.
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


  /**
   * The modification tracker of the local server usually polls the modification table once a second.
   * This timeout should be more than enough even for heavy load scenarios.
   */
  private static final long DEFAULT_TIMEOUT = Constants.MINUTE_MS;


  /**
   * A tableserial history entry.
   */
  private static class History extends IdSerialTuple {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long epochal;   // necessary for purging old gaps only

    /**
     * Creates a history entry.
     *
     * @param id the object ID, 0 for insert/update rollback
     * @param tableSerial the modification tableserial, negative for delete
     * @param epochal the epochal
     */
    private History(long id, long tableSerial, long epochal) {
      super(id, tableSerial);
      if (tableSerial == 0 ||
          tableSerial < 0 && id == 0) {
        throw new IllegalArgumentException("invalid gap id=" + id + " tableSerial=" + tableSerial);
      }
      this.epochal = epochal;
    }
  }


  private final long timeout;                                   // the timeout in ms
  private final Map<String, TreeMap<Long, History>> history;    // [tablename:[abs(tableserial):gap]]
  private long lastCleanup;                                     // epochal of last cleanup run


  /**
   * Creates the history singleton.
   *
   * @param timeout the timeout in ms
   */
  public TableSerialHistory(long timeout) {
    this.timeout = timeout;
    history = new HashMap<>();
    lastCleanup = System.currentTimeMillis();
  }

  /**
   * Creates the history singleton with a default timeout.
   */
  public TableSerialHistory() {
    this(DEFAULT_TIMEOUT);
  }


  /**
   * Memorizes a history entry.<br>
   *
   * @param tableName the tablename
   * @param id the object's ID, 0 for an update or insert
   * @param tableSerial the object's tableserial, negative if deletion
   */
  public synchronized void memorize(String tableName, long id, long tableSerial) {
    long epochal = System.currentTimeMillis();
    history.computeIfAbsent(tableName, _ -> new TreeMap<>())
           .put(Math.abs(tableSerial), new History(id, tableSerial, epochal));
    purge(epochal);
  }

  /**
   * Memorizes a history entry.<br>
   *
   * @param tableName the tablename
   * @param tuple the id/tableserial tuple
   */
  public void memorize(String tableName, IdSerialTuple tuple) {
    memorize(tableName, tuple.getId(), tuple.getSerial());
  }

  /**
   * Memorizes history entries described by id/tableserial tuples.
   *
   * @param tableName the tablename
   * @param tuples the gaps
   */
  public synchronized void memorize(String tableName, Collection<IdSerialTuple> tuples) {
    long epochal = System.currentTimeMillis();
    TreeMap<Long, History> map = history.computeIfAbsent(tableName, _ -> new TreeMap<>());
    tuples.forEach(tuple ->
                     map.put(Math.abs(tuple.getSerial()),
                             new History(tuple.getId(), tuple.getSerial(), epochal)));
    purge(epochal);
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
  public synchronized Collection<IdSerialTuple> retrieve(String tableName, long minSerial, long maxSerial, boolean forget) {
    TreeMap<Long, History> gaps = history.get(tableName);
    if (gaps != null) {
      Collection<IdSerialTuple> tuples = new ArrayList<>();
      Iterator<Map.Entry<Long, History>> iter = gaps.subMap(minSerial, false, maxSerial, true)
                                                    .entrySet().iterator();
      while (iter.hasNext()) {
        tuples.add(iter.next().getValue());
        if (forget) {
          iter.remove();
        }
      }
      return tuples.isEmpty() ? null : tuples;
    }
    return null;
  }


  private void purge(long epochal) {
    if (epochal - lastCleanup >= timeout) {
      lastCleanup = epochal;
      long oldest = epochal - timeout;
      history.values().forEach(
        m -> m.entrySet().removeIf(e -> e.getValue().epochal < oldest));
    }
  }

}
