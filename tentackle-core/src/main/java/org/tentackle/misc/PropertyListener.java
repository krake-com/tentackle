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

package org.tentackle.misc;

import java.util.EventListener;

/**
 * Listener to track property modifications.
 * <p>
 * Replacement for {@code java.beans.PropertyChangeListener}, which belongs to the {@code java.desktop} module
 * since Java 9.
 *
 * @author harald
 */
@FunctionalInterface
public interface PropertyListener extends EventListener {

  /**
   * Invoked when a property is about to change.
   * <p>
   * The method gets invoked <em>before</em> the property is actually changed,
   * i.e. throwing a {@code RuntimeException} can be used as a veto.
   *
   * @param ev the property change event
   */
  void changed(PropertyEvent ev);

}
