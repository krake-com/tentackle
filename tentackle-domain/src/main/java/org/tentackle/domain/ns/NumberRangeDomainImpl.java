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


package org.tentackle.domain.ns;

import org.tentackle.ns.NumberSource.Range;
import org.tentackle.ns.NumberSourceEmptyException;
import org.tentackle.domain.AbstractDomainObject;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.ns.pdo.NumberRangeDomain;
import org.tentackle.pdo.DomainObjectService;

/**
 * NumberRange domain implementation.
 *
 * @author harald
 */
@DomainObjectService(NumberRange.class)
public class NumberRangeDomainImpl extends AbstractDomainObject<NumberRange, NumberRangeDomainImpl> implements NumberRangeDomain {

  private static final long serialVersionUID = 1L;


  public NumberRangeDomainImpl(NumberRange pdo) {
    super(pdo);
  }

  public NumberRangeDomainImpl() {
    super();
  }

  @Override
  public String toString() {
    return String.valueOf(pdo().getNumberPool()) + '[' + pdo().getBegin() + ',' + pdo().getEnd() + ']';
  }

  @Override
  public long size() {
    long size = pdo().getEnd() - pdo().getBegin();
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
    long number = pdo().getBegin();
    pdo().setBegin(number + 1);
    return number;
  }

  @Override
  public Range popNumbers(long count) {
    assertNotEmpty();
    long size = size();
    if (count > size) {
      count = size;
    }
    Range range = new Range(pdo().getBegin(), pdo().getBegin() + count);
    pdo().setBegin(range.getEnd());
    return range;
  }

  @Override
  public boolean intersects(long begin, long end) {
    return begin < pdo().getEnd() && end > pdo().getBegin();
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
