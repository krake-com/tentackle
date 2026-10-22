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
import org.tentackle.ns.NumberSource.Range;
import org.tentackle.ns.NumberSourceEmptyException;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.ns.pdo.NumberRangeDomain;
import org.tentackle.pdo.DomainObjectService;

import java.io.Serial;

/**
 * Number range domain implementation.<br>
 * A range covers the numbers from {@code begin} (inclusive) to {@code end} (exclusive).
 *
 * @author harald
 */
@DomainObjectService(NumberRange.class)
public class NumberRangeDomainImpl extends AbstractDomainObject<NumberRange, NumberRangeDomainImpl> implements NumberRangeDomain {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Creates the domain delegate for the given number range.
   *
   * @param pdo the number range PDO
   */
  public NumberRangeDomainImpl(NumberRange pdo) {
    super(pdo);
  }

  /**
   * Creates the domain delegate without a PDO.<br>
   * The PDO is linked later via {@link #setPdo}.
   */
  public NumberRangeDomainImpl() {
    super();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Returns the number pool followed by {@code [begin,end]}.
   */
  @Override
  public String toString() {
    return String.valueOf(me().getNumberPool()) + '[' + me().getBegin() + ',' + me().getEnd() + ']';
  }

  @Override
  public long size() {
    long size = me().getEnd() - me().getBegin();
    if (size < 0) {
      size = 0;   // inconsistent range (end < begin): treat as empty
    }
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public long popNumber() {
    assertNotEmpty();
    long number = me().getBegin();
    me().setBegin(number + 1);
    return number;
  }

  /**
   * {@inheritDoc}
   * <p>
   * If fewer numbers than requested are left, the returned range is truncated to the remaining size.
   */
  @Override
  public Range popNumbers(long count) {
    assertNotEmpty();
    long size = size();
    if (count > size) {
      count = size;
    }
    Range range = new Range(me().getBegin(), me().getBegin() + count);
    me().setBegin(range.end());
    return range;
  }

  @Override
  public boolean intersects(long begin, long end) {
    return begin < me().getEnd() && end > me().getBegin();
  }


  /**
   * Asserts that the range is not empty.
   *
   * @throws NumberSourceEmptyException if empty
   */
  protected void assertNotEmpty() {
    if (isEmpty()) {
      throw new NumberSourceEmptyException("range " + this + " is empty");
    }
  }

}
