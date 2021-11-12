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
 * NumberRange domain implementation.
 *
 * @author harald
 */
@DomainObjectService(NumberRange.class)
public class NumberRangeDomainImpl extends AbstractDomainObject<NumberRange, NumberRangeDomainImpl> implements NumberRangeDomain {

  @Serial
  private static final long serialVersionUID = 1L;


  public NumberRangeDomainImpl(NumberRange pdo) {
    super(pdo);
  }

  public NumberRangeDomainImpl() {
    super();
  }

  @Override
  public String toString() {
    return String.valueOf(me().getNumberPool()) + '[' + me().getBegin() + ',' + me().getEnd() + ']';
  }

  @Override
  public long size() {
    long size = me().getEnd() - me().getBegin();
    if (size < 0) {
      size = 0;   // how?
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
   * Asserts that range is not empty.
   * @throws NumberSourceEmptyException if empty
   */
  protected void assertNotEmpty() {
    if (isEmpty()) {
      throw new NumberSourceEmptyException("range " + this + " is empty");
    }
  }

}
