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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalGetter;

import java.io.Serial;
import java.util.Properties;

/**
 * Properties with defaults for TRIP.
 * <p>
 * {@link Properties} keeps its defaults in a field declared in {@code java.util}, which TRIP
 * cannot serialize (see {@code MapTripType#isFieldSerializable}). A {@code Properties} therefore
 * arrives with its own entries only, silently losing everything it inherited from its defaults.
 * <p>
 * This class exposes the chain through a {@link CanonicalGetter} and feeds it back through a
 * {@link CanonicalConstructor}, so that TRIP transports the whole chain. Use it instead of
 * {@link Properties} whenever the defaults must survive the wire.
 * <p>
 * Notice that the entire chain must consist of {@code ChainedProperties}, since {@link #getDefaults()}
 * casts. The chain must not be cyclic, of course. Both is guaranteed by the constructors below,
 * unless a subclass invokes {@link Properties#Properties(Properties)} on its own.
 */
public class ChainedProperties extends Properties {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates chained properties with the given defaults.
   *
   * @param defaults the defaults, null if none
   */
  @CanonicalConstructor
  public ChainedProperties(ChainedProperties defaults) {
    super(defaults);
  }

  /**
   * Creates chained properties without defaults.
   */
  public ChainedProperties() {
    super();
  }

  /**
   * Gets the defaults.
   *
   * @return the defaults, null if none
   */
  @CanonicalGetter(ordinal = 0)
  public ChainedProperties getDefaults() {
    return (ChainedProperties) defaults;
  }

}
