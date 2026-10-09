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
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbModificationType;
import org.tentackle.dbms.ModificationLog;
import org.tentackle.dbms.ModificationLogSerializer;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.persist.trip.TripModificationLogSerializer;
import org.tentackle.session.SavepointHandle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link TripModificationLogSerializer}.
 */
public class TripModificationLogSerializerTest extends AbstractPdoTest {

  private static final String POOL_REALM = "test";

  /**
   * Serializer remembering the RESET modlogs and with a controllable clock.
   */
  private static class TestSerializer extends TripModificationLogSerializer {

    private final List<ModificationLog> resetLogs = new ArrayList<>();
    private long now = System.currentTimeMillis();

    private TestSerializer(int maxObjects, Duration maxAge) {
      super(maxObjects, maxAge);
    }

    @Override
    protected ModificationLog writeResetModificationLog(Db db) {
      ModificationLog log = super.writeResetModificationLog(db);
      resetLogs.add(log);
      return log;
    }

    @Override
    protected long currentTimeMillis() {
      return now;
    }
  }


  public TripModificationLogSerializerTest() {
    super(TransactionType.METHOD);    // rolled back after each method
  }


  @Test(groups = "local")
  public void roundTripWithoutComponents() {
    Reporter.log("TripModificationLogSerializerTest.roundTripWithoutComponents <br>", true);

    TestSerializer writer = new TestSerializer(0, null);
    TripModificationLogSerializer reader = new TripModificationLogSerializer(0, null);
    NumberPool numberPool = createNumberPool("modlogser1");

    byte[] data = writer.serialize(persistenceDelegate(numberPool));
    // the very first serialization writes a RESET
    assertEquals(writer.resetLogs.size(), 1);
    ModificationLog resetLog = writer.resetLogs.getFirst();
    assertTrue(resetLog.getId() > 0);
    assertEquals(resetLog.getModType(), DbModificationType.RESET);

    AbstractPersistentObject<NumberPool, ?> po = reader.deserialize(data);
    po.setSession(getSession());
    NumberPool copy = po.getPdo();
    assertNotSame(copy, numberPool);
    assertEquals(copy.getId(), numberPool.getId());
    assertEquals(copy.getSerial(), numberPool.getSerial());
    assertEquals(copy.getName(), numberPool.getName());
    assertEquals(copy.getRealm(), POOL_REALM);
    assertTrue(copy.isOnline());
    assertFalse(copy.isNumberRangeListLoaded());

    // loaded lazily from the database
    assertEquals(copy.getNumberRangeList().size(), 2);
    assertTrue(copy.isNumberRangeListLoaded());

    // second serialization of the same type: no RESET, and smaller since the dictionary is not sent again
    byte[] data2 = writer.serialize(persistenceDelegate(numberPool));
    assertEquals(writer.resetLogs.size(), 1);
    assertTrue(data2.length < data.length);
    po = reader.deserialize(data2);
    assertEquals(po.getPdo().getName(), numberPool.getName());
  }

  @Test(groups = "local")
  public void resetAfterMaxObjects() {
    Reporter.log("TripModificationLogSerializerTest.resetAfterMaxObjects <br>", true);

    TestSerializer writer = new TestSerializer(2, null);
    TripModificationLogSerializer reader = new TripModificationLogSerializer(2, null);
    NumberPool numberPool = createNumberPool("modlogser2");
    NumberRange numberRange = numberPool.getNumberRangeList().getFirst();

    List<byte[]> data = new ArrayList<>();
    List<Integer> resetIndexes = new ArrayList<>();    // index of the first object after a RESET
    for (int i = 0; i < 5; i++) {
      int resets = writer.resetLogs.size();
      if (i % 2 == 0) {
        data.add(writer.serialize(persistenceDelegate(numberPool)));
      }
      else {
        data.add(writer.serialize(persistenceDelegate(numberRange)));
      }
      if (writer.resetLogs.size() > resets) {
        resetIndexes.add(i);
      }
    }
    assertEquals(resetIndexes, List.of(0, 2, 4));

    // the reader resets whenever it encounters the RESET modlog
    for (int i = 0; i < data.size(); i++) {
      if (resetIndexes.contains(i)) {
        reader.reset();
      }
      AbstractPersistentObject<?, ?> po = reader.deserialize(data.get(i));
      if (i % 2 == 0) {
        assertEquals(((NumberPool) po.getPdo()).getName(), numberPool.getName());
      }
      else {
        assertEquals(((NumberRange) po.getPdo()).getEnd(), numberRange.getEnd());
      }
    }
  }

  @Test(groups = "local")
  public void resetAfterMaxAge() {
    Reporter.log("TripModificationLogSerializerTest.resetAfterMaxAge <br>", true);

    TestSerializer writer = new TestSerializer(0, Duration.ofMinutes(5));
    NumberPool numberPool = createNumberPool("modlogser3");

    writer.serialize(persistenceDelegate(numberPool));
    assertEquals(writer.resetLogs.size(), 1);
    writer.now += Duration.ofMinutes(4).toMillis();
    assertFalse(writer.isResetPending());
    writer.serialize(persistenceDelegate(numberPool));
    assertEquals(writer.resetLogs.size(), 1);
    writer.now += Duration.ofMinutes(1).toMillis();
    assertTrue(writer.isResetPending());
    writer.serialize(persistenceDelegate(numberPool));
    assertEquals(writer.resetLogs.size(), 2);
    assertFalse(writer.isResetPending());
  }

  @Test(groups = "local")
  public void resetAfterRollback() {
    Reporter.log("TripModificationLogSerializerTest.resetAfterRollback <br>", true);

    Db db = (Db) getSession();
    TestSerializer writer = new TestSerializer(0, null);
    NumberPool numberPool = createNumberPool("modlogser4");
    NumberRange numberRange = numberPool.getNumberRangeList().getFirst();

    writer.serialize(persistenceDelegate(numberPool));
    // RESET and new dictionary entries -> registered
    assertSame(db.getTransactionProperty(ModificationLogSerializer.class), writer);

    // no new entries -> not registered
    db.setTransactionProperty(ModificationLogSerializer.class, null);
    writer.serialize(persistenceDelegate(numberPool));
    assertNull(db.getTransactionProperty(ModificationLogSerializer.class));
    assertFalse(writer.isResetPending());

    // new type within a savepoint that is rolled back
    SavepointHandle savepoint = db.setSavepoint();
    writer.serialize(persistenceDelegate(numberRange));
    assertSame(db.getTransactionProperty(ModificationLogSerializer.class), writer);
    db.rollback(savepoint);
    assertTrue(writer.isResetPending());
    writer.serialize(persistenceDelegate(numberRange));
    assertEquals(writer.resetLogs.size(), 2);
    assertFalse(writer.isResetPending());

    // rollback of the whole transaction
    endTransaction();
    assertTrue(writer.isResetPending());
    beginTransaction();   // for afterMethod
  }


  private NumberPool createNumberPool(String name) {
    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName(name);
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
    return on(NumberPool.class).select(numberPool.getId());
  }

  @SuppressWarnings("unchecked")
  private static <T extends PersistentDomainObject<T>> AbstractPersistentObject<T, ?> persistenceDelegate(T pdo) {
    return (AbstractPersistentObject<T, ?>) pdo.getPersistenceDelegate();
  }

}
