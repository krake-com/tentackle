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


package org.tentackle.domain.ns;

import org.tentackle.domain.AbstractDomainObject;
import org.tentackle.ns.NumberSourceEmptyException;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberPoolDomain;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.DomainObjectService;

import java.io.Serial;
import java.util.Iterator;

/**
 * NumberPool domain implementation.
 *
 * @author harald
 */
@DomainObjectService(NumberPool.class)
public class NumberPoolDomainImpl extends AbstractDomainObject<NumberPool, NumberPoolDomainImpl> implements NumberPoolDomain {

  @Serial
  private static final long serialVersionUID = 1L;


  public NumberPoolDomainImpl(NumberPool pdo) {
    super(pdo);
  }

  public NumberPoolDomainImpl() {
    super();
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(me().getName());
    if (me().getRealm() != null) {
      buf.append(':').append(me().getRealm());
    }
    return buf.toString();
  }

  /**
   * {@inheritDoc}
   * <p>
   * <strong>This method modifies the pool.</strong> While scanning for the lowest non-empty range
   * it drops every exhausted range from {@link NumberPool#getNumberRangeList()}. That list is a
   * {@code TrackedList}, so the removed ranges are <em>deleted from the database</em> as soon as
   * the pool is persisted (see {@code NumberPoolPersistenceImpl.deleteReferencingRelations}) &mdash;
   * this is the intended housekeeping that keeps the range table from filling up with used-up rows.
   * <p>
   * Two consequences for callers:
   * <ul>
   *   <li>The pruning happens on the failure path as well: when the pool turns out to be empty, the
   *       ranges have already been removed before the {@link NumberSourceEmptyException} is thrown.</li>
   *   <li>Call this within a transaction that either persists the pool afterward or is rolled back,
   *       and hold the pool locked while doing so &mdash; otherwise the in-memory list silently
   *       diverges from the database, or the deletions ride along with an unrelated later save.</li>
   * </ul>
   * The method is not thread-safe and does not lock anything itself. {@code DefaultNumberSource} is
   * the intended caller and provides all of it: its methods are {@code synchronized}, run inside a
   * transaction, take a database row lock on the pool via {@code reloadForUpdate()}, and persist the
   * pool at the end.
   *
   * @return the current range, never null
   * @throws NumberSourceEmptyException if no non-empty range is left
   */
  @Override
  public NumberRange getCurrentRange() {
    NumberRange currentRange = null;
    for (Iterator<NumberRange> iter =  me().getNumberRangeList().iterator(); iter.hasNext(); ) {
      NumberRange range = iter.next();
      if (range.isEmpty()) {
        iter.remove();    // housekeeping: gets the exhausted range deleted on the next persist
      }
      else  {
        // find the lowest range
        if (currentRange == null || currentRange.getBegin() > range.getBegin()) {
          currentRange = range;
        }
      }
    }
    if (currentRange == null) {
      throw new NumberSourceEmptyException("pool " + this + " is empty");
    }
    return currentRange;
  }

  @Override
  public boolean isSlave() {
    return me().getLowWaterMark() > 0 && me().getRequestSize() > 0;   // the uplink URL is checked in validator
  }
}
