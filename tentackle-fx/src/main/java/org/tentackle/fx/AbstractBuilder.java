/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.fx;

import com.sun.javafx.fxml.BeanAdapter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javafx.scene.control.Label;
import javafx.util.Builder;

/**
 * Base implementation for a builder.
 *
 * @author harald
 * @param <T> the bean type
 */
public abstract class AbstractBuilder<T> extends BeanAdapter implements Builder<T> {

  /** some non-null value. */
  private static final Label DUMMY_LABEL = new Label();

  /** inner elements with initial null-values. */
  private final Set<String> innerElements;



  /**
   * Creates a builder.
   *
   * @param bean the bean
   */
  public AbstractBuilder(T bean) {
    super(bean);
    innerElements = null;
  }


  /**
   * Creates a builder with inner elements.
   *
   * @param bean the bean
   * @param innerElements name of inner elements
   */
  public AbstractBuilder(T bean, String... innerElements) {
    super(bean);
    this.innerElements = innerElements == null ? null : new HashSet<>(Arrays.asList(innerElements));
  }


  /**
   * Gets the bean.
   *
   * @return the bean
   */
  @Override
  @SuppressWarnings("unchecked")
  public T getBean() {
    return (T) super.getBean();
  }


  @Override
  public Object get(Object key) {
    /**
     * FXML-Loader treats inner elements as readonly and does not allow null-values.
     * This hack allows a builder to be used in such cases.
     */
    return innerElements != null && innerElements.contains((String) key) ? DUMMY_LABEL : super.get(key);
  }


  @Override
  @SuppressWarnings("unchecked")
  public T build() {
    T bean = getBean();
    Class<T> clazz = (Class<T>) bean.getClass();
    Configurator<T> configurator = FxFactory.getInstance().getConfigurator(clazz);
    if (configurator != null) {
      configurator.configure(bean);
    }
    return bean;
  }

}
