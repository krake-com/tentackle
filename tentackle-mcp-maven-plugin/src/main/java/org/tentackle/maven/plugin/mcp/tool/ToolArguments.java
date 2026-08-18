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
import java.util.Locale;
import java.util.Map;

/**
 * The arguments of a tool invocation.
 * <p>
 * Wraps Helidon's {@code McpParameters}, whose constructor is not public, so that a tool is a plain function
 * that a test can call with a map.
 * <p>
 * Agents are inconsistent about the JSON type they send: a single value arrives as a bare string where the
 * schema asks for an array, a number as {@code "10"}, a boolean as {@code "true"}. Helidon's accessors throw on
 * every one of those, so each value is read as the text it stands for and interpreted here. Accepting that, and
 * a comma-separated string, costs a few lines and saves a failed tool call every time.
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
    String value = parameters == null ? scalarText(values.get(name)) : text(parameters.get(name));
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
            addSplit(collected, scalarText(item));
          }
        }
        case Object single -> addSplit(collected, scalarText(single));
      }
    }
    else {
      McpParameters parameter = parameters.get(name);
      List<McpParameters> items = items(parameter);
      if (items != null) {
        for (McpParameters item : items) {
          addSplit(collected, text(item));
        }
      }
      else {
        addSplit(collected, text(parameter));
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
      if (values.get(name) instanceof Number number) {
        return number.intValue();
      }
    }
    else {
      McpParameters parameter = parameters.get(name);
      if (parameter.isNumber()) {
        return parameter.asInteger().orElse(defaultValue);
      }
    }
    String value = string(name, null);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException nfx) {
      throw new IllegalArgumentException("the '" + name + "' argument is not a number: '" + value + '\'', nfx);
    }
  }

  /**
   * Gets a boolean argument.
   *
   * @param name the argument name
   * @param defaultValue the value used when absent
   * @return the value
   */
  public boolean bool(String name, boolean defaultValue) {
    String value = string(name, null);
    if (value == null) {
      return defaultValue;
    }
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "true", "yes", "1" -> true;
      case "false", "no", "0" -> false;
      default -> throw new IllegalArgumentException("the '" + name + "' argument is not a boolean: '" + value + '\'');
    };
  }

  private void addSplit(List<String> collected, String value) {
    if (value == null) {
      return;
    }
    for (String part : value.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        collected.add(trimmed);
      }
    }
  }

  /**
   * Gets the items of an MCP parameter, if it is an array.
   * <p>
   * Helidon offers no test for the JSON type of a node and throws when the accessor does not match it, so the
   * mismatch is what tells us that the caller sent a single value instead of an array.
   *
   * @param parameter the parameter
   * @return the items, null if the parameter is not an array
   */
  private static List<McpParameters> items(McpParameters parameter) {
    try {
      return parameter.asList().orElse(null);
    }
    catch (IllegalStateException isx) {
      return null;
    }
  }

  /**
   * Renders a scalar MCP parameter as text.
   * <p>
   * The typed accessors throw when the JSON type differs from what the schema asked for, which an agent gets
   * wrong often enough that a failed tool call would be a regular occurrence. Every scalar is therefore read as
   * the text it stands for and interpreted by the caller.
   *
   * @param parameter the parameter
   * @return the text, null if absent or not a scalar
   */
  private static String text(McpParameters parameter) {
    try {
      if (parameter.isEmpty()) {
        return null;
      }
      if (parameter.isString()) {
        return parameter.asString().orElse(null);
      }
      if (parameter.isNumber()) {
        long value = parameter.asLong().orElse(0L);
        double preciseValue = parameter.asDouble().orElse(0.0);
        return preciseValue == value ? Long.toString(value) : Double.toString(preciseValue);
      }
      Boolean flag = parameter.asBoolean().orElse(null);
      return flag == null ? null : flag.toString();
    }
    catch (IllegalStateException isx) {
      return null;      // an object or an array where a scalar was expected
    }
  }

  /**
   * Renders a scalar value of the map-backed variant as text.
   *
   * @param value the value
   * @return the text, null if absent or not a scalar
   */
  private static String scalarText(Object value) {
    return switch (value) {
      case null -> null;
      case List<?> ignored -> null;
      case Map<?, ?> ignored -> null;
      default -> String.valueOf(value);
    };
  }

}
