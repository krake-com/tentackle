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

import java.util.Iterator;
import org.tentackle.ns.NumberSourceEmptyException;
import org.tentackle.domain.AbstractDomainObject;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberPoolDomain;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.DomainObjectService;

/**
 * NumberPool domain implementation.
 *
 * @author harald
 */
@DomainObjectService(NumberPool.class)
public class NumberPoolDomainImpl extends AbstractDomainObject<NumberPool, NumberPoolDomainImpl> implements NumberPoolDomain {

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
    buf.append(pdo().getName());
    if (pdo().getRealm() != null) {
      buf.append(':').append(pdo().getRealm());
    }
    return buf.toString();
  }

  @Override
  public NumberRange getCurrentRange() {
    NumberRange currentRange = null;
    for (Iterator<NumberRange> iter =  pdo().getNumberRangeList().iterator(); iter.hasNext(); ) {
      NumberRange range = iter.next();
      if (range.isEmpty()) {
        iter.remove();
      }
      else  {
        // find lowest range
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

}
