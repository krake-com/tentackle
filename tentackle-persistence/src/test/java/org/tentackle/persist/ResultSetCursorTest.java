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

package org.tentackle.persist;

import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Unit tests for {@link ResultSetCursor}.
 * <p>
 * A cursor holds a result set (or a remote cursor) that must be released exactly once, either by
 * {@code close()} on the application thread or, if the cursor was dropped without being closed, by
 * the cleaner thread. Everything else about a cursor needs a session and a result set and is
 * therefore covered by the database-backed tests in {@code tentackle-test-pdo}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ResultSetCursorTest {

  /**
   * The resources handed to the cleaner must be volatile.
   * <p>
   * They are written by whichever thread runs the cleanup - the closing application thread or the
   * cleaner thread - and read by {@link ResultSetCursor#isClosed()} on the application thread.
   * Without volatile a closed cursor may never be seen as closed, and a properly closed one may be
   * logged as unreferenced.
   * <p>
   * The race is real but practically unobservable on demand, so the declaration itself is asserted.
   */
  @Test
  public void testCleanupResourcesAreVolatile() {
    Class<?> resourcesClass = null;
    for (Class<?> nested : ResultSetCursor.class.getDeclaredClasses()) {
      if ("Resources".equals(nested.getSimpleName())) {
        resourcesClass = nested;
        break;
      }
    }
    if (resourcesClass == null) {
      fail("no Resources class in " + ResultSetCursor.class.getName());
    }

    for (String fieldName : List.of("rs", "remoteCursor", "me")) {
      try {
        Field field = resourcesClass.getDeclaredField(fieldName);
        assertTrue(Modifier.isVolatile(field.getModifiers()),
                   field.getName() + " is written by the cleaner thread as well and must be volatile");
      }
      catch (NoSuchFieldException nsx) {
        fail("no such field: " + fieldName, nsx);
      }
    }
  }

}
