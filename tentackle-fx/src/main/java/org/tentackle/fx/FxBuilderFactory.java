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

import org.tentackle.common.ServiceFactory;

import javafx.fxml.JavaFXBuilderFactory;
import javafx.util.Builder;
import javafx.util.BuilderFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;


/**
 * The default implementation of a builder factory.
 * <p>
 * Returns the builders registered via {@link BuilderService} for the node classes they serve,
 * so that the FXML loader creates the tentackle implementations.
 * All other types are delegated to the standard {@link JavaFXBuilderFactory}.
 * <p>
 * Delegation is used because {@link JavaFXBuilderFactory} is final and its code
 * depends on internal <code>com.sun.xxx</code> classes, so it can neither be extended nor duplicated.
 *
 * @author harald
 */
public class FxBuilderFactory implements BuilderFactory {

  private final BuilderFactory defaultFactory;
  private final Map<Class<?>,Class<?>> builderMap;


  /**
   * Creates the factory.
   */
  public FxBuilderFactory() {
    defaultFactory = new JavaFXBuilderFactory();

    // pick up the class mapping for the builders
    Map<String,String> serviceMap = ServiceFactory.getServiceFinder().createNameMap(Builder.class.getName());
    builderMap = new HashMap<>();
    for (Map.Entry<String,String> entry: serviceMap.entrySet()) {
      try {
        Class<?> nodeClass = Class.forName(entry.getKey());
        Class<?> builderClass = Class.forName(entry.getValue());
        builderMap.put(nodeClass, builderClass);
      }
      catch (ClassNotFoundException ex) {
        throw new FxRuntimeException(ex);
      }
    }
  }



  @Override
  public Builder<?> getBuilder(Class<?> type) {

    Builder<?> builder;

    // map to special builders
    Class<?> builderClass = builderMap.get(type);
    if (builderClass != null) {
      try {
        /*
         * We always return a new builder instance, which is used only once
         * and not cached by FXMLLoader in any way.
         * This allows the builder to extend BeanAdapter and wrap a single node.
         */
        builder = (Builder<?>) builderClass.getDeclaredConstructor().newInstance();
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
        throw new FxRuntimeException("cannot create builder for " + type, ex);
      }
    }
    else {
      builder = defaultFactory.getBuilder(type);
    }

    return builder;
  }

}
