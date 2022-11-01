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

import org.testng.annotations.Test;

import org.tentackle.misc.Provider;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * Tests for transaction envelope.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class TransactionEnvelopeTest {

  @SuppressWarnings("serial")
  private class MyException extends Exception {
    public MyException(String message) {
      super(message);
    }
  }

  @SuppressWarnings("serial")
  private class OtherException extends Exception {
    public OtherException(String message) {
      super(message);
    }
  }


  @Test
  public void testEnvelope() {
    try (Session session = SessionFactory.getInstance().create()) {

      // without checked exception
      int rv = session.transaction(() -> {
        assertEquals(session.getTxName(), "TransactionEnvelopeTest#testEnvelope");
        return 5;
      });
      assertEquals(rv, 5);

      // with checked exceptions
      try {
        session.transaction(() -> {
          if (System.currentTimeMillis() > 1000) {    // always true (to avoid compiler optimization)
            throw new MyException("test");
          }
          else {
            throw new OtherException("blah");
          }
        });
      }
      catch (OtherException ex1) {
        fail("should not be thrown", ex1);
      }
      catch (MyException ex2) {
        assertEquals(ex2.getMessage(), "test");
      }
      catch (Throwable t) {
        fail("exception not retained", t);
      }

      try {
        session.transaction(() -> {
          if (System.currentTimeMillis() < 1000) {    // always false (to avoid compiler optimization)
            throw new MyException("test");
          }
          else {
            throw new OtherException("blah");
          }
        });
      }
      catch (OtherException ex1) {
        assertEquals(ex1.getMessage(), "blah");
      }
      catch (MyException ex2) {
        fail("should not be thrown", ex2);
      }
      catch (Throwable t) {
        fail("exception not retained", t);
      }

      // with inner class
      rv = session.transaction(new Provider<>() {
        @Override
        public Integer get() {
          assertEquals(session.getTxName(), "TransactionEnvelopeTest#testEnvelope");
          return 10;
        }
      });
      assertEquals(rv, 10);
    }
  }

}
