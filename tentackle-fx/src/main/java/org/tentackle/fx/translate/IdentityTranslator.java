/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.translate;

import org.tentackle.fx.FxComponent;

import java.util.function.Function;

/**
 * The identity translator base class.<br>
 * Model and view share the same type, and the values are passed unchanged.
 *
 * @author harald
 * @param <T> the type
 */
public abstract class IdentityTranslator<T> extends AbstractValueTranslator<T,T> {

  /**
   * Creates an identity translator.
   *
   * @param component the component
   */
  public IdentityTranslator(FxComponent component) {
    super(component);
  }

  @Override
  public Function<T, T> toViewFunction() {
    return v -> v;
  }

  @Override
  public Function<T, T> toModelFunction() {
    return v -> v;
  }

}
