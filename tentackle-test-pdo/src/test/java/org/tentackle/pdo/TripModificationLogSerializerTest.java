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
import org.tentackle.dbms.StatementWrapper;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.persist.trip.RebuildEpochException;
import org.tentackle.persist.trip.TripModificationLogSerializer;
import org.tentackle.session.SavepointHandle;
import org.tentackle.session.Session;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

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
    // the very first serialization writes a RESET, which starts the epoch
    assertEquals(writer.resetLogs.size(), 1);
    ModificationLog resetLog = writer.resetLogs.getFirst();
    assertTrue(resetLog.getId() > 0);
    assertEquals(resetLog.getModType(), DbModificationType.RESET);
    assertEquals(writer.getCurrentEpoch(), resetLog.getId());
    assertEquals(TripModificationLogSerializer.getEpoch(data), resetLog.getId());

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

    // second serialization of the same type within the same transaction: no RESET,
    // and smaller since the dictionary is not sent again
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

    // the reader picks the dictionary by the epoch stored with the data
    for (int i = 0; i < data.size(); i++) {
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
  public void noResetAfterRollback() {
    Reporter.log("TripModificationLogSerializerTest.noResetAfterRollback <br>", true);

    Db db = (Db) getSession();
    TestSerializer writer = new TestSerializer(0, null);
    NumberPool numberPool = createNumberPool("modlogser4");
    NumberRange numberRange = numberPool.getNumberRangeList().getFirst();

    // new dictionary entries -> registered for commit and rollback
    byte[] data = writer.serialize(persistenceDelegate(numberPool));
    assertSame(db.getTransactionProperty(ModificationLogSerializer.class), writer);

    // new type within a savepoint that is rolled back
    SavepointHandle savepoint = db.setSavepoint();
    writer.serialize(persistenceDelegate(numberRange));
    db.rollback(savepoint);
    assertFalse(writer.isResetPending());

    // the pending entries are discarded: the types are defined again
    byte[] data2 = writer.serialize(persistenceDelegate(numberPool));
    assertEquals(data2.length, data.length);
    assertEquals(writer.resetLogs.size(), 1);

    // rollback of the whole transaction
    endTransaction();
    assertFalse(writer.isResetPending());
    beginTransaction();   // for afterMethod
  }

  @Test(groups = "local")
  public void overlappingTransactions() {
    Reporter.log("TripModificationLogSerializerTest.overlappingTransactions <br>", true);

    TestSerializer writer = new TestSerializer(0, null);
    TripModificationLogSerializer reader = new TripModificationLogSerializer(0, null);
    Db db1 = openDb();
    Db db2 = openDb();
    try {
      long tx1 = db1.begin("overlap1");
      byte[] data1 = writer.serialize(persistenceDelegate(newNumberPool(db1, "pool-1")));
      long tx2 = db2.begin("overlap2");
      // the entries of tx1 are not committed yet -> defined again
      byte[] data2 = writer.serialize(persistenceDelegate(newNumberPool(db2, "pool-2")));
      assertEquals(data2.length, data1.length);
      assertTrue(db2.commit(tx2));

      // the reader sees tx2 before tx1: must not wait for tx1's entries
      assertEquals(poolName(reader.deserialize(data2)), "pool-2");
      assertTrue(db1.commit(tx1));
      // the same type under another ordinal
      assertEquals(poolName(reader.deserialize(data1)), "pool-1");

      // a later transaction refers to the committed entries
      long tx3 = db1.begin("overlap3");
      byte[] data3 = writer.serialize(persistenceDelegate(newNumberPool(db1, "pool-3")));
      assertTrue(db1.commit(tx3));
      assertTrue(data3.length < data1.length);
      assertEquals(poolName(reader.deserialize(data3)), "pool-3");
      assertEquals(writer.resetLogs.size(), 1);
    }
    finally {
      cleanup(writer, db1, db2);
    }
  }

  @Test(groups = "local")
  public void rolledBackDefinition() {
    Reporter.log("TripModificationLogSerializerTest.rolledBackDefinition <br>", true);

    TestSerializer writer = new TestSerializer(0, null);
    TripModificationLogSerializer reader = new TripModificationLogSerializer(0, null);
    Db db1 = openDb();
    Db db2 = openDb();
    try {
      long tx1 = db1.begin("rollback1");
      byte[] data1 = writer.serialize(persistenceDelegate(newNumberPool(db1, "pool-1")));
      long tx2 = db2.begin("rollback2");
      byte[] data2 = writer.serialize(persistenceDelegate(newNumberPool(db2, "pool-2")));
      assertTrue(db1.rollback(tx1));    // the RESET is rolled back as well

      // the definition of tx1 was never visible to tx2
      byte[] data3 = writer.serialize(persistenceDelegate(newNumberPool(db2, "pool-3")));
      assertTrue(data3.length < data2.length);
      assertTrue(db2.commit(tx2));
      assertEquals(poolName(reader.deserialize(data2)), "pool-2");
      assertEquals(poolName(reader.deserialize(data3)), "pool-3");

      // nor is it to later transactions
      long tx4 = db1.begin("rollback4");
      byte[] data4 = writer.serialize(persistenceDelegate(newNumberPool(db1, "pool-4")));
      assertTrue(db1.commit(tx4));
      assertTrue(data4.length < data1.length);
      assertEquals(poolName(reader.deserialize(data4)), "pool-4");
      assertFalse(writer.isResetPending());
      assertEquals(writer.resetLogs.size(), 1);
    }
    finally {
      cleanup(writer, db1, db2);
    }
  }

  @Test(groups = "local")
  public void resetCrossingTransactions() {
    Reporter.log("TripModificationLogSerializerTest.resetCrossingTransactions <br>", true);

    TestSerializer writer = new TestSerializer(0, Duration.ofMinutes(5));
    TripModificationLogSerializer reader = new TripModificationLogSerializer(0, null);
    Db db1 = openDb();
    Db db2 = openDb();
    try {
      // epoch 1: the range type is committed
      long tx0 = db1.begin("crossing0");
      byte[] data0 = writer.serialize(persistenceDelegate(newNumberRange(db1, 1000)));
      assertTrue(db1.commit(tx0));
      long epoch1 = writer.getCurrentEpoch();

      // tx1 refers to the committed entries of epoch 1
      long tx1 = db1.begin("crossing1");
      byte[] data1 = writer.serialize(persistenceDelegate(newNumberRange(db1, 2000)));
      assertTrue(data1.length < data0.length);

      // epoch 2 starts while tx1 is still running: another type gets the same ordinals
      writer.now += Duration.ofMinutes(5).toMillis();
      long tx2 = db2.begin("crossing2");
      byte[] data2 = writer.serialize(persistenceDelegate(newNumberPool(db2, "pool-2")));
      long epoch2 = writer.getCurrentEpoch();
      assertTrue(epoch2 > epoch1);
      assertEquals(TripModificationLogSerializer.getEpoch(data1), epoch1);
      assertEquals(TripModificationLogSerializer.getEpoch(data2), epoch2);
      assertTrue(db2.commit(tx2));

      // tx1 continues in epoch 2 and commits after tx2
      byte[] data3 = writer.serialize(persistenceDelegate(newNumberRange(db1, 3000)));
      assertEquals(TripModificationLogSerializer.getEpoch(data3), epoch2);
      assertTrue(db1.commit(tx1));

      // the reader processes in commit order: the late data of epoch 1 uses the dictionary of epoch 1
      assertEquals(rangeEnd(reader.deserialize(data0)), 1000);
      assertEquals(poolName(reader.deserialize(data2)), "pool-2");
      assertEquals(rangeEnd(reader.deserialize(data1)), 2000);
      assertEquals(rangeEnd(reader.deserialize(data3)), 3000);

      // a reader keeping only one epoch must rebuild it
      TripModificationLogSerializer smallReader = new TripModificationLogSerializer(0, null);
      smallReader.setMaxEpochs(1);
      smallReader.deserialize(data0);
      smallReader.deserialize(data2);
      RebuildEpochException rx = expectThrows(RebuildEpochException.class, () -> smallReader.deserialize(data1));
      assertEquals(rx.getEpoch(), epoch1);
      assertEquals(smallReader.rebuildEpoch(epoch1, List.of(data0, data2)), 1);
      assertEquals(rangeEnd(smallReader.deserialize(data1)), 2000);
    }
    finally {
      cleanup(writer, db1, db2);
    }
  }

  @Test(groups = "local")
  public void rebuildAfterRestart() {
    Reporter.log("TripModificationLogSerializerTest.rebuildAfterRestart <br>", true);

    TestSerializer writer = new TestSerializer(0, null);
    Db db1 = openDb();
    try {
      long tx1 = db1.begin("restart1");
      byte[] data1 = writer.serialize(persistenceDelegate(newNumberPool(db1, "pool-1")));
      assertTrue(db1.commit(tx1));
      long tx2 = db1.begin("restart2");
      byte[] data2 = writer.serialize(persistenceDelegate(newNumberPool(db1, "pool-2")));
      assertTrue(db1.commit(tx2));

      // a restarted reader does not know the entries of the processed modlogs: fails immediately
      TripModificationLogSerializer reader = new TripModificationLogSerializer(0, null);
      RebuildEpochException rx = expectThrows(RebuildEpochException.class, () -> reader.deserialize(data2));
      long epoch = TripModificationLogSerializer.getEpoch(data2);
      assertEquals(rx.getEpoch(), epoch);

      assertEquals(reader.rebuildEpoch(epoch, List.of(data1)), 1);
      assertEquals(poolName(reader.deserialize(data2)), "pool-2");
    }
    finally {
      cleanup(writer, db1);
    }
  }


  private Db openDb() {
    return (Db) Pdo.createSession();
  }

  /**
   * Removes the committed RESET modlogs and closes the sessions.<br>
   * Other tests expect an empty modlog table.
   *
   * @param writer the serializer
   * @param dbs the sessions
   */
  private void cleanup(TestSerializer writer, Db... dbs) {
    for (Db db : dbs) {
      while (db.isTxRunning()) {
        db.rollbackImmediately(null);
      }
    }
    if (!writer.resetLogs.isEmpty()) {
      StringBuilder ids = new StringBuilder();
      for (ModificationLog log : writer.resetLogs) {
        if (!ids.isEmpty()) {
          ids.append(',');
        }
        ids.append(log.getId());
      }
      try (StatementWrapper st = dbs[0].createStatement()) {
        st.executeUpdate("DELETE FROM " + new ModificationLog(dbs[0]).getTableName() + " WHERE " + ModificationLog.CN_ID + " IN (" + ids + ")");
      }
    }
    for (Db db : dbs) {
      db.close();
    }
  }

  private NumberPool newNumberPool(Session session, String name) {
    NumberPool numberPool = Pdo.create(NumberPool.class, session);
    numberPool.setName(name);
    numberPool.setRealm(POOL_REALM);
    numberPool.setOnline(true);
    return numberPool;
  }

  private NumberRange newNumberRange(Session session, long end) {
    NumberRange numberRange = Pdo.create(NumberRange.class, session);
    numberRange.setBegin(1);
    numberRange.setEnd(end);
    return numberRange;
  }

  private String poolName(AbstractPersistentObject<?, ?> po) {
    po.setSession(getSession());
    return ((NumberPool) po.getPdo()).getName();
  }

  private long rangeEnd(AbstractPersistentObject<?, ?> po) {
    po.setSession(getSession());
    return ((NumberRange) po.getPdo()).getEnd();
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
