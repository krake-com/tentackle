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

package org.tentackle.maven.plugin.wizard.browser;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Persistent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for data nodes from PDOs.
 */
public class DataNodeFactory {

  /**
   * Creates the data object with its subnodes.
   *
   * @param pdo the PDO
   * @param withComponents true if with all components
   * @return the PDO's data, never null
   */
  public static DataObject create(PersistentDomainObject<?> pdo, boolean withComponents) {
    if (withComponents) {
      pdo.loadComponents(false);
    }
    return getDataObject(pdo, null, withComponents);
  }

  /**
   * Returns the GUI provider for a given PDO.
   *
   * @param pdo the PDO
   * @return the GUI provider, null if no provider available
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static GuiProvider<?> getGuiProvider(PersistentDomainObject<?> pdo) {
    GuiProvider<?> guiProvider = null;
    String typeName = pdo.getEffectiveClass().getSimpleName();
    Boolean exists = guiProviderInfoMap.get(typeName);
    if (exists == null) {
      try {
        guiProvider = GuiProviderFactory.getInstance().createGuiProvider((PersistentDomainObject) pdo);
        guiProviderInfoMap.put(typeName, Boolean.TRUE);
      }
      catch (PdoRuntimeException px) {
        guiProviderInfoMap.put(typeName, Boolean.FALSE);
      }
    }
    else if (exists) {
      guiProvider = GuiProviderFactory.getInstance().createGuiProvider((PersistentDomainObject) pdo);
    }
    return guiProvider;
  }


  // --- all private static below ---

  private static final Logger LOGGER = LoggerFactory.getLogger(DataNodeFactory.class);

  private static final String PO_SUFFIX = "PersistenceObject";

  private static final Map<String, Boolean> guiProviderInfoMap = new HashMap<>();   // map whether GUI provides exists

  /**
   * Collected infos for attributes and components of a PDO class.
   */
  private static class ClassInfo {

    private final Class<? extends PersistentDomainObject<?>> pdoClass;
    private final String typeName;
    private final Set<MethodInfo> methodInfos;

    private ClassInfo(Class<? extends PersistentDomainObject<?>> pdoClass) {
      this.pdoClass = pdoClass;
      typeName = toTypeName(pdoClass);
      methodInfos = new TreeSet<>();      // sorted by name
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ClassInfo classInfo = (ClassInfo) o;
      return pdoClass.equals(classInfo.pdoClass);
    }

    @Override
    public int hashCode() {
      return pdoClass.hashCode();
    }
  }

  /**
   * Method info for getters of attributes or composite relations.
   */
  private static class MethodInfo implements Comparable<MethodInfo> {     // sorted by name

    private final String name;
    private final Method getter;
    private final String typeName;
    private final boolean isComponent;
    private final boolean isList;

    private MethodInfo(String name, Method getter, Class<?> type, boolean isComponent, boolean isList) {
      this.name = name;
      this.getter = getter;
      this.isComponent = isComponent;
      this.isList = isList;
      this.typeName = toTypeName(type);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MethodInfo that = (MethodInfo) o;
      return name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public int compareTo(MethodInfo o) {
      return name.compareTo(o.name);
    }
  }

  /**
   * Map of PDO-classes to class infos.
   */
  private static final Map<Class<? extends PersistentDomainObject<?>>, ClassInfo> classMap = new ConcurrentHashMap<>();


  @SuppressWarnings("unchecked")
  private static DataObject getDataObject(PersistentDomainObject<?> pdo, String name, boolean withComponents) {

    ClassInfo classInfo = classMap.computeIfAbsent(pdo.getEffectiveClass(), DataNodeFactory::createClassInfo);

    String type = classInfo.typeName;
    long id = pdo.getId();
    long serial = pdo.getSerial();
    boolean rootEntity = pdo.isRootEntity();

    List<DataNode> nodes = new ArrayList<>();
    for (MethodInfo methodInfo : classInfo.methodInfos) {
      try {
        Object value = methodInfo.getter.invoke(pdo);
        if (methodInfo.isComponent) {
          if (withComponents) {
            if (methodInfo.isList) {
              List<DataObject> dataObjects = new ArrayList<>();
              List<? extends PersistentDomainObject<?>> components = (List<? extends PersistentDomainObject<?>>) value;
              if (components != null) {       // should never be null, but one never knows...
                for (PersistentDomainObject<?> component : components) {
                  dataObjects.add(createDataObject(methodInfo.typeName, null, component));
                }
              }
              nodes.add(new DataList(methodInfo.typeName, methodInfo.name, dataObjects));
            }
            else {
              nodes.add(createDataObject(methodInfo.typeName, methodInfo.name, (PersistentDomainObject<?>) value));
            }
          }
        }
        else {
          String stringValue = value == null ? null : value.toString();
          nodes.add(new DataItem(methodInfo.typeName, methodInfo.name, stringValue));
        }
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOGGER.warning("cannot determine method return value", e);
      }
    }

    return new DataObject(type, name, id, serial, rootEntity, getGuiProvider(pdo), nodes);
  }

  private static DataObject createDataObject(String type, String name, PersistentDomainObject<?> value) {
    DataObject dataObject;
    if (value == null) {
      dataObject = new DataObject(type, name, 0, 0, false, null, null);
    }
    else {
      dataObject = getDataObject(value, name, true);
    }
    return dataObject;
  }

  private static ClassInfo createClassInfo(Class<? extends PersistentDomainObject<?>> pdoClass) {
    ClassInfo classInfo = new ClassInfo(pdoClass);
    for (Method method : pdoClass.getMethods()) {
      if (method.getParameterCount() == 0 && !method.isBridge()) {        // skip bridge methods such as List<Blah> implemented by TrackedList<Blah>
        String name = method.getName();
        Class<?> type = method.getReturnType();
        boolean isGetter = name.startsWith("get") && Character.isUpperCase(name.charAt(3));
        if (isGetter) {
          name = StringHelper.firstToLower(name.substring(3));
        }
        else if (name.startsWith("is") && Character.isUpperCase(name.charAt(2))
                 && (type == Boolean.class || type == Boolean.TYPE)) {
          name = StringHelper.firstToLower(name.substring(2));
          isGetter = true;
        }
        if (isGetter) {
          Persistent annotation = method.getAnnotation(Persistent.class);
          if (annotation != null && annotation.component()) {
            boolean isComponent = false;
            boolean isList = false;
            if (PersistentDomainObject.class.isAssignableFrom(type)) {
              isComponent = true;
            }
            else if (List.class.isAssignableFrom(type)) {
              Type genType = method.getGenericReturnType();
              if (genType instanceof ParameterizedType) {
                Type[] typeArguments = ((ParameterizedType) genType).getActualTypeArguments();
                if (typeArguments != null && typeArguments.length == 1) {
                  Type typeArg = typeArguments[0];
                  if (typeArg instanceof Class<?> clz) {
                    if (PersistentDomainObject.class.isAssignableFrom(clz)) {
                      type = clz;
                      isComponent = true;
                      isList = true;
                    }
                  }
                }
              }
            }
            classInfo.methodInfos.add(new MethodInfo(name, method, type, isComponent, isList));
          }
        }
      }
    }
    return classInfo;
  }

  private static String toTypeName(Class<?> type) {
    String typeName = type.getSimpleName();
    if (PersistentDomainObject.class.isAssignableFrom(type) && typeName.endsWith(PO_SUFFIX)) {
      typeName = typeName.substring(0, typeName.length() - PO_SUFFIX.length());
    }
    return typeName;
  }

}
