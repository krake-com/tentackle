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

package org.tentackle.maven.plugin.mcp.tool;

import io.helidon.extensions.mcp.server.McpParameters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The arguments of a tool invocation.
 * <p>
 * Wraps Helidon's {@code McpParameters}, whose constructor is not public, so that a tool is a plain function
 * that a test can call with a map.
 * <p>
 * Agents are inconsistent about list arguments: a single value often arrives as a bare string where the schema
 * asks for an array. Accepting that, and a comma-separated string, costs a few lines here and saves a failed
 * tool call every time.
 *
 * @author harald
 */
public final class ToolArguments {

  private final McpParameters parameters;
  private final Map<String, Object> values;

  private ToolArguments(McpParameters parameters, Map<String, Object> values) {
    this.parameters = parameters;
    this.values = values;
  }

  /**
   * Creates arguments backed by an MCP request.
   *
   * @param parameters the parameters
   * @return the arguments
   */
  public static ToolArguments of(McpParameters parameters) {
    return new ToolArguments(parameters, null);
  }

  /**
   * Creates arguments backed by a map.
   * <p>
   * Values may be strings, numbers, booleans, or lists thereof.
   *
   * @param values the values
   * @return the arguments
   */
  public static ToolArguments of(Map<String, Object> values) {
    return new ToolArguments(null, Map.copyOf(values));
  }

  /**
   * Gets a string argument.
   *
   * @param name the argument name
   * @param defaultValue the value used when absent or blank
   * @return the value
   */
  public String string(String name, String defaultValue) {
    String value = parameters == null
                   ? values.get(name) instanceof String string ? string : null
                   : parameters.get(name).asString().orElse(null);
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  /**
   * Gets a mandatory string argument.
   *
   * @param name the argument name
   * @return the value, never null
   * @throws IllegalArgumentException if absent or blank
   */
  public String requiredString(String name) {
    String value = string(name, null);
    if (value == null) {
      throw new IllegalArgumentException("the '" + name + "' argument is required");
    }
    return value;
  }

  /**
   * Gets a list argument.
   * <p>
   * Accepts a JSON array, a single string, or a comma-separated string.
   *
   * @param name the argument name
   * @param defaultValue the list used when absent or empty
   * @return the values, never null
   */
  public List<String> list(String name, List<String> defaultValue) {
    List<String> collected = new ArrayList<>();
    if (parameters == null) {
      switch (values.get(name)) {
        case null -> { }
        case List<?> list -> {
          for (Object item : list) {
            addSplit(collected, String.valueOf(item));
          }
        }
        case Object single -> addSplit(collected, String.valueOf(single));
      }
    }
    else {
      McpParameters parameter = parameters.get(name);
      List<McpParameters> items = parameter.asList().orElse(null);
      if (items != null) {
        for (McpParameters item : items) {
          item.asString().ifPresent(value -> addSplit(collected, value));
        }
      }
      else {
        parameter.asString().ifPresent(value -> addSplit(collected, value));
      }
    }
    return collected.isEmpty() ? defaultValue : List.copyOf(collected);
  }

  /**
   * Gets an integer argument.
   *
   * @param name the argument name
   * @param defaultValue the value used when absent
   * @return the value
   */
  public int integer(String name, int defaultValue) {
    if (parameters == null) {
      return values.get(name) instanceof Number number ? number.intValue() : defaultValue;
    }
    return parameters.get(name).asInteger().orElse(defaultValue);
  }

  /**
   * Gets a boolean argument.
   *
   * @param name the argument name
   * @param defaultValue the value used when absent
   * @return the value
   */
  public boolean bool(String name, boolean defaultValue) {
    if (parameters == null) {
      return values.get(name) instanceof Boolean flag ? flag : defaultValue;
    }
    return parameters.get(name).asBoolean().orElse(defaultValue);
  }

  private void addSplit(List<String> collected, String value) {
    for (String part : value.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        collected.add(trimmed);
      }
    }
  }

}
