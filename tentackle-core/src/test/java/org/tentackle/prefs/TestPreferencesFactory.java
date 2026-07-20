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

package org.tentackle.prefs;

import org.tentackle.common.Service;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The {@link PersistedPreferencesFactory} used throughout the {@code org.tentackle.prefs} tests.
 * <p>
 * It is registered via {@code @Service} so that {@link PersistedPreferencesFactory#getInstance()} resolves to it.
 * The user- and system roots are backed by an {@link InMemoryPreferences} tree, so the tests never touch the
 * operating system's preference store. The backing roots are static (the singleton caches them) and can be wiped
 * between tests with {@link #resetBackingStore()}.
 */
@Service(PersistedPreferencesFactory.class)
public class TestPreferencesFactory extends DefaultPreferencesFactory {

  private static final InMemoryPreferences USER_BACKING = new InMemoryPreferences(true);
  private static final InMemoryPreferences SYSTEM_BACKING = new InMemoryPreferences(false);

  /**
   * Wipes all keys and child nodes from the in-memory backing store.
   */
  public static void resetBackingStore() {
    clearTree(USER_BACKING);
    clearTree(SYSTEM_BACKING);
  }

  private static void clearTree(Preferences node) {
    try {
      node.clear();
      for (String child : node.childrenNames()) {
        node.node(child).removeNode();
      }
      node.flush();
    }
    catch (BackingStoreException bsx) {
      throw new IllegalStateException("cannot reset backing store", bsx);
    }
  }

  /**
   * Creates the factory (invoked reflectively by the service loader).
   */
  public TestPreferencesFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public DefaultPreferences userRoot() {
    return new DefaultPreferences(USER_BACKING);
  }

  @Override
  public DefaultPreferences systemRoot() {
    return new DefaultPreferences(SYSTEM_BACKING);
  }
}
