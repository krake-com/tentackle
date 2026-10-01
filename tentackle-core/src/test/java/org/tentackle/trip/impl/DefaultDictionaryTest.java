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

package org.tentackle.trip.impl;

import org.testng.annotations.Test;

import org.tentackle.trip.DictionaryEntry;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link DefaultDictionary}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultDictionaryTest {

  /**
   * An interrupt must not turn the wait for a missing entry into an immediate timeout.
   */
  @Test(timeOut = 30000)
  public void interruptDoesNotAbortWaitingForEntry() throws InterruptedException {
    DefaultDictionary dictionary = new DefaultDictionary(UUID.randomUUID(), true);
    DictionaryEntry entry = dictionary.createEntry(TestClass.class);
    AtomicReference<Object> result = new AtomicReference<>();
    AtomicReference<Boolean> stillInterrupted = new AtomicReference<>();

    Thread reader = new Thread(() -> {
      Thread.currentThread().interrupt();
      try {
        result.set(dictionary.getEntry(entry.getOrdinal()));
      }
      catch (RuntimeException ex) {
        result.set(ex);
      }
      stillInterrupted.set(Thread.currentThread().isInterrupted());
    });
    reader.start();
    Thread.sleep(200);
    assertNull(result.get(), "getEntry returned prematurely: " + result.get());
    dictionary.addEntry(entry);
    reader.join(10000);

    assertSame(result.get(), entry);
    assertTrue(stillInterrupted.get(), "interrupt flag must be restored");
  }

}
