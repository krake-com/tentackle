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

package org.tentackle.reflect;

import org.testng.annotations.Test;

import org.tentackle.log.LogCapture;
import org.tentackle.log.Logger;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link InterceptableMethodInvoker}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class InterceptableMethodInvokerTest {

  private static InterceptableMethod lengthMethod() throws NoSuchMethodException {
    Method method = String.class.getMethod("length");
    return new InterceptableMethod(method, method, null);
  }

  @Test
  public void logsInvocationsIfEnabled() throws Throwable {
    InterceptableMethodInvoker invoker = new InterceptableMethodInvoker("test");
    invoker.setLoggingInvocations(true);
    try (LogCapture capture = LogCapture.attach(InterceptableMethodInvoker.class, Logger.Level.INFO)) {
      assertEquals(invoker.invoke("abc", lengthMethod(), null), 3);
      String message = capture.singleRecord().getMessage();
      assertTrue(message.startsWith("String.length ["), message);
    }
  }

  @Test
  public void logsInvocationDetailsIfEnabled() throws Throwable {
    InterceptableMethodInvoker invoker = new InterceptableMethodInvoker("test");
    invoker.setLoggingInvocations(true);
    invoker.setLoggingInvocationDetails(true);
    try (LogCapture capture = LogCapture.attach(InterceptableMethodInvoker.class, Logger.Level.INFO)) {
      invoker.invoke("abc", lengthMethod(), null);
      String message = capture.singleRecord().getMessage();
      assertTrue(message.contains("String.length() = 3"), message);
    }
  }

  @Test
  public void doesNotLogIfOnlyCollectingStatistics() throws Throwable {
    InterceptableMethodInvoker invoker = new InterceptableMethodInvoker("test");
    invoker.setCollectingStatistics(true);
    invoker.setLoggingInvocationDetails(true);    // details alone don't enable logging
    try (LogCapture capture = LogCapture.attach(InterceptableMethodInvoker.class, Logger.Level.INFO)) {
      invoker.invoke("abc", lengthMethod(), null);
      assertTrue(capture.records().isEmpty(), capture.allMessages());
    }
  }

}
