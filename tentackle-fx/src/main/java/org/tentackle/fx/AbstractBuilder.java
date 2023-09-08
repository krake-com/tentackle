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

package org.tentackle.fx;

import org.tentackle.reflect.PropertyMap;
import org.tentackle.reflect.PropertyMapping;

import javafx.scene.control.Label;
import javafx.util.Builder;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Base implementation of a builder usable by the {@link javafx.fxml.FXMLLoader}.
 *
 * @author harald
 * @param <T> the bean type
 */
public class AbstractBuilder<T> extends PropertyMap<T> implements Builder<T> {

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

  @Override
  public Object get(Object key) {
    Object object = super.get(key);
    /*
     * FXML-Loader treats inner elements as readonly and does not allow null-values.
     * This hack allows a builder to be used in such cases.
     */
    if (object == null && innerElements != null && innerElements.contains(key)) {
      object = DUMMY_LABEL;
    }
    return object;
  }

  @Override
  public T build() {
    T bean = getBean();
    Configurator<T> configurator = FxFactory.getInstance().getConfigurator(getBeanType());
    if (configurator != null) {
      configurator.configure(bean);
    }
    return bean;
  }

  @Override
  protected PropertyMapping<T> createMapping(String name, Method getter, Method setter) {
    return new FxPropertyMapping<>(name, getter, setter);
  }
}
