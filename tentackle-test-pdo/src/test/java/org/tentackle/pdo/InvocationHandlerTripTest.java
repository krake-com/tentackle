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

import org.tentackle.misc.TrackedList;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.EchoTransceiver;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the invocation handlers of PDOs and operations survive a TRIP round trip.<br>
 * The handlers transfer their delegates, but not their mixins, which are derived from the delegates
 * on the receiving side.
 */
@SuppressWarnings("missing-explicit-ctor")
public class InvocationHandlerTripTest extends AbstractPdoTest {

  @SuppressWarnings("unchecked")
  private static <T> T transfer(T object) {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream stream = transceiver.getStream();
    TripType<T> type = TripFactory.getInstance().getType((Class<T>) object.getClass());
    type.write(object, stream);
    transceiver.echo();
    stream = transceiver.getStream();   // switched!
    OpCode opCode = stream.getDeserializer().deserializeOpCode();
    return type.read(stream, opCode);
  }

  @Test(groups = "local")
  @SuppressWarnings("unchecked")
  public void transferPdo() throws NoSuchMethodException {
    Reporter.log("InvocationHandlerTripTest.transferPdo <br>", true);

    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName("handlertrip");
    numberPool.setRealm("test");

    NumberPool copy = transfer(numberPool);
    assertNotSame(copy, numberPool);

    // the delegates back-reference the new proxy
    assertSame(copy.getPersistenceDelegate().getPdo(), copy);
    assertSame(copy.getDomainDelegate().getPdo(), copy);

    // special methods
    assertEquals(copy.getEffectiveClass(), NumberPool.class);
    assertEquals(copy.getEffectiveSuperClasses(), List.of(NumberPool.class.getInterfaces()[0]));
    assertEquals(copy.hashCode(), copy.getPersistenceDelegate().hashCode());
    assertTrue(copy.equals(copy));
    assertEquals(copy.toString(), numberPool.toString());

    // persistence and domain methods
    assertEquals(copy.getName(), "handlertrip");
    assertEquals(copy.isSlave(), numberPool.isSlave());

    // the mixins are derived from the delegates
    PdoInvocationHandler<NumberPool> handler = (PdoInvocationHandler<NumberPool>) Proxy.getInvocationHandler(copy);
    PdoMethodCacheInfo info = handler.determineInvocation(NumberPool.class.getMethod("getName"));
    assertEquals(info.delegateClass(), copy.getPersistenceDelegate().getClass());
    info = handler.determineInvocation(NumberPool.class.getMethod("isSlave"));
    assertEquals(info.delegateClass(), copy.getDomainDelegate().getClass());
  }

  @Test(groups = "local")
  public void transferPdoWithRemovedComponent() {
    Reporter.log("InvocationHandlerTripTest.transferPdoWithRemovedComponent <br>", true);

    // The removed components of a tracked list are kept in a hash set. They share the domain
    // context, which the hash guard must not refuse, because the hash code of a PDO depends on
    // its class and ID only.
    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName("removedtrip");
    numberPool.setRealm("test");
    NumberRange range1 = on(NumberRange.class);
    range1.setBegin(1);
    range1.setEnd(100);
    NumberRange range2 = on(NumberRange.class);
    range2.setBegin(100);
    range2.setEnd(200);
    numberPool.getNumberRangeList().add(range1);
    numberPool.getNumberRangeList().add(range2);
    numberPool.getNumberRangeList().remove(range2);
    assertTrue(numberPool.getNumberRangeList().isSomeRemoved());

    NumberPool copy = transfer(numberPool);
    TrackedList<NumberRange> rangeList = copy.getNumberRangeList();
    assertEquals(rangeList.size(), 1);
    assertTrue(rangeList.isSomeRemoved());
    assertEquals(rangeList.getRemovedObjects().size(), 1);
    NumberRange removed = rangeList.getRemovedObjects().iterator().next();
    assertEquals(removed.getBegin(), 100);
    // numberPool wasn't persisted, hence non-root context
    assertSame(removed.getDomainContext(), copy.getDomainContext().getNonRootContext());
  }

  @Test(groups = "local")
  @SuppressWarnings("unchecked")
  public void transferOperation() throws NoSuchMethodException {
    Reporter.log("InvocationHandlerTripTest.transferOperation <br>", true);

    // persistence only: the domain side is a dummy without a delegate
    PdoCacheOperation operation = Pdo.createOperation(PdoCacheOperation.class, getDomainContext());
    assertNull(operation.getDomainDelegate());

    PdoCacheOperation copy = transfer(operation);
    assertNotSame(copy, operation);
    assertSame(copy.getPersistenceDelegate().getOperation(), copy);
    assertNull(copy.getDomainDelegate());

    assertEquals(copy.getEffectiveClass(), PdoCacheOperation.class);
    assertEquals(copy.hashCode(), copy.getPersistenceDelegate().hashCode());
    assertTrue(copy.equals(copy));

    OperationInvocationHandler<PdoCacheOperation> handler =
        (OperationInvocationHandler<PdoCacheOperation>) Proxy.getInvocationHandler(copy);
    OperationMethodCacheInfo info = handler.determineInvocation(PdoCacheOperation.class.getMethod("getDomainContext"));
    assertEquals(info.delegateClass(), copy.getPersistenceDelegate().getClass());
  }

}
