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
import org.tentackle.misc.Convertible;
import org.tentackle.misc.Holder;
import org.tentackle.misc.ObjectUtilities;
import org.tentackle.model.Attribute;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Factory generating Java code that creates PDOs.
 */
public class JavaCodeFactory {

  /**
   * Stereotype for attributes and relations to skip test code generation.
   */
  public static final String NOTEST_STEREOTYPE = "NOTEST";

  private static final String HIDDEN_COMMENT = "// hidden: ";


  /**
   * By default, the variables of the created entities are reused for the
   * 2nd, 3rd, etc... instances. If this flag is true, the variable names
   * are numbered starting at {@link #variableOffset}.
   */
  public static boolean distinctVariables;

  /**
   * If {@link #distinctVariables} is true, the variable names are numbered.
   * If {@code variableOffset} is null, the first instance has no number.
   * Otherwise, it starts with the given offset.
   */
  public static Integer variableOffset;



  /**
   * Generates code from a data object template.
   *
   * @param codePaths optional code paths configuration map
   * @param dataObjects the data objects
   * @return the Java code
   */
  public static String generateCode(Map<String, Boolean> codePaths, DataObject... dataObjects) {
    return new JavaCodeFactory(codePaths, dataObjects).generate();
  }



  private final Map<String, Boolean> codePaths;
  private final DataObject[] dataObjects;
  private final Map<String, Holder<Integer>> nameMap;

  private JavaCodeFactory(Map<String, Boolean> codePaths, DataObject... dataObjects) {
    this.codePaths = codePaths;
    this.dataObjects = dataObjects;
    nameMap = new HashMap<>();
  }

  /**
   * Generates the Java code.
   *
   * @return the code
   */
  public String generate() {
    StringBuilder buf = new StringBuilder();
    for (DataObject dataObject : dataObjects) {
      if (!buf.isEmpty()) {
        buf.append('\n');
      }
      String varName = createVarName(dataObject.getType());
      buf.append(createComponentCode(varName, dataObject));
      if (dataObject.getPdo().isRootEntity()) {
        if (distinctVariables) {
          buf.append(varName).append(" = ").append(varName).append(".persist();\n");
        }
        else {
          buf.append(varName).append(".save();\n");
        }
      }
    }
    return buf.toString();
  }


  private Holder<Integer> getVariableCount(String varName) {
    return nameMap.computeIfAbsent(varName, _ -> new Holder<>(variableOffset == null ? 0 : variableOffset));
  }

  private String createVarName(String type) {
    String varName = StringHelper.firstToLower(type);
    Holder<Integer> holder = getVariableCount(varName);
    int count = holder.get();
    if (distinctVariables && (count > 0 || variableOffset != null)) {
      varName += count;
    }
    holder.accept(count + 1);
    return varName;
  }

  private String createNodeCode(String varName, DataNode node) {
    String code;
    if (node instanceof DataItem item) {
      code = createItemCode(varName, item);
    }
    else if (node instanceof DataObject component) {
      StringBuilder buf = new StringBuilder();
      Relation relation = component.getRelation();
      if (relation == null || isGenerating(!relation.getStereotypes().contains(NOTEST_STEREOTYPE), node)) {
        String componentName = createVarName(component.getType());
        buf.append(createComponentCode(componentName, component));
        if (relation != null) {
          buf.append(varName).append('.').append(relation.getSetterName()).append('(').append(componentName).append(");\n");
        }
      }
      code = buf.toString();
    }
    else if (node instanceof DataList list) {
      code = createListCode(varName, list);
    }
    else {
      code = "";    // will not happen, but...
    }
    return code;
  }

  private String createItemCode(String varName, DataItem item) {
    StringBuilder buf = new StringBuilder();
    Attribute attribute = item.getAttribute();
    if (!item.isIdOfCompositeRelation() && attribute != null &&
        !attribute.isImplicit() &&
        isGenerating(!attribute.getOptions().getStereotypes().contains(NOTEST_STEREOTYPE), item) &&
        !item.isCounterForListRelation()) {
      if (item.isHidden()) {
        buf.append(HIDDEN_COMMENT);
      }
      Object orgValue = item.getOrgValue();
      NonCompositeRelation nonCompositeRelation = item.getNonCompositeRelation();
      if (nonCompositeRelation != null) {
        Relation relation = nonCompositeRelation.getRelation();
        buf.append(varName).append('.').append(relation.getSetterName()).append("(Objects.requireNonNull(on(")
           .append(relation.getForeignEntity()).append(".class).selectByUniqueDomainKey(")
           .append(extractUdkLiteral(nonCompositeRelation.getRelatedPdo())).append(")));")
           .append(toIdComment(nonCompositeRelation.getRelatedPdo())).append('\n');
      }
      else if (isSettingNecessary(orgValue, attribute)) {
        if (orgValue != null) {
          String str;
          DataType<?> dataType = attribute.getDataType();
          if (attribute.isConvertible()) {
            if (orgValue.getClass().isEnum()) {
              str = orgValue.getClass().getSimpleName() + "." + ((Enum<?>) orgValue).name();
            }
            else {
              try {
                dataType = attribute.getInnerDataType();
                str = orgValue.getClass().getSimpleName() + ".toInternal(" + dataType.valueOfLiteralToCode(((Convertible<?>) orgValue).toExternal().toString(), null) + ")";
              }
              catch (ModelException e) {
                str = dataType.valueOfLiteralToCode(orgValue.toString(), null);
              }
            }
          }
          else {
            str = dataType.valueOfLiteralToCode(item.getValue(), null);
          }
          buf.append(varName).append('.').append(attribute.getSetterName()).append("(").append(str).append(");\n");
        }
        else {
          // null is not the default: set it explicitly
          buf.append(varName).append('.').append(attribute.getSetterName()).append("(null);\n");
        }
      }
    }
    return buf.toString();
  }

  private String createComponentCode(String varName, DataObject component) {
    StringBuilder buf = new StringBuilder();
    List<DataNode> nodes = component.getNodes();
    if (nodes != null) {
      if (component.isHidden()) {
        buf.append(HIDDEN_COMMENT);
      }
      if (distinctVariables || getVariableCount(varName).get() == 1) {
        buf.append(component.getType()).append(' ');
      }
      buf.append(varName).append(" = on(").append(component.getType()).append(".class);")
         .append(toIdComment(component.getPdo())).append('\n');
      for (DataNode node : nodes) {
        buf.append(createNodeCode(varName, node));
      }
    }
    return buf.toString();
  }

  private String createListCode(String varName, DataList list) {
    StringBuilder buf = new StringBuilder();
    if (list.getRelation() != null && isGenerating(!list.getRelation().getStereotypes().contains(NOTEST_STEREOTYPE), list)) {
      List<DataObject> nodes = list.getNodes();
      if (nodes != null) {
        for (DataObject object : list.getNodes()) {
          String objectName = createVarName(object.getType());
          NonCompositeRelation listRelation = object.getNmRelation();
          if (listRelation != null && isGenerating(true, list)) {
            Relation relation = listRelation.getRelation();
            Relation nmRelation = relation.getNmRelation();
            buf.append(varName).append(".get").append(relation.getNmMethodName()).append("().add(Objects.requireNonNull(on(")
               .append(nmRelation.getForeignEntity()).append(".class).selectByUniqueDomainKey(")
               .append(extractUdkLiteral(listRelation.getRelatedPdo())).append(")));")
               .append(toIdComment(listRelation.getRelatedPdo())).append('\n');
          }
          else {
            buf.append(createComponentCode(objectName, object));
            if (list.isHidden()) {
              buf.append(HIDDEN_COMMENT);
            }
            buf.append(varName).append('.').append(list.getRelation().getGetterName())
               .append("().add(").append(objectName).append(");\n");
          }
        }
      }
    }
    return buf.toString();
  }

  private boolean isSettingNecessary(Object orgValue, Attribute attribute) {
    String nonNullDefault = attribute.getOptions().getInitialValue();
    if (nonNullDefault == null) {
      nonNullDefault = attribute.getOptions().getNewValue();
    }
    Object defaultValue = nonNullDefault == null ? null : attribute.getDataType().valueOf(nonNullDefault);

    if (defaultValue == null) {
      if (attribute.getDataType().isPrimitive()) {
        if (orgValue instanceof Number number) {
          return !ObjectUtilities.getInstance().isZero(number);
        }
        if (orgValue instanceof Boolean bool) {
          return bool;  // only if TRUE
        }
        if (orgValue instanceof Character c) {
          return c != 0;
        }
      }
    }

    return !Objects.equals(orgValue, defaultValue);
  }

  private String extractUdkLiteral(PersistentDomainObject<?> pdo) {
    Class<?> uniqueDomainKeyType = pdo.getUniqueDomainKeyType();
    if (pdo.getUniqueDomainKey() instanceof Convertible<?> convertible) {
      Object external = convertible.toExternal();
      DataType<?> externalType = DataTypeFactory.getInstance().get(external.getClass());
      return uniqueDomainKeyType.getSimpleName() + ".toInternal(" + externalType.valueOfLiteralToCode(external.toString(), null) + ")";
    }
    DataType<?> dataType = DataTypeFactory.getInstance().get(uniqueDomainKeyType);
    if (dataType != null) {
      return dataType.valueOfLiteralToCode(pdo.getUniqueDomainKey().toString(), null);
    }
    // composite UDK
    return uniqueDomainKeyType.getSimpleName() + ".valueOf(\"" + pdo.getUniqueDomainKey() + "\")";
  }

  private String toIdComment(PersistentDomainObject<?> pdo) {
    if (pdo != null && !pdo.isEmbedded()) {
      return "    // [" + pdo.getId() + "/" + pdo.getSerial() + "]";
    }
    return "";
  }

  private boolean isGenerating(boolean generating, DataNode node) {
    if (codePaths != null) {
      String configurationPath = node.getConfigurationPath();
      if (configurationPath != null) {
        Boolean toggle = codePaths.get(configurationPath);
        if (toggle != null) {
          generating = toggle;
        }
      }
    }
    return generating;
  }

}
