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

import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * A minimal in-memory {@link java.util.prefs.Preferences} implementation used as a backing store for the tests.
 * <p>
 * The JDK does not ship a public in-memory implementation, so this test double provides a deterministic tree that
 * never touches the operating system's preference store. The {@code userNode} flag is remembered so that
 * {@link #isUserNode()} can be exercised without depending on the platform's user/system roots.
 */
public class InMemoryPreferences extends AbstractPreferences {

  private final Map<String, String> values = new TreeMap<>();
  private final Map<String, InMemoryPreferences> children = new TreeMap<>();
  private final boolean userNode;

  /**
   * Creates a root node.
   *
   * @param userNode true if this is a user root, false if a system root
   */
  public InMemoryPreferences(boolean userNode) {
    super(null, "");
    this.userNode = userNode;
  }

  private InMemoryPreferences(InMemoryPreferences parent, String name) {
    super(parent, name);
    this.userNode = parent.userNode;
  }

  @Override
  public boolean isUserNode() {
    return userNode;
  }

  @Override
  protected void putSpi(String key, String value) {
    values.put(key, value);
  }

  @Override
  protected String getSpi(String key) {
    return values.get(key);
  }

  @Override
  protected void removeSpi(String key) {
    values.remove(key);
  }

  @Override
  protected void removeNodeSpi() throws BackingStoreException {
    values.clear();
    children.clear();
    if (parent() instanceof InMemoryPreferences parent) {
      parent.children.remove(name());
    }
  }

  @Override
  protected String[] keysSpi() throws BackingStoreException {
    return values.keySet().toArray(new String[0]);
  }

  @Override
  protected String[] childrenNamesSpi() throws BackingStoreException {
    return children.keySet().toArray(new String[0]);
  }

  @Override
  protected AbstractPreferences childSpi(String name) {
    InMemoryPreferences child = children.get(name);
    if (child == null) {
      child = new InMemoryPreferences(this, name);
      child.newNode = true;   // let AbstractPreferences fire node-added events
      children.put(name, child);
    }
    return child;
  }

  @Override
  protected void syncSpi() throws BackingStoreException {
    // nothing to do, everything is in memory
  }

  @Override
  protected void flushSpi() throws BackingStoreException {
    // nothing to do, everything is in memory
  }
}
