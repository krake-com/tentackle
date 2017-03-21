/**
 * Tentackle - http://www.tentackle.org
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.IdSerialTuple;


/**
 * Keeps a backlog of expiration sets.<br>
 * Used in RMI-servers to reduce database roundtrips for clients
 * requesting the expiration info for their local caches.
 *
 * @author harald
 */
public class TableSerialExpirationBacklog {


  /**
   * Default size of expiration table.
   */
  public static int defaultSize = 64;          // should be enough for the 99% case



  // holds a set of expiration info
  private static class ExpirationSet implements Cloneable {

    private long minSerial;                         // > minSerial
    private long maxSerial;                         // <= maxSerial
    private final List<IdSerialTuple> idSerTuples;  // pairs of id/tableserial

    private ExpirationSet(long minSerial, long maxSerial, List<IdSerialTuple> idSerTuples) {
      this.minSerial    = minSerial;
      this.maxSerial    = maxSerial;
      this.idSerTuples  = idSerTuples;
    }

    @Override
    public ExpirationSet clone() {
      try {
        return (ExpirationSet) super.clone();
      }
      catch (CloneNotSupportedException ex) {
        throw new TentackleRuntimeException("clone failed", ex);
      }
    }
  }



  // the backlog
  private final ExpirationSet[] expirations;      // array of expirations
  private int nextNdx;                            // round robin index

  // synchronization lock
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();



  /**
   * Creates a backlog.
   *
   * @param size the size of the backlog
   */
  public TableSerialExpirationBacklog(int size) {
    expirations = new ExpirationSet[size];      // should be enough for the 99% case
    for (int i=0; i < size; i++) {
      expirations[i] = null;    // null = unused yet
    }
  }

  /**
   * Creates a backlog with a default size.
   */
  public TableSerialExpirationBacklog() {
    this(defaultSize);
  }


  /**
   * Adds an expiration set to the backlog.
   *
   * @param minSerial the lower serial bound of the query (minSerial &lt; tableSerial)
   * @param maxSerial the upper serial bound of the query (tableSerial &le; maxSerial)
   * @param idSerTuples the expiration info as pairs of id/tableserial.
   */
  public void addExpiration(long minSerial, long maxSerial, List<IdSerialTuple> idSerTuples) {
    if (idSerTuples != null && !idSerTuples.isEmpty()) {
      lock.writeLock().lock();
      try {
        expirations[nextNdx++] = new ExpirationSet(minSerial, maxSerial, idSerTuples);
        if (nextNdx >= expirations.length) {
          nextNdx = 0;
        }
      }
      finally {
        lock.writeLock().unlock();
      }
    }
  }


  /**
   * Gets the expiration backlog for a given range of tableserials.
   *
   * @param minSerial the lower serial bound of the query (minSerial &lt; tableSerial)
   * @param maxSerial the upper serial bound of the query (tableSerial &le; maxSerial)
   * @return the expiration info as pairs of id/tableserial, null if given range was not found in the backlog
   */
  public List<IdSerialTuple> getExpiration(long minSerial, long maxSerial) {

    // extract matching sets
    ExpirationSet[] matchedExps = new ExpirationSet[expirations.length];
    int matchCount = 0;
    lock.readLock().lock();
    try {
      // we start at current nextNdx: this is the oldest entry
      int ndx = nextNdx;
      for (int i=0; i < expirations.length; i++) {
        if (ndx >= expirations.length) {
          ndx = 0;
        }
        ExpirationSet exp = expirations[ndx++];
        if (exp != null) {
          if (exp.maxSerial >= minSerial && exp.minSerial <= maxSerial) {
            // add to list of matched sets
            matchedExps[matchCount++] = exp;
          }
        }
      }
    }
    finally {
      lock.readLock().unlock();
    }

    while (matchCount > 0) {
      // merge the sets. note that a merge creates a clone, so there's no need for synchronization
      matchedExps = merge(matchedExps, matchCount);
      if (matchedExps.length == matchCount) {
        // cannot be merged any further
        for (int m=0; m < matchCount; m++) {
          ExpirationSet exp = matchedExps[m];
          if (exp.minSerial <= minSerial && exp.maxSerial >= maxSerial) {

            // create the result of unique id/tableserial pairs, sorted by id + tableserial
            Set<IdSerialTuple> setById = new TreeSet<>((IdSerialTuple o1, IdSerialTuple o2) -> {
              // order by id + tableserial descending
              int rv = Long.compare(o2.getId(), o1.getId());
              if (rv == 0) {
                rv = Long.compare(o2.getSerial(), o1.getSerial());
              }
              return rv;
            });

            for (IdSerialTuple idSer: exp.idSerTuples) {
              if (idSer.getSerial() > minSerial && idSer.getSerial() <= maxSerial) {
                setById.add(idSer);
              }
            }

            // only take the id/tableserial with the highest tableserial for duplicate ids
            List<IdSerialTuple> expireList = new ArrayList<>();

            IdSerialTuple lastIdSer = null;
            for (IdSerialTuple idSer: setById) {
              if (lastIdSer == null || lastIdSer.getId() == idSer.getId()) {
                lastIdSer = idSer;
              }
              else  {
                expireList.add(lastIdSer);
                lastIdSer = idSer;
              }
            }
            if (lastIdSer != null) {
              expireList.add(lastIdSer);
            }

            return expireList;
          }
        }
        // no usable range found
        break;
      }

      matchCount = matchedExps.length;
      // continue merge...
    }

    return null;    // no such range in backlog: ask the database!
  }



  /**
   * Merge expiration sets.
   *
   * @param expSets the expiration sets to merge
   * @return the merged sets
   */
  private ExpirationSet[] merge(ExpirationSet[] expSets, int count) {
    ExpirationSet[] mergedSets = new ExpirationSet[count];
    mergedSets[0] = expSets[0].clone();
    int mergeCount = 1;
    for (int i=1; i < count; i++) {
      ExpirationSet exp = expSets[i];
      boolean merged = false;
      for (int j=0; j < mergeCount; j++) {
        ExpirationSet mexp = mergedSets[j];
        if (exp.maxSerial >= mexp.minSerial && exp.minSerial <= mexp.minSerial) {
          mexp.minSerial = exp.minSerial;
          if (exp.maxSerial > mexp.maxSerial) {
            mexp.maxSerial = exp.maxSerial;
          }
          merged = true;
        }
        if (exp.minSerial <= mexp.maxSerial && exp.maxSerial >= mexp.maxSerial) {
          mexp.maxSerial = exp.maxSerial;
          if (exp.minSerial < mexp.minSerial) {
            mexp.minSerial = exp.minSerial;
          }
          merged = true;
        }
        if (merged) {
          mexp.idSerTuples.addAll(exp.idSerTuples);
          break;
        }
      }
      if (!merged) {
        // not merged: add a clone to merged sets
        mergedSets[mergeCount++] = exp.clone();
      }
    }
    // cut result array in size
    ExpirationSet[] nMergedSets = new ExpirationSet[mergeCount];
    System.arraycopy(mergedSets, 0, nMergedSets, 0, mergeCount);
    return nMergedSets;
  }

}
