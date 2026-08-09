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

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the JSON schema of a tool's arguments.
 * <p>
 * The schema is what an agent reads before calling, so the descriptions here are part of the API, not
 * documentation. Closed argument sets are declared as a JSON {@code enum} rather than described in prose,
 * because a listed value is one an agent cannot get wrong.
 * <p>
 * Helidon's JSON Schema API expresses neither {@code enum} nor {@code default}, and
 * {@link io.helidon.extensions.mcp.server.McpTool#schema()} is a plain string, so the JSON is emitted directly.
 *
 * @author harald
 */
public class SchemaBuilder {

  private final List<String> properties;
  private final List<String> required;

  /**
   * Creates a builder for an object schema.
   */
  public SchemaBuilder() {
    properties = new ArrayList<>();
    required = new ArrayList<>();
  }

  /**
   * Adds an optional string property.
   *
   * @param name the property name
   * @param description what it does
   * @return this builder
   */
  public SchemaBuilder string(String name, String description) {
    properties.add(property(name, "\"type\":\"string\"", description));
    return this;
  }

  /**
   * Adds a mandatory string property.
   *
   * @param name the property name
   * @param description what it does
   * @return this builder
   */
  public SchemaBuilder requiredString(String name, String description) {
    string(name, description);
    required.add(name);
    return this;
  }

  /**
   * Adds a string property restricted to a set of values.
   *
   * @param name the property name
   * @param description what it does
   * @param defaultValue the value used when absent, null if none
   * @param values the allowed values
   * @return this builder
   */
  public SchemaBuilder enumeration(String name, String description, String defaultValue, String... values) {
    StringBuilder body = new StringBuilder(128);
    body.append("\"type\":\"string\",\"enum\":").append(stringArray(values));
    if (defaultValue != null) {
      body.append(",\"default\":").append(quote(defaultValue));
    }
    properties.add(property(name, body.toString(), description));
    return this;
  }

  /**
   * Adds an array-of-strings property.
   *
   * @param name the property name
   * @param description what it does
   * @param values the allowed item values, none to allow any string
   * @return this builder
   */
  public SchemaBuilder stringArray(String name, String description, String... values) {
    String items = values.length == 0
                   ? "{\"type\":\"string\"}"
                   : "{\"type\":\"string\",\"enum\":" + stringArray(values) + '}';
    properties.add(property(name, "\"type\":\"array\",\"items\":" + items, description));
    return this;
  }

  /**
   * Adds an integer property.
   *
   * @param name the property name
   * @param description what it does
   * @param defaultValue the value used when absent
   * @return this builder
   */
  public SchemaBuilder integer(String name, String description, int defaultValue) {
    properties.add(property(name, "\"type\":\"integer\",\"default\":" + defaultValue, description));
    return this;
  }

  /**
   * Adds a boolean property.
   *
   * @param name the property name
   * @param description what it does
   * @param defaultValue the value used when absent
   * @return this builder
   */
  public SchemaBuilder bool(String name, String description, boolean defaultValue) {
    properties.add(property(name, "\"type\":\"boolean\",\"default\":" + defaultValue, description));
    return this;
  }

  /**
   * Builds the schema.
   *
   * @return the JSON schema
   */
  public String build() {
    StringBuilder buf = new StringBuilder(512);
    buf.append("{\"type\":\"object\",\"properties\":{").append(String.join(",", properties)).append('}');
    if (!required.isEmpty()) {
      buf.append(",\"required\":").append(stringArray(required.toArray(new String[0])));
    }
    return buf.append('}').toString();
  }

  private String property(String name, String body, String description) {
    return quote(name) + ":{" + body + ",\"description\":" + quote(description) + '}';
  }

  private String stringArray(String... values) {
    List<String> quoted = new ArrayList<>(values.length);
    for (String value : values) {
      quoted.add(quote(value));
    }
    return "[" + String.join(",", quoted) + "]";
  }

  /**
   * Quotes a string as a JSON literal.
   *
   * @param text the text
   * @return the quoted text
   */
  public static String quote(String text) {
    StringBuilder buf = new StringBuilder(text.length() + 16).append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> buf.append("\\\"");
        case '\\' -> buf.append("\\\\");
        case '\n' -> buf.append("\\n");
        case '\r' -> buf.append("\\r");
        case '\t' -> buf.append("\\t");
        case '\b' -> buf.append("\\b");
        case '\f' -> buf.append("\\f");
        default -> {
          if (c < 0x20) {
            buf.append(String.format("\\u%04x", (int) c));
          }
          else {
            buf.append(c);
          }
        }
      }
    }
    return buf.append('"').toString();
  }

}
