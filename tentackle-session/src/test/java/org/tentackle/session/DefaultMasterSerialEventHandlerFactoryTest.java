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

package org.tentackle.session;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;

/**
 * Tests the default master serial event handler factory.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultMasterSerialEventHandlerFactoryTest {

  /**
   * An event without any handler.<br>
   * Must not extend {@link BrokenHandlerTestEvent}, since handlers are mapped leniently,
   * i.e. along the class hierarchy.
   */
  private static class UnhandledEvent implements MasterSerialEvent {

    @Override
    public void setSerial(long serial) {
    }

    @Override
    public long serial() {
      return 0;
    }
  }

  @Test
  public void testNoHandler() {
    Assert.assertNull(new DefaultMasterSerialEventHandlerFactory().getEventHandler(UnhandledEvent.class));
  }

  @Test
  public void testHandlerFailureKeepsCause() {
    DefaultMasterSerialEventHandlerFactory factory = new DefaultMasterSerialEventHandlerFactory();
    PersistenceException px = Assert.expectThrows(PersistenceException.class,
                                                  () -> factory.getEventHandler(BrokenHandlerTestEvent.class));
    // the reason must not get lost: the constructor's exception is wrapped by the reflection API
    Throwable cause = px.getCause();
    Assert.assertTrue(cause instanceof InvocationTargetException, "cause: " + cause);
    Assert.assertEquals(cause.getCause().getMessage(), BrokenTestEventHandler.FAILURE);
    Assert.assertTrue(px.getMessage().contains(BrokenTestEventHandler.class.getName()), px.getMessage());
  }

}
