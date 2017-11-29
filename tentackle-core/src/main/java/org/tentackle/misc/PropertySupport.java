/*
 * Tentackle - http://www.tentackle.org
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replacement for {@code java.beans.PropertyChangeSupport} as this becomes part of the Swing desktop module
 * in Java 9.
 *
 * @author harald
 */
public class PropertySupport {

  private final Object source;                                    // the event source (the PDO, usually)
  private final Map<String,List<PropertyListener>> listenerMap;   // map of property names to listeners
  private List<PropertyListener> allPropsListeners;               // fast reference to listeners for all properties


  /**
   * Creates the property support for a given object.
   *
   * @param source the event source (the object holding the properties)
   */
  public PropertySupport(Object source) {
    this.source = source;
    listenerMap = new HashMap<>();
  }


  /**
   * Adds a property listener that gets invoked if any of the properties change.
   *
   * @param l the listener
   */
  public void addPropertyListener(PropertyListener l) {
    addListener("", l);
  }

  /**
   * Adds a property listener that gets invoked if the given property changes.
   *
   * @param propertyName the name of the property
   * @param l the listener
   */
  public void addPropertyListener(String propertyName, PropertyListener l) {
    if (propertyName == null) {
      propertyName = "";
    }
    addListener(propertyName, l);
  }

  /**
   * Removes a property listener that gets invoked if any of the properties change.
   *
   * @param l the listener
   */
  public void removePropertyListener(PropertyListener l) {
    removeListener("", l);
  }

  /**
   * Removes a property listener that gets invoked if the given property changes.
   *
   * @param propertyName the name of the property
   * @param l the listener
   */
  public void removePropertyListener(String propertyName, PropertyListener l) {
    if (propertyName == null) {
      propertyName = "";
    }
    removeListener(propertyName, l);
  }


  /**
   * Fires property listeners.
   * <p>
   * The method must be invoked <em>before</em> the property is actually changed,
   * i.e. throwing a {@code RuntimeException} in {@link PropertyListener#changed}
   * can be used as a veto.
   *
   * @param propertyName the property name
   * @param oldValue the old value
   * @param newValue the new value
   */
  public void firePropertyChanged(String propertyName, Object oldValue, Object newValue) {
    if (propertyName == null || propertyName.isEmpty()) {
      // avoids NPEs and firing listeners twice if propertyName == ""
      throw new IllegalArgumentException("missing property name");
    }

    List<PropertyListener> listeners = listenerMap.get(propertyName);

    if (listeners != null && !listeners.isEmpty() ||
        allPropsListeners != null && !allPropsListeners.isEmpty()) {

      PropertyEvent event = new PropertyEvent(source, propertyName, oldValue, newValue);

      if (listeners != null) {
        for (PropertyListener l: listeners) {
          l.changed(event);
        }
      }

      if (allPropsListeners != null) {
        for (PropertyListener l: allPropsListeners) {
          l.changed(event);
        }
      }
    }
  }



  private void addListener(String propertyName, PropertyListener l) {
    List<PropertyListener> listeners = listenerMap.get(propertyName);
    if (listeners == null) {
      listeners = new ArrayList<>();
      listenerMap.put(propertyName, listeners);
      if (propertyName.isEmpty()) {
        allPropsListeners = listeners;
      }
    }
    listeners.add(l);
  }

  private void removeListener(String propertyName, PropertyListener l) {
    List<PropertyListener> listeners = listenerMap.get(propertyName);
    if (listeners != null) {
      listeners.remove(l);
    }
  }

}
