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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Persistent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for data nodes from PDOs.
 */
public class DataNodeFactory {

  private static final Logger LOGGER = Logger.get(DataNodeFactory.class);

  /**
   * Creates the data object with its subnodes.
   *
   * @param pdo the PDO
   * @return the PDO's data, never null
   */
  public static DataObject create(PersistentDomainObject<?> pdo) {
    pdo.loadComponents(false);
    return getDataObject(pdo, null, null, null);
  }


  // --- all private static below ---

  private static final String PO_SUFFIX = "PersistenceObject";

  /**
   * Collected infos for attributes and components of a PDO class.
   */
  private static class ClassInfo {

    private final Class<? extends PersistentDomainObject<?>> pdoClass;
    private final String typeName;
    private final Entity entity;
    private final Set<MethodInfo> methodInfos;

    private ClassInfo(Class<? extends PersistentDomainObject<?>> pdoClass) {
      this.pdoClass = pdoClass;
      typeName = toTypeName(pdoClass);
      entity = lookupEntity(typeName);
      methodInfos = new TreeSet<>();      // sorted by name
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
              methodInfos.add(new MethodInfo(name, annotation.comment(), method, type, isComponent, isList));
            }
          }
        }
      }
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
    private final String comment;
    private final Method getter;
    private final String typeName;
    private final boolean isComponent;
    private final boolean isList;

    private MethodInfo(String name, String comment, Method getter, Class<?> type, boolean isComponent, boolean isList) {
      this.name = name;
      this.comment = comment;
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
      int rv = Boolean.compare(isComponent, o.isComponent);   // attributes first, then relations
      if (rv == 0) {
        rv = name.compareTo(o.name);
      }
      return rv;
    }

  }

  /**
   * Map of PDO-classes to class infos.
   */
  private static final Map<Class<? extends PersistentDomainObject<?>>, ClassInfo> classMap = new ConcurrentHashMap<>();


  @SuppressWarnings("unchecked")
  private static DataObject getDataObject(PersistentDomainObject<?> pdo, String name, String comment, PersistentDomainObject<?> parentPdo) {

    ClassInfo classInfo = classMap.computeIfAbsent(pdo.getEffectiveClass(), ClassInfo::new);
    if (comment == null || comment.equals(classInfo.entity.getName())) {
      String entityComment = classInfo.entity.getOptions().getComment();
      if (entityComment != null) {
        comment = entityComment;
      }
    }

    List<DataNode> nodes = new ArrayList<>();
    for (MethodInfo methodInfo : classInfo.methodInfos) {
      try {
        Object value = methodInfo.getter.invoke(pdo);
        if (methodInfo.isComponent) {
          if (methodInfo.isList) {
            List<DataObject> dataObjects = new ArrayList<>();
            List<? extends PersistentDomainObject<?>> components = (List<? extends PersistentDomainObject<?>>) value;
            if (components != null) {       // should never be null, but one never knows...
              int ndx = 0;
              for (PersistentDomainObject<?> component : components) {
                dataObjects.add(createDataObject(methodInfo.name + "[" + ndx++ + "]", methodInfo.comment, component, pdo));
              }
            }
            Relation relation = classInfo.entity.getRelation(methodInfo.name, true);
            nodes.add(new DataList(methodInfo.typeName, methodInfo.name, methodInfo.comment, dataObjects, relation));
          }
          else {
            nodes.add(createDataObject(methodInfo.name, methodInfo.comment, (PersistentDomainObject<?>) value, pdo));
          }
        }
        else {
          if ("rootId".equals(methodInfo.name) && !pdo.isRootIdProvided() ||
              "rootClassId".equals(methodInfo.name) && !pdo.isRootClassIdProvided() ||
              "tableSerial".equals(methodInfo.name) && !pdo.isTableSerialProvided() ||
              "normText".equals(methodInfo.name) && !pdo.isNormTextProvided() ||
              "editedBy".equals(methodInfo.name) && !pdo.isTokenLockProvided() ||
              "editedSince".equals(methodInfo.name) && !pdo.isTokenLockProvided() ||
              "editedExpiry".equals(methodInfo.name) && !pdo.isTokenLockProvided()) {
            continue;
          }
          boolean idOfCompositeRelation = false;
          NonCompositeRelation nonCompositeRelation = null;
          if ("long".equals(methodInfo.typeName) || "Long".equals(methodInfo.typeName)) {
            // may be a 1:1 object relation
            for (Relation relation : classInfo.entity.getAllRelations()) {
              if (relation.getRelationType() == RelationType.OBJECT) {
                Attribute attribute = relation.getAttribute();
                if (attribute != null && attribute.getName().equals(methodInfo.name)) {
                  Relation foreignRelation = relation.getForeignRelation();
                  if (relation.isComposite() ||
                      foreignRelation != null && foreignRelation.isComposite()) {
                    idOfCompositeRelation = true;
                    break;
                  }
                  else {
                    String getterName = relation.getGetterName();
                    try {
                      Method getter = classInfo.pdoClass.getMethod(getterName);
                      Object getterValue = getter.invoke(pdo);
                      if (getterValue instanceof PersistentDomainObject<?> relatedPdo && relatedPdo.isUniqueDomainKeyProvided()) {
                        nonCompositeRelation = new NonCompositeRelation(relation, relatedPdo);
                        break;
                      }
                    }
                    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                      LOGGER.warning("cannot load 1:1 relation: " + getterName, e);
                    }
                  }
                }
              }
            }
          }
          Attribute attribute = classInfo.entity.getAttributeByJavaName(methodInfo.name, true);
          nodes.add(new DataItem(methodInfo.typeName, methodInfo.name, value, methodInfo.comment, attribute, idOfCompositeRelation, nonCompositeRelation));
        }
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOGGER.warning("cannot determine method return value", e);
      }
    }

    Relation objectRelation = null;
    NonCompositeRelation nmRelation = null;
    if (parentPdo != null) {
      ClassInfo parentInfo = classMap.get(parentPdo.getEffectiveClass());
      if (parentInfo != null) {   // must exist, but one never knows...
        String relationName;    // relation name w/o index [xxx]
        int ndx = name.indexOf('[');
        if (ndx >= 0) {
          relationName = name.substring(0, ndx);
        }
        else {
          relationName = name;
        }
        Relation relation = parentInfo.entity.getRelation(relationName, true);
        if (relation != null && relation.isComposite()) {
          objectRelation = relation;
          Relation nmRel = relation.getNmRelation();
          if (nmRel != null) {
            String getterName = nmRel.getGetterName();
            try {
              Method getter = classInfo.pdoClass.getMethod(getterName);
              Object getterValue = getter.invoke(pdo);
              if (getterValue instanceof PersistentDomainObject<?> relatedPdo && relatedPdo.isUniqueDomainKeyProvided()) {
                nmRelation = new NonCompositeRelation(relation, relatedPdo);
              }
            }
            catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
              LOGGER.warning("cannot load N:M relation: " + getterName, e);
            }
          }
        }
      }
    }

    return new DataObject(pdo, name, comment, nodes, objectRelation, nmRelation);
  }

  private static DataObject createDataObject(String name, String comment, PersistentDomainObject<?> pdo, PersistentDomainObject<?> parentPdo) {
    DataObject dataObject;
    if (pdo == null) {
      dataObject = new DataObject(pdo, name, null, null, null, null);
    }
    else {
      dataObject = getDataObject(pdo, name, comment, parentPdo);
    }
    return dataObject;
  }

  private static String toTypeName(Class<?> type) {
    String typeName = type.getSimpleName();
    if (PersistentDomainObject.class.isAssignableFrom(type) && typeName.endsWith(PO_SUFFIX)) {
      typeName = typeName.substring(0, typeName.length() - PO_SUFFIX.length());
    }
    return typeName;
  }

  private static Entity lookupEntity(String typeName) {
    Entity entity = null;
    try {
      entity = Model.getInstance().getByEntityName(typeName);
    }
    catch (ModelException e) {
      LOGGER.severe("cannot determine entity for " + typeName, e);
    }
    return entity;
  }

}
