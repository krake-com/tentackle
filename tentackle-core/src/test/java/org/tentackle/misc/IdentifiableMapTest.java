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


package org.tentackle.misc;

import java.util.Iterator;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the identifiable map.
 *
 * @author harald
 */
public class IdentifiableMapTest {

  private abstract static class NumberIdentifiable implements Identifiable {

    private static long idCount;

    private final Number number;
    private long id;

    private NumberIdentifiable(Number number) {
      this.number = number;
      this.id = ++idCount;
    }

    @Override
    public long getId() {
      return id;
    }

    @Override
    public void setId(long id) {
      this.id = id;
    }

    @Override
    public String toGenericString() {
      return number + "[" + getId() + "]";
    }

    @Override
    public String toString() {
      return toGenericString();
    }
  }


  private static class IntegerIdentifiable extends NumberIdentifiable {
    private IntegerIdentifiable(Integer number) {
      super(number);
    }
  }


  private static class LongIdentifiable extends NumberIdentifiable {
    private LongIdentifiable(Long number) {
      super(number);
    }
  }


  @Test
  public void testMap() {

    IdentifiableMap<NumberIdentifiable> map = new IdentifiableMap<>();

    map.add(new LongIdentifiable(1100L));
    map.add(new LongIdentifiable(1200L));
    map.add(new LongIdentifiable(1300L));
    map.add(new IntegerIdentifiable(-100));
    map.add(new IntegerIdentifiable(200));
    map.add(new IntegerIdentifiable(300));

    Iterator<NumberIdentifiable> allIter = map.getAll().iterator();
    Assert.assertEquals(allIter.next().toString(), "1100[1]");
    Assert.assertEquals(allIter.next().toString(), "1200[2]");
    Assert.assertEquals(allIter.next().toString(), "1300[3]");
    Assert.assertEquals(allIter.next().toString(), "-100[4]");
    Assert.assertEquals(allIter.next().toString(), "200[5]");
    Assert.assertEquals(allIter.next().toString(), "300[6]");

    allIter = map.getAllSorted().iterator();
    Assert.assertEquals(allIter.next().toString(), "-100[4]");
    Assert.assertEquals(allIter.next().toString(), "200[5]");
    Assert.assertEquals(allIter.next().toString(), "300[6]");
    Assert.assertEquals(allIter.next().toString(), "1100[1]");
    Assert.assertEquals(allIter.next().toString(), "1200[2]");
    Assert.assertEquals(allIter.next().toString(), "1300[3]");

    map.add(new LongIdentifiable(1400L));
    allIter = map.getAll().iterator();
    Assert.assertEquals(allIter.next().toString(), "1100[1]");
    Assert.assertEquals(allIter.next().toString(), "1200[2]");
    Assert.assertEquals(allIter.next().toString(), "1300[3]");
    Assert.assertEquals(allIter.next().toString(), "-100[4]");
    Assert.assertEquals(allIter.next().toString(), "200[5]");
    Assert.assertEquals(allIter.next().toString(), "300[6]");
    Assert.assertEquals(allIter.next().toString(), "1400[7]");

    allIter = map.getAllSorted().iterator();
    Assert.assertEquals(allIter.next().toString(), "-100[4]");
    Assert.assertEquals(allIter.next().toString(), "200[5]");
    Assert.assertEquals(allIter.next().toString(), "300[6]");
    Assert.assertEquals(allIter.next().toString(), "1100[1]");
    Assert.assertEquals(allIter.next().toString(), "1200[2]");
    Assert.assertEquals(allIter.next().toString(), "1300[3]");
    Assert.assertEquals(allIter.next().toString(), "1400[7]");

    map.remove(IntegerIdentifiable.class, 4);
    allIter = map.getAll().iterator();
    Assert.assertEquals(allIter.next().toString(), "1100[1]");
    Assert.assertEquals(allIter.next().toString(), "1200[2]");
    Assert.assertEquals(allIter.next().toString(), "1300[3]");
    Assert.assertEquals(allIter.next().toString(), "200[5]");
    Assert.assertEquals(allIter.next().toString(), "300[6]");
    Assert.assertEquals(allIter.next().toString(), "1400[7]");

    allIter = map.getAllSorted().iterator();
    Assert.assertEquals(allIter.next().toString(), "200[5]");
    Assert.assertEquals(allIter.next().toString(), "300[6]");
    Assert.assertEquals(allIter.next().toString(), "1100[1]");
    Assert.assertEquals(allIter.next().toString(), "1200[2]");
    Assert.assertEquals(allIter.next().toString(), "1300[3]");
    Assert.assertEquals(allIter.next().toString(), "1400[7]");

    Iterator<IntegerIdentifiable> intIter = map.getAllSorted(IntegerIdentifiable.class).iterator();
    Assert.assertEquals(intIter.next().toString(), "200[5]");
    Assert.assertEquals(intIter.next().toString(), "300[6]");

    Iterator<LongIdentifiable> longIter = map.getAllSorted(LongIdentifiable.class).iterator();
    Assert.assertEquals(longIter.next().toString(), "1100[1]");
    Assert.assertEquals(longIter.next().toString(), "1200[2]");
    Assert.assertEquals(longIter.next().toString(), "1300[3]");
    Assert.assertEquals(longIter.next().toString(), "1400[7]");
  }

}