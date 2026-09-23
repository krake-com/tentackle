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

package org.tentackle.pdo;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.persist.trip.PdoComponentTripFilter;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripFilter;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.EchoTransceiver;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests serializing PDOs without their components.
 */
public class PdoComponentTripFilterTest extends AbstractPdoTest {

  private static final String POOL_NAME = "tripfilter";
  private static final String POOL_REALM = "test";


  @SuppressWarnings("unchecked")
  private static <T> T transfer(T object, TripFilter filter) {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream stream = transceiver.getStream();
    stream.setFilter(filter);
    TripType<T> type = TripFactory.getInstance().getType((Class<T>) object.getClass());
    type.write(object, stream);
    transceiver.echo();
    stream = transceiver.getStream();   // switched!
    OpCode opCode = stream.getDeserializer().deserializeOpCode();
    return type.read(stream, opCode);
  }

  @Test(groups = "local")
  public void transferWithoutComponents() {
    Reporter.log("PdoComponentTripFilterTest.transferWithoutComponents <br>", true);

    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName(POOL_NAME);
    numberPool.setRealm(POOL_REALM);
    numberPool.setOnline(true);
    NumberRange numberRange = numberPool.on(NumberRange.class);
    numberRange.setBegin(1);
    numberRange.setEnd(1000);
    numberPool.getNumberRangeList().add(numberRange);
    numberRange = numberRange.on();
    numberRange.setBegin(2000);
    numberRange.setEnd(3000);
    numberPool.getNumberRangeList().add(numberRange);
    numberPool = numberPool.persist();

    numberPool = on(NumberPool.class).select(numberPool.getId());
    assertEquals(numberPool.getNumberRangeList().size(), 2);
    assertTrue(numberPool.isNumberRangeListLoaded());

    // without filter: the components are transferred as well
    NumberPool copy = transfer(numberPool, null);
    assertNotSame(copy, numberPool);
    assertTrue(copy.isNumberRangeListLoaded());
    assertEquals(copy.getNumberRangeList().size(), 2);

    // with filter: the components are skipped
    copy = transfer(numberPool, PdoComponentTripFilter.getInstance());
    assertNotSame(copy, numberPool);
    assertEquals(copy.getId(), numberPool.getId());
    assertEquals(copy.getSerial(), numberPool.getSerial());
    assertEquals(copy.getName(), POOL_NAME);
    assertEquals(copy.getRealm(), POOL_REALM);
    assertTrue(copy.isOnline());
    assertFalse(copy.isModified());
    assertFalse(copy.isNumberRangeListLoaded());

    // loaded lazily from the database
    assertEquals(copy.getNumberRangeList().size(), 2);
    assertTrue(copy.isNumberRangeListLoaded());
    assertEquals(copy.getNumberRangeList().get(1).getEnd(), 3000);
  }

}
