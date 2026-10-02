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

/**
 * A modification listener adapter.<br>
 * Provides the default priority 100, no time frame and no time delay.
 *
 * @author harald
 */
public abstract class ModificationListenerAdapter implements ModificationListener {

  private static final int DEFAULT_PRIORITY = 100;


  private final String[] names;         // the modification names

  /**
   * Creates a listener for the given modification names.
   *
   * @param names the names, null if any modification
   */
  public ModificationListenerAdapter(String... names) {
    this.names = names;
  }

  /**
   * Creates a listener for any modification.
   */
  public ModificationListenerAdapter() {
    names = null;
  }

  @Override
  public String[] getNames() {
    return names;
  }

  @Override
  public int getPriority() {
    return DEFAULT_PRIORITY;
  }

  @Override
  public long getTimeFrame() {
    return 0;
  }

  @Override
  public long getTimeDelay() {
    return 0;
  }

}
