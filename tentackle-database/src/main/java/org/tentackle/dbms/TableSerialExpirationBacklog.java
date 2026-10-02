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

import org.tentackle.log.Logger;
import org.tentackle.misc.IdSerialTuple;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Keeps a backlog of expiration sets.
 * <p>
 * An expiration set holds the id/tableserial tuples of the objects modified within a range of
 * tableserials. The sets are kept round-robin, so that caches asking for the same range repeatedly
 * do not have to query the database. If a range is requested that is older than the oldest set,
 * the backlog is enlarged (up to a maximum size) and the caller must query the database.
 * <p>
 * In the server connected to the database, the sets are merged with the {@link TableSerialHistory}
 * to fill the gaps caused by deletions, rollbacks, and multiple updates.
 *
 * @see ModificationTally
 * @author harald
 */
public class TableSerialExpirationBacklog {

  private static final Logger LOGGER = Logger.get(TableSerialExpirationBacklog.class);

  private static final int DEFAULT_SIZE = 256;  // should be enough for the 99% case (master data)
  private static final int MAX_SIZE = 4096;     // max. size (auto enlarge)


  /**
   * Holds a list of expired id/tableserial tuples.
   */
  private static class ExpirationList {

    private final long minSerial;                       // > minSerial
    private final long maxSerial;                       // <= maxSerial
    private final Map<Long, IdSerialTuple> tupleMap;    // abs(tableserial):id/tableserial

    private ExpirationList(long minSerial, long maxSerial, Collection<IdSerialTuple> tuples) {
      this.minSerial = minSerial;
      this.maxSerial = maxSerial;
      tupleMap = new TreeMap<>();
      addMissing(tuples);
    }

    private void addMissing(Collection<IdSerialTuple> tuples) {
      if (tuples != null) {
        // add only those tableserial not already in the map
        tuples.forEach(tuple -> tupleMap.putIfAbsent(Math.abs(tuple.getSerial()), tuple));
      }
    }

    private Collection<IdSerialTuple> getTuples() {
      return tupleMap.values();
    }
  }


  // the backlog
  private final String name;                      // the name of the backlog
  private ExpirationList[] expirations;           // array of expirations
  private int nextNdx;                            // round-robin index
  private long minSerial;                         // min serial of all expiration sets
  private long maxSerial;                         // max serial of all expiration sets

  // synchronization lock
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * Creates a backlog.
   *
   * @param name the name of the backlog
   * @param size the size of the backlog
   */
  public TableSerialExpirationBacklog(String name, int size) {
    this.name = name;
    expirations = new ExpirationList[size];      // should be enough for the 99% case
  }

  /**
   * Creates a backlog with a default size.
   *
   * @param name the name of the backlog
   */
  public TableSerialExpirationBacklog(String name) {
    this(name, DEFAULT_SIZE);
  }

  @Override
  public String toString() {
    return name + "[" + minSerial + "-" + maxSerial + "]";
  }

  /**
   * Adds an expiration set to the backlog.
   *
   * @param minSerial the lower serial bound of the query (minSerial &lt; tableSerial)
   * @param maxSerial the upper serial bound of the query (tableSerial &le; maxSerial)
   * @param tuples the expiration info as pairs of id/tableserial.
   * @param mergeHistory true to merge the {@link TableSerialHistory}
   */
  public void addExpiration(long minSerial, long maxSerial, Collection<IdSerialTuple> tuples, boolean mergeHistory) {
    if (tuples != null) {
      if (minSerial >= maxSerial) {
        LOGGER.warning("illegal tableserial range [{0}-{1}] refused to add to expiration backlog {2}", minSerial, maxSerial, this);
      }
      else {
        LOGGER.fine("adding expiration set {0}-{1}[{2}] for {3}",
                    minSerial, maxSerial, tuples.size(), name);

        lock.writeLock().lock();
        try {
          // Min- and maxSerial ranges of expirations may overlap, i.e. [3-8] followed by [5-10] is perfectly ok,
          // since we could have rollbacks (in this case from 5-8).
          // However, the maxSerials must not be decreasing!
          if (maxSerial < this.maxSerial) {
            LOGGER.severe("expiration range [{0}-{1}] added unexpectedly to expiration backlog {2} -> ignored",
                          minSerial, maxSerial, this);
          }
          else {
            // add expiration set round-robin
            ExpirationList expiration = new ExpirationList(minSerial, maxSerial, tuples);
            if (mergeHistory) {
              // only for the local server
              expiration.addMissing(TableSerialHistory.getInstance().retrieve(name, minSerial, maxSerial, true));
            }

            if (!expiration.tupleMap.isEmpty()) {

              expirations[nextNdx++] = expiration;
              if (nextNdx >= expirations.length) {
                nextNdx = 0;
              }

              // update minSerial from the next oldest or from the added set, if the first at all
              expiration = expirations[nextNdx];
              if (expiration != null) {
                this.minSerial = expiration.minSerial;
              }
              else if (nextNdx == 1) {
                // this was the first added to the array of expirations
                this.minSerial = minSerial;
              }

              // update maxSerial always from added set
              if (maxSerial > this.maxSerial) {
                this.maxSerial = maxSerial;
              }
            }
          }
        }
        finally {
          lock.writeLock().unlock();
        }
      }
    }
  }


  /**
   * Gets the expiration backlog for a given range of table-serials.<br>
   * An id of 0 indicates a rolled back insert or update.<br>
   * A negative tableserial indicates a deletion.
   *
   * @param minSerial the lower serial bound of the query (minSerial &lt; tableSerial)
   * @param maxSerial the upper serial bound of the query (tableSerial &le; maxSerial)
   * @return the expiration set as tuples of id/tableserial, ordered by abs(tableserial), null if the given range was not found in the backlog
   */
  public TreeSet<IdSerialTuple> getExpiration(long minSerial, long maxSerial) {

    // sort by abs(tableserial)
    TreeSet<IdSerialTuple> expireSet = new TreeSet<>(Comparator.comparingLong(tuple -> Math.abs(tuple.getSerial())));
    boolean found = false;

    lock.readLock().lock();
    try {
      if (minSerial < this.minSerial) {
        LOGGER.warning("expiration backlog {0} queried for outdated range [{1}-{2}]", this, minSerial, maxSerial);
        int size = expirations.length;
        lock.readLock().unlock();
        lock.writeLock().lock();
        try {
          if (size == expirations.length) {   // not enlarged by another thread while upgrading to write-lock
            // enlarge, if possible
            int newSize = enlargeSize(size);
            if (newSize > size) {
              ExpirationList[] newExpirations = new ExpirationList[newSize];
              System.arraycopy(expirations, 0, newExpirations, 0, size);
              expirations = newExpirations;
              LOGGER.info("expiration backlog {0} enlarged to {1} entries", this, newSize);
            }
          }
          lock.readLock().lock();
        }
        finally {
          lock.writeLock().unlock();
        }
        return null;    // ask the database!
      }

      // we start at current nextNdx - 1: that's the newest entry
      // and go back until maxSerial < minSerial
      int ndx = nextNdx;
      int count = 0;    // number of slots to check
      while (count < expirations.length) {
        if (--ndx < 0) {
          ndx = expirations.length - 1;
        }
        count++;
        ExpirationList expiration = expirations[ndx];
        if (expiration == null || expiration.maxSerial < minSerial) {
          break;
        }
      }
      // go forward.
      // the direction is important since tableserials may overlap due to rollback!
      while (count-- > 0) {
        if (ndx >= expirations.length) {
          ndx = 0;
        }
        ExpirationList expiration = expirations[ndx++];
        if (expiration != null && expiration.maxSerial >= minSerial && expiration.minSerial <= maxSerial) {
          found = true;   // at least one matching set found
          for (IdSerialTuple tuple : expiration.getTuples()) {
            long serial = Math.abs(tuple.getSerial());
            if (serial > minSerial && serial <= maxSerial) {
              expireSet.add(tuple);
            }
          }
        }
      }
    }
    finally {
      lock.readLock().unlock();
    }

    return found ? expireSet : null;
  }


  /**
   * Computes the enlarged size.<br>
   * Invoked when there is an outdated query.
   *
   * @param size the current size
   * @return the enlarged size, same if no more enlargement possible
   */
  protected int enlargeSize(int size) {
    size <<= 1;
    if (size > MAX_SIZE) {
      size = MAX_SIZE;
    }
    return size;
  }

}
