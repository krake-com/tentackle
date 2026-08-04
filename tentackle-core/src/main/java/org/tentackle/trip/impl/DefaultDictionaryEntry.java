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

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.DictionaryEntryComponent;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of a dictionary entry.
 */
public class DefaultDictionaryEntry implements DictionaryEntry {

  private final int ordinal;
  private final String className;
  private final DictionaryEntryComponent[] components;
  private final boolean isProxy;

  // not sent over the wire (see DictionaryEntryTripType, which always goes through the constructor)
  private final Class<?>[] proxyInterfaces;               // proxy interfaces, if proxy
  private final TripType<?> tripType;                     // matching trip-type
  private final TripComponent<?, ?>[] tripComponents;     // fast array lookup for the type's components


  /**
   * Creates an entry with components.
   *
   * @param ordinal    the ordinal, &lt; 0 for temporary
   * @param className  the serializable FQCN
   * @param isProxy    true if this is an entry for a dynamic proxy, false otherwise
   * @param components the names of the components
   * @see ReflectionHelper#getSerializableClassName(Class)
   */
  public DefaultDictionaryEntry(int ordinal, String className, boolean isProxy, DictionaryEntryComponent[] components) {
    this.ordinal = ordinal;
    this.components = Objects.requireNonNull(components);
    this.isProxy = isProxy;

    if (this.isProxy) {
      proxyInterfaces = new Class<?>[this.components.length];
      for (int i = 0; i < this.components.length; i++) {
        try {
          proxyInterfaces[i] = Class.forName(this.components[i].getComponentName());
        }
        catch (ClassNotFoundException e) {
          throw new TripRuntimeException("cannot load interface for " + this.components[i], e);
        }
      }
      tripType = TripFactory.getInstance().getType(ReflectionHelper.createProxyClass(proxyInterfaces));
      // for proxies, the classname is always the local JVM's name
      this.className = ReflectionHelper.getSerializableClassName(tripType.getObjectClass());
    }
    else {
      this.className = Objects.requireNonNull(className);
      proxyInterfaces = null;
      tripType = TripFactory.getInstance().getType(this.className);
    }

    // the indexes of the type's components may be different from the ordinals of the dictionary entry
    tripComponents = new TripComponent<?, ?>[this.components.length];
    Map<String, TripComponent<?, ?>> typeComponents = new HashMap<>();
    for (TripComponent<?, ?> typeComponent : tripType.getComponents()) {
      typeComponents.put(typeComponent.getName(), typeComponent);
    }
    for (int i = 0; i < this.components.length; i++) {
      tripComponents[i] = typeComponents.get(this.components[i].getComponentName());
    }
  }

  @Override
  public int getOrdinal() {
    return ordinal;
  }

  @Override
  public String getClassName() {
    return className;
  }

  @Override
  public DictionaryEntryComponent[] getComponents() {
    return components;
  }

  @Override
  public boolean isClientOnly() {
    return ordinal < 0;
  }

  @Override
  public boolean isProxy() {
    return isProxy;
  }

  @Override
  public Class<?>[] getProxyInterfaces() {
    return proxyInterfaces;
  }

  @Override
  public DictionaryEntryComponent getComponent(int ordinal) {
    if (ordinal < 0 || ordinal >= components.length) {
      throw new TripRuntimeException("component ordinal out of range: " + ordinal + " for dictionary entry " + this);
    }
    return components[ordinal];
  }

  @Override
  public TripType<?> getType() {
    return tripType;
  }

  @Override
  public TripComponent<?, ?> getTypeComponent(int ordinal) {
    // the ordinal comes from the stream: don't let a corrupted one escape as an AIOOBE
    if (ordinal < 0 || ordinal >= tripComponents.length) {
      throw new TripRuntimeException("component ordinal out of range: " + ordinal + " for dictionary entry " + this);
    }
    return tripComponents[ordinal];
  }

  @Override
  public String toString() {
    return className + "/" + ordinal + "[" + components.length + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    DefaultDictionaryEntry that = (DefaultDictionaryEntry) o;
    return ordinal == that.ordinal;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(ordinal);
  }

}
