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

import java.io.Serial;
import java.util.EventObject;
import java.util.Objects;

/**
 * Event fired when a property is modified.
 * <p>
 * Replacement for {@code java.beans.PropertyChangeEvent}, which belongs to the {@code java.desktop} module
 * since Java 9.
 *
 * @author harald
 */
public class PropertyEvent extends EventObject {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String propertyName;    // name of the property
  @SuppressWarnings("serial")
  private final Object newValue;        // the new value
  @SuppressWarnings("serial")
  private final Object oldValue;        // the old value

  /**
   * Constructs a new {@code PropertyEvent}.
   *
   * @param source       the source of the event
   * @param propertyName the property name
   * @param oldValue     the old value of the property
   * @param newValue     the new value of the property
   * @throws IllegalArgumentException if {@code source} is {@code null}
   * @throws NullPointerException if {@code propertyName} is {@code null}
   */
  public PropertyEvent(Object source, String propertyName, Object oldValue, Object newValue) {
    super(source);
    this.propertyName = Objects.requireNonNull(propertyName, "propertyName");
    this.newValue = newValue;
    this.oldValue = oldValue;
  }

  /**
   * Gets the property name.
   *
   * @return the name
   */
  public String getPropertyName() {
    return propertyName;
  }

  /**
   * Gets the new value for the property.
   *
   * @return the new value
   */
  public Object getNewValue() {
    return newValue;
  }

  /**
   * Gets the old value for the property.
   *
   * @return the old value
   */
  public Object getOldValue() {
    return oldValue;
  }

}
