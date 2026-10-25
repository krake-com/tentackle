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

package org.tentackle.fx.nofxml;

import org.tentackle.common.Service;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.log.Logger;

import javafx.scene.Node;
import javafx.scene.Parent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The default implementation of a {@link NoFXMLLinker}.<br>
 * Injects the nodes, resources, invokes initialize() and sets the view into the controller.
 * <p>
 * Notice that the old {@link javafx.fxml.Initializable} interface is not supported
 * and the initialize() method must be annotated with {@link javafx.fxml.FXML} to be invoked, even if public.
 */
@Service(NoFXMLLinker.class)
public class DefaultNoFXMLLinker implements NoFXMLLinker {

  private static final Logger LOGGER = Logger.get(DefaultNoFXMLLinker.class);

  private static final String INITIALIZE_METHOD_NAME = "initialize";

  /**
   * Creates a linker.
   */
  public DefaultNoFXMLLinker() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void link(Parent view, FxController controller, ResourceBundle resources) {

    Map<String, Node> nodeMap = new HashMap<>();    // id:node

    // extract the nodes from the view and optionally inject the resources
    for (Field noFXMLField : view.getClass().getDeclaredFields()) {
      if (noFXMLField.isAnnotationPresent(NoFXML.class)) {
        try {
          Object value = getField(noFXMLField, view);
          if (value instanceof Node node) {
            if (node.getId() == null) {   // if not set programmatically
              node.setId(noFXMLField.getName());  // use declared name as id
            }
            Node dupNode = nodeMap.putIfAbsent(node.getId(), node);
            if (dupNode != null) {
              throw new FxRuntimeException("node id '" + node.getId() + "' declared more than once");
            }
          }
          else if (ResourceBundle.class.isAssignableFrom(noFXMLField.getType())) {
            assertResourcesValid(resources, noFXMLField);
            setField(noFXMLField, view, resources);
          }
          else {
            if (Node.class.isAssignableFrom(noFXMLField.getType())) {
              throw new FxRuntimeException("node is null: " + noFXMLField);
            }
            LOGGER.warning("unsupported field annotated with @NoFXML: {0} -> ignored", noFXMLField);
          }
        }
        catch (IllegalAccessException | IllegalArgumentException ex) {
          throw new FxRuntimeException("cannot access @NoFXML field " + noFXMLField, ex);
        }
      }
    }

    List<Field> unboundFxmlFields = new ArrayList<>();

    // inject nodes and optional resources into the controller
    for (Field fxmlField : controller.getFXMLFields()) {
      Object value;
      if (ResourceBundle.class.isAssignableFrom(fxmlField.getType())) {
        assertResourcesValid(resources, fxmlField);
        value = resources;
      }
      else {
        value = nodeMap.get(fxmlField.getName());
      }
      if (value != null) {
        try {
          setField(fxmlField, controller, value);
        }
        catch (IllegalAccessException | IllegalArgumentException ex) {
          throw new FxRuntimeException("cannot set controller variable " + fxmlField, ex);
        }
      }
      else {
        unboundFxmlFields.add(fxmlField);
      }
    }

    if (!unboundFxmlFields.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      for (Field unboundFxmlField : unboundFxmlFields) {
        if (!buf.isEmpty()) {
          buf.append(", ");
        }
        buf.append(unboundFxmlField.getName());
      }
      // unbound FXML fields are not allowed (while not all nodes in the view need to be bound to the controller!)
      throw new FxRuntimeException("unbound @FXML fields in " + controller.getClass().getName() + ": " + buf);
    }

    invokeInitialize(controller.getFXMLMethods(), controller);
    invokeInitialize(findNoFXMLMethods(view.getClass()), view);

    controller.setView(view);
  }


  private Object getField(Field field, Object object) throws IllegalAccessException {
    try {
      return field.get(object);
    }
    catch (IllegalAccessException ix) {
      field.setAccessible(true);
      return field.get(object);
    }
  }

  private void setField(Field field, Object object, Object value) throws IllegalAccessException {
    if (Modifier.isFinal(field.getModifiers())) {
      // never mutate final fields reflectively (JEP-500)
      throw new FxRuntimeException("cannot inject into final field " + field);
    }
    try {
      field.set(object, value);
    }
    catch (IllegalAccessException ix) {
      field.setAccessible(true);
      field.set(object, value);
    }
  }

  private void invokeMethod(Method method, Object object) throws InvocationTargetException, IllegalAccessException {
    try {
      method.invoke(object);
    }
    catch (IllegalAccessException ix) {
      method.setAccessible(true);
      method.invoke(object);
    }
  }

  private Collection<Method> findNoFXMLMethods(Class<?> clazz) {
    Collection<Method> noFXMLMethods = new ArrayList<>();
    for (Method method : clazz.getDeclaredMethods()) {
      if (method.isAnnotationPresent(NoFXML.class)) {
        noFXMLMethods.add(method);
      }
    }
    return noFXMLMethods;
  }

  private void invokeInitialize(Collection<Method> methods, Object object) {
    for (Method method : methods) {
      if (method.getParameterCount() == 0 && method.getReturnType() == Void.TYPE &&
          !Modifier.isStatic(method.getModifiers()) &&
          INITIALIZE_METHOD_NAME.equals(method.getName())) {
        try {
          invokeMethod(method, object);
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          throw new FxRuntimeException("cannot invoke " + method, e);
        }
      }
      else {
        LOGGER.warning("unsupported method annotated with @NoFXML: {0} -> ignored", method);
      }
    }
  }

  private void assertResourcesValid(ResourceBundle resources, Field field) {
    if (resources == null) {
      throw new FxRuntimeException("cannot inject resources to " + field + " because resources are null");
    }
  }

}
