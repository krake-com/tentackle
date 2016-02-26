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

package org.tentackle.fx;

import java.util.Objects;

/**
 * The unique key for value translators.<br>
 *
 * @author harald
 * @param <M> the model's type
 * @param <V> the view's type
 */
public class ValueTranslatorKey<M,V> {

  private final Class<?> modelClass;
  private final Class<?> viewClass;

  /**
   * Creates a translator key.
   *
   * @param modelClass the model's type
   * @param viewClass the view's type
   */
  public ValueTranslatorKey(Class<?> modelClass, Class<?> viewClass) {
    this.modelClass = modelClass;
    this.viewClass = viewClass;
  }

  /**
   * Gets the model's class.
   *
   * @return the class
   */
  public Class<?> getModelClass() {
    return modelClass;
  }

  /**
   * Gets the view' class.
   *
   * @return the class
   */
  public Class<?> getViewClass() {
    return viewClass;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 71 * hash + Objects.hashCode(this.modelClass);
    hash = 71 * hash + Objects.hashCode(this.viewClass);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ValueTranslatorKey<?, ?> other = (ValueTranslatorKey<?, ?>) obj;
    if (!Objects.equals(this.modelClass, other.modelClass)) {
      return false;
    }
    return Objects.equals(this.viewClass, other.viewClass);
  }

}
