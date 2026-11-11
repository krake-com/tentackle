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

package org.tentackle.dbms;

import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.EchoTransceiver;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Tests that a driver-specific {@link SQLException} is transferred as a plain {@link SQLException}.<br>
 * The client usually doesn't have the JDBC driver on its classpath, so the driver's class must not
 * be sent over the wire.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DriverSQLExceptionTripTest {

  private static final UUID NIL_UUID = new UUID(0, 0);

  @Test
  void testPostgresException() {
    PSQLException px = new PSQLException("duplicate key", PSQLState.UNIQUE_VIOLATION, new IllegalStateException("cause"));
    px.setNextException(new SQLException("next", "08000", 7));

    Dictionary clientDictionary = TripFactory.getInstance().createDictionary(NIL_UUID, false);
    Throwable tunneled = tunnel(px, clientDictionary);

    assertNull(clientDictionary.getEntry(PSQLException.class.getName()), "driver class sent to the client");
    assertSame(tunneled.getClass(), SQLException.class);
    SQLException sx = (SQLException) tunneled;
    assertEquals(sx.getMessage(), px.getMessage() + " (" + PSQLException.class.getName() + ")");
    assertEquals(sx.getSQLState(), PSQLState.UNIQUE_VIOLATION.getState());
    assertEquals(sx.getErrorCode(), px.getErrorCode());
    assertSame(sx.getCause().getClass(), IllegalStateException.class);
    assertEquals(sx.getCause().getMessage(), "cause");
    assertEquals(sx.getNextException().getMessage(), "next");
    assertEquals(sx.getNextException().getSQLState(), "08000");
    assertEquals(sx.getStackTrace(), px.getStackTrace());
  }

  @Test
  void testPostgresExceptionAsCause() {
    PSQLException px = new PSQLException("duplicate key", PSQLState.UNIQUE_VIOLATION);
    TripRuntimeException tx = new TripRuntimeException("trip", px);

    Dictionary clientDictionary = TripFactory.getInstance().createDictionary(NIL_UUID, false);
    TripRuntimeException tunneled = (TripRuntimeException) tunnel(tx, clientDictionary);

    assertNull(clientDictionary.getEntry(PSQLException.class.getName()), "driver class sent to the client");
    assertEquals(tunneled.getMessage(), "trip");
    assertSame(tunneled.getCause().getClass(), SQLException.class);
    assertEquals(((SQLException) tunneled.getCause()).getSQLState(), PSQLState.UNIQUE_VIOLATION.getState());
  }


  @Test
  void testDriverCause() {
    // Oracle-style: a JDK SQL exception pointing to a driver-specific exception, which is not an SQL exception
    DriverException dx = new DriverException("ORA-00001: unique constraint violated", new IllegalStateException("inner"));
    SQLIntegrityConstraintViolationException cx = new SQLIntegrityConstraintViolationException("violated", "23000", 1, dx);

    Dictionary clientDictionary = TripFactory.getInstance().createDictionary(NIL_UUID, false);
    SQLException sx = (SQLException) tunnel(new PersistenceException("persistence", cx), clientDictionary).getCause();

    assertNull(clientDictionary.getEntry(DriverException.class.getName()), "driver class sent to the client");
    assertSame(sx.getClass(), SQLIntegrityConstraintViolationException.class);
    assertEquals(sx.getMessage(), "violated");
    assertEquals(sx.getSQLState(), "23000");
    assertSame(sx.getCause().getClass(), Exception.class);
    assertEquals(sx.getCause().getMessage(), dx.toString());
    assertEquals(sx.getCause().getStackTrace(), dx.getStackTrace());
    assertSame(sx.getCause().getCause().getClass(), IllegalStateException.class);
    assertEquals(sx.getCause().getCause().getMessage(), "inner");
  }

  @Test
  void testLocalCauseUnchanged() {
    // the driver's exception is replaced only on the wire, not locally
    PSQLException px = new PSQLException("deadlock", PSQLState.UNKNOWN_STATE, new IllegalStateException("cause"));
    assertSame(new PersistenceException("persistence", px).getCause(), px);
  }


  /**
   * Driver-specific exception, which is not an SQL exception, such as {@code oracle.jdbc.OracleDatabaseException}.
   */
  public static class DriverException extends Exception {

    private static final long serialVersionUID = 1L;

    public DriverException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Transfers the value as an object of unknown type, as a remote call's result or exception is.
   *
   * @param value the value
   * @param clientDictionary the receiving dictionary
   * @return the transferred value
   */
  private Throwable tunnel(Throwable value, Dictionary clientDictionary) {
    EchoTransceiver transceiver = new EchoTransceiver(
      TripFactory.getInstance().createDictionary(NIL_UUID, true), clientDictionary, false, null);
    TripType<Object> type = transceiver.getStream().getType(value.getClass());
    type.write(value, transceiver.getStream());
    transceiver.echo();
    OpCode opCode = transceiver.getStream().getDeserializer().deserializeOpCode();
    return (Throwable) transceiver.getStream().deriveType(opCode).read(transceiver.getStream(), opCode);
  }

}
