/*
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

package org.tentackle.ns;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

/**
 * The default implementation of a number source.<br>
 * Based on {@link NumberPool} and {@link NumberRange}.<br>
 * This number source does not implement any logic to access an uplink source,
 * and thus needs to be extended if an uplink is required.
 * <p>
 * Notice that the DefaultNumberSource is not remoting capable.
 *
 * @author harald
 */
public class DefaultNumberSource implements NumberSource {

  private NumberPool pool;          // the pool backing up this number source
  private NumberRange currentRange; // current range in use, null if none


  /**
   * Creates a number source from a given pool.
   *
   * @param pool the number pool
   */
  public DefaultNumberSource(NumberPool pool) {
    if (pool.getSession().isRemote()) {
      throw new PersistenceException(pool, "number sources cannot be remote");
    }
    this.pool = pool;
  }

  @Override
  public String toString() {
    return pool.toString();
  }

  @Override
  public synchronized Range[] getRanges() {
    return Session.getSession().transaction(() -> {
      lockPool();
      Range[] ranges = new Range[pool.getNumberRangeList().size()];
      int i = 0;
      for (NumberRange numberRange: pool.getNumberRangeList()) {
        ranges[i++] = new Range(numberRange.getBegin(), numberRange.getEnd());
      }
      return ranges;
    });
  }

  @Override
  public synchronized long getCount() {
    return Session.getSession().transaction(() -> {
      lockPool();
      long count = 0;
      for (NumberRange numberRange: pool.getNumberRangeList()) {
        count += numberRange.size();
      }
      return count;
    });
  }

  @Override
  public boolean isEmpty() {
    return getCount() == 0;
  }

  @Override
  public synchronized boolean isOnline() {
    return Session.getSession().transaction(() -> {
      lockPool();
      return pool.isOnline();
    });
  }

  @Override
  public synchronized long popNumber() {
    /**
     * As this is by far the most often used method, it is
     * implemented to be as fast as possible.
     * The happy path is:
     * - pool hasn't changed (name, whatever)
     *    -> exclusive lock on pool
     * - current range hasn't changed (since the last getNumber())
     *    -> pop a number and persist pool (i.e. that range)
     */
    return Session.getSession().transaction(() -> {
      lockPool();
      findCurrentRange();
      long number = currentRange.popNumber();
      // this will only update the ranges (if no range removed).
      // see NumberPoolPI.isUpdatingSerialEvenIfNotModified
      pool = pool.persist();
      return number;
    });
  }

  @Override
  public Range[] popNumbers(final long count) {
    return Session.getSession().transaction(() -> {
      lockPool();
      findCurrentRange();
      Set<Range> ranges = new TreeSet<>(    // returned ranges sorted by begin
        (Range o1, Range o2) -> Long.compare(o1.getBegin(), o2.getBegin()));

      long remains = count;
      while (remains > 0) {
        Range r = currentRange.popNumbers(remains);
        ranges.add(r);
        remains -= r.size();
        try {
          currentRange = pool.getCurrentRange();
        }
        catch (NumberSourceEmptyException nx) {
          break;  // empty
        }
      }
      pool = pool.persist();
      return ranges.toArray(new Range[ranges.size()]);
    });
  }

  @Override
  public synchronized Range[] popNumbers(long begin, long end) {
    return Session.getSession().transaction(() -> {
      lockPool();
      Set<Range> ranges = new TreeSet<>(    // returned ranges sorted by begin
        (Range o1, Range o2) -> Long.compare(o1.getBegin(), o2.getBegin()));

      List<NumberRange> splitRanges = new ArrayList<>();  // splitted ranges (to add)
      for (Iterator<NumberRange> iter =  pool.getNumberRangeList().iterator(); iter.hasNext(); ) {
        NumberRange range = iter.next();
        if (range.intersects(begin, end)) {
          if (begin <= range.getBegin()) {
            if (end >= range.getEnd()) {
              // consume the whole range
              ranges.add(new Range(range.getBegin(), range.getEnd()));
              iter.remove();
            }
            else  {
              // consume lower part of range
              ranges.add(new Range(range.getBegin(), end));
              range.setBegin(end);
            }
          }
          else {
            if (end >= range.getEnd()) {
              // consume higher part of range
              ranges.add(new Range(begin, range.getEnd()));
              range.setEnd(begin);
            }
            else  {
              // consume middle part of range
              ranges.add(new Range(begin, end));
              NumberRange splitRange = Pdo.create(range);
              splitRange.setBegin(end);
              splitRange.setEnd(range.getEnd());
              splitRanges.add(splitRange);
              range.setEnd(begin);    // cut range
            }
          }
        }
      }
      // add splitted ranges to pool
      pool.getNumberRangeList().addAll(splitRanges);
      pool = pool.persist();
      return ranges.toArray(new Range[ranges.size()]);
    });
  }

  @Override
  public synchronized void pushNumber(long number) {
    Session.getSession().transaction(() -> {
      lockPool();
      boolean merged = false;
      for (NumberRange range: pool.getNumberRangeList()) {
        if (range.intersects(number, number + 1)) {
          // number already part of range -> nothing to do
          return null;
        }
        if (range.getBegin() == number + 1) {
          range.setBegin(number);
          merged = true;
          break;
        }
        else if (range.getEnd() == number) {
          range.setEnd(number);
          merged = true;
          break;
        }
      }
      if (!merged) {
        // need a new range
        NumberRange range = Pdo.create(NumberRange.class, pool.getDomainContext());
        range.setBegin(number);
        range.setEnd(number + 1);
        pool.getNumberRangeList().add(range);
      }
      fixRanges();
      pool = pool.persist();
      return null;
    });
  }

  @Override
  public synchronized void pushNumbers(long begin, long end) {
    Session.getSession().transaction(() -> {
      lockPool();
      boolean merged = false;
      for (NumberRange range: pool.getNumberRangeList()) {
        if (range.intersects(begin, end)) {
          if (range.getEnd() < end) {
            range.setEnd(end);
            merged = true;
          }
          if (range.getBegin() > begin) {
            range.setBegin(begin);
            merged = true;
          }
          if (!merged) {
            // range to add is fully contained within this range -> nothing to do
            return null;
          }
          break;
        }
        if (range.getBegin() == end) {
          range.setBegin(begin);
          merged = true;
          break;
        }
        else if (range.getEnd() == begin) {
          range.setEnd(end);
          merged = true;
          break;
        }
      }
      if (!merged) {
        // need a new range
        NumberRange range = Pdo.create(NumberRange.class, pool.getDomainContext());
        range.setBegin(begin);
        range.setEnd(end);
        pool.getNumberRangeList().add(range);
      }
      fixRanges();
      pool = pool.persist();
      return null;
    });
  }


  /**
   * Locks the pool.
   */
  private void lockPool() {
    NumberPool lockedPool = pool.reloadLocked();
    if (lockedPool == null) {
      throw new NumberSourceException("pool " + pool + " vanished");
    }
    if (lockedPool.getSerial() != pool.getSerial()) {
      pool = lockedPool;
      currentRange = null;
    }
    if (!pool.isOnline()) {
      throw new NumberSourceException("pool " + pool + " is offline");
    }
  }

  /**
   * Finds the current range.
   */
  private void findCurrentRange() {
    if (currentRange != null) {
      NumberRange range = currentRange.reload();
      if (range == null ||
          range.getSerial() != currentRange.getSerial() ||
          currentRange.isEmpty()) {
        // Range vanished or changed via another server/client or some transaction rolled back
        // -> invalidate all cached ranges
        currentRange = null;
        pool.reloadRanges();
      }
    }

    if (currentRange == null) {
      currentRange = pool.getCurrentRange();
    }
  }


  /**
   * Combines overlapping ranges.<br>
   * Necessary to guarantee uniqueness of the numbers in this pool.
   */
  private void fixRanges() {
    // ranges sorted by begin
    Map<Range, NumberRange> rangeMap = new TreeMap<>(
        (Range o1, Range o2) -> Long.compare(o1.getBegin(), o2.getBegin()));
    for (NumberRange range: pool.getNumberRangeList()) {
      rangeMap.put(new Range(range.getBegin(), range.getEnd()), range);
    }

    NumberRange[] ranges = new NumberRange[rangeMap.size()];
    rangeMap.values().toArray(ranges);

    for (int i=0; i < ranges.length; i++) {
      NumberRange range = ranges[i];
      if (range != null) {  // if not already deleted
        // check all following ranges for overlaps
        for (int j=i+1; j < ranges.length; j++) {
          NumberRange nextRange = ranges[j];
          if (nextRange != null) {
            if (range.getEnd() >= nextRange.getEnd()) {
              // nextRange is covered by range -> delete it
              pool.getNumberRangeList().remove(nextRange);
              ranges[j] = null;
            }
            else if (range.getEnd() > nextRange.getBegin()) {
              // merge
              range.setEnd(nextRange.getEnd());
              pool.getNumberRangeList().remove(nextRange);
              ranges[j] = null;
            }
            else  {
              break;  // others are greater -> don't check
            }
          }
        }
      }
    }
  }

}
