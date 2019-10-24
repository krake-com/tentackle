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

package org.tentackle.maven;

import org.apache.maven.plugin.logging.Log;

import org.tentackle.common.StringHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The freemarker template model.
 */
public class TemplateModel extends HashMap<String, Object> {

  private final Log logger;   // the maven logger

  /**
   * Creates the template model.
   *
   * @param logger the maven logger
   */
  public TemplateModel(Log logger) {
    this.logger = logger;
  }

  /**
   * Adds the properties to the model.<br>
   * Keys are translated to camel case.
   *
   * @param properties the properties
   */
  public void addProperties(Properties properties) {
    for (Entry<Object, Object> entry : properties.entrySet()) {
      putValue(StringHelper.toCamelCase(entry.getKey().toString()), entry.getValue());
    }
  }

  /**
   * Adds a map to the model.<br>
   * Keys are translated to camel case.
   *
   * @param map the properties
   */
  public void addMap(Map<String, Object> map) {
    for (Entry<String, Object> entry : map.entrySet()) {
      putValue(StringHelper.toCamelCase(entry.getKey()), entry.getValue());
    }
  }

  /**
   * Adds a key-value pair to the model.<br>
   * The value is converted to a string.<br>
   * {@code null} is mapped to the empty string.<br>
   *
   * @param key the key
   * @param value the value
   */
  public void putValue(String key, Object value) {
    String val = value == null ? "" : value.toString();
    put(key, val);
    logger.debug("template variable " + key + " = \"" + val + "\"");
  }
}
