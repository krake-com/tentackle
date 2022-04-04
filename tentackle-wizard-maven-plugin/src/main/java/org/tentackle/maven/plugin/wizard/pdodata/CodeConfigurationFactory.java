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

import org.tentackle.model.Attribute;
import org.tentackle.model.Relation;
import org.tentackle.sql.datatypes.StringType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates the default configuration to control code generation.
 */
public class CodeConfigurationFactory {

  /**
   * Generates a config property map from a data object template.
   *
   * @param dataObjects the data objects
   * @return the Java code
   */
  public static Map<String, Boolean> createConfig(DataObject... dataObjects) {
    return new CodeConfigurationFactory(dataObjects).create();
  }


  private final DataObject[] dataObjects;

  private CodeConfigurationFactory(DataObject... dataObjects) {
    this.dataObjects = dataObjects;
  }

  private Map<String, Boolean> create() {
    Map<String, Boolean> configuration = new LinkedHashMap<>();
    String configPath = "";
    for (DataObject dataObject : dataObjects) {
      analyzeComponent(configuration, configPath, dataObject);
    }
    return configuration;
  }

  private void analyzeComponent(Map<String, Boolean> configuration, String configPath, DataObject component) {
    configPath = updatePath(configPath, component);
    if (component.getRelation() != null && component.getRelation().getStereotypes().contains(JavaCodeFactory.NOTEST_STEREOTYPE)) {
      configuration.put(configPath, Boolean.FALSE);
    }
    for (DataNode node : component.getNodes()) {
      analyzeNode(configuration, configPath, node);
    }
  }

  private void analyzeNode(Map<String, Boolean> configuration, String configPath, DataNode node) {
    if (node instanceof DataItem item) {
      analyzeItem(configuration, configPath, item);
    }
    else if (node instanceof DataObject component) {
      analyzeComponent(configuration, configPath, component);
    }
    else if (node instanceof DataList list) {
      analyzeList(configuration, configPath, list);
    }
  }

  private void analyzeItem(Map<String, Boolean> configuration, String configPath, DataItem item) {
    Attribute attribute = item.getAttribute();
    if (attribute != null) {
      configPath = updatePath(configPath, item);
      if (attribute.getOptions().getStereotypes().contains(JavaCodeFactory.NOTEST_STEREOTYPE)) {
        configuration.put(configPath, Boolean.FALSE);
      }
      else if (attribute.getDataType() instanceof StringType &&
               item.getOrgValue() instanceof String str && str.contains("\n")) {
        // multiline strings: allow turning off
        configuration.put(configPath, Boolean.TRUE);
      }
    }
  }

  private void analyzeList(Map<String, Boolean> configuration, String configPath, DataList list) {
    Relation relation = list.getRelation();
    if (relation != null) {
      configPath = updatePath(configPath, list);
      if (relation.getStereotypes().contains(JavaCodeFactory.NOTEST_STEREOTYPE)) {
        configuration.put(configPath, Boolean.FALSE);
      }
      for (DataObject object : list.getNodes()) {
        if (object.getNmRelation() != null) {
          configuration.put(configPath, Boolean.TRUE);    // allow NM relations to be skipped
        }
        analyzeComponent(configuration, configPath, object);
      }
    }
  }

  private String updatePath(String path, DataNode node) {
    String name = node.getName();
    if (name != null && !name.contains("[")) {    // not root and not list element
      if (!path.isEmpty()) {
        path += ".";
      }
      path += name;
    }
    node.setConfigurationPath(path);
    return path;
  }

}
