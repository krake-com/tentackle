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

package org.tentackle.io;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Some URI helper methods.
 */
public class UriHelper {

  private static final Logger LOGGER = Logger.get(UriHelper.class);


  /**
   * Gets a map of key-value pairs from a URI's query string.
   *
   * @param query the query string
   * @param converter the converter from strings to values
   * @return the map of query parameters
   */
  public static <T> Map<String, T> getQueryParameters(String query, Function<String, T> converter) {
    Map<String, T> map = new HashMap<>();
    if (query != null) {
      String[] pairs = query.split("&");
      for (String pair : pairs) {
        String[] keyValue = pair.split("=");
        if (keyValue.length == 2) {
          try {
            map.put(keyValue[0], converter.apply(keyValue[1]));
          }
          catch (RuntimeException ex) {
            // don't log as an error since this may be expected for mixed types
            LOGGER.fine("failed to convert query parameter {0} -> ignored", pair, ex);
          }
        }
        else {
          LOGGER.fine("invalid query parameter {0} -> ignored", pair);
        }
      }
    }
    return map;
  }


  /**
   * Expands variables in a string against the components of a base URI.<br>
   * This is a simple expression language to derive a URI (or any string) from another one, so that
   * variants can be created without repeating (or externally configuring) values such as the host or port.
   * <p>
   * A variable is written as {@code $name} or, if an arithmetic expression is needed, as {@code ${expression}}.
   * The following components of the base URI are provided (names are case-insensitive):
   * <ul>
   *   <li>{@code scheme} &mdash; the URI scheme (e.g. {@code trips})</li>
   *   <li>{@code userinfo} &mdash; the user-info part, if any</li>
   *   <li>{@code host} &mdash; the host</li>
   *   <li>{@code port} &mdash; the port (numeric)</li>
   *   <li>{@code authority} &mdash; the full authority ({@code userinfo@host:port})</li>
   *   <li>{@code path} &mdash; the path without its leading {@code /} (e.g. {@code myapp})</li>
   *   <li>{@code rawpath} &mdash; the path including its leading {@code /} (e.g. {@code /myapp})</li>
   *   <li>{@code query} &mdash; the query string without the leading {@code ?}, if any</li>
   *   <li>{@code fragment} &mdash; the fragment, if any</li>
   * </ul>
   * Within {@code ${...}} a single arithmetic operation ({@code +}, {@code -}, {@code *}, {@code /}, {@code %})
   * may be applied to a numeric variable and an integer literal, for example {@code ${port+1}}.
   * A {@code $} can be escaped with a backslash ({@code \$}).
   * <p>
   * Example (given the base {@code trips://localhost:18000/myapp?idle=10}):
   * <pre>
   *   tripcs://$host:${port+1}/$path?$query
   * </pre>
   * expands to {@code tripcs://localhost:18001/myapp?idle=10}.
   * <p>
   * If the string contains no variable reference, it is returned unchanged and {@code base} may be null.
   *
   * @param value the (possibly variable-referencing) string
   * @param base the base URI to resolve variables against, may be null if {@code value} references no variables
   * @return the expanded string with all variables resolved
   * @throws TentackleRuntimeException if a variable is referenced but cannot be resolved
   */
  public static String expand(String value, URI base) {
    if (value.indexOf('$') < 0) {
      return value;   // fast path: nothing to expand
    }
    if (base == null) {
      throw new TentackleRuntimeException("'" + value + "' references variables but no base URI was provided");
    }
    StringBuilder out = new StringBuilder();
    int len = value.length();
    for (int i = 0; i < len; i++) {
      char c = value.charAt(i);
      if (c == '\\' && i + 1 < len) {
        out.append(value.charAt(++i));      // escaped character
      }
      else if (c == '$' && i + 1 < len && value.charAt(i + 1) == '{') {
        int end = value.indexOf('}', i + 2);
        if (end < 0) {
          throw new TentackleRuntimeException("unterminated '${' in '" + value + "'");
        }
        out.append(evaluate(value.substring(i + 2, end).trim(), base, value));
        i = end;
      }
      else if (c == '$' && i + 1 < len && isVariableStart(value.charAt(i + 1))) {
        int j = i + 1;
        while (j < len && isVariablePart(value.charAt(j))) {
          j++;
        }
        out.append(resolve(value.substring(i + 1, j), base, value));
        i = j - 1;
      }
      else {
        out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Evaluates a {@code ${...}} expression, i.e., a variable optionally followed by a single arithmetic operation.
   *
   * @param expr the trimmed expression, e.g. {@code port+1}
   * @param base the base URI
   * @param value the whole string (for error messages)
   * @return the resolved string
   */
  private static String evaluate(String expr, URI base, String value) {
    for (int i = 0; i < expr.length(); i++) {
      char op = expr.charAt(i);
      if (op == '+' || op == '-' || op == '*' || op == '/' || op == '%') {
        String name = expr.substring(0, i).trim();
        String operand = expr.substring(i + 1).trim();
        long left;
        long right;
        try {
          left = Long.parseLong(resolve(name, base, value));
          right = Long.parseLong(operand);
        }
        catch (NumberFormatException nx) {
          throw new TentackleRuntimeException(
              "non-numeric operand in expression '" + expr + "' of '" + value + "'", nx);
        }
        long result = switch (op) {
          case '+' -> left + right;
          case '-' -> left - right;
          case '*' -> left * right;
          case '/' -> left / right;
          default  -> left % right;
        };
        return Long.toString(result);
      }
    }
    return resolve(expr, base, value);
  }

  /**
   * Resolves a single variable name against the base URI.
   *
   * @param name the variable name (case-insensitive)
   * @param base the base URI
   * @param value the whole string (for error messages)
   * @return the variable's value, never null
   */
  private static String resolve(String name, URI base, String value) {
    String result = switch (name.toLowerCase()) {
      case "scheme"    -> base.getScheme();
      case "userinfo"  -> base.getRawUserInfo();
      case "host"      -> base.getHost();
      case "port"      -> base.getPort() < 0 ? null : Integer.toString(base.getPort());
      case "authority" -> base.getRawAuthority();
      case "path"      -> stripLeadingSlash(base.getRawPath());
      case "rawpath"   -> base.getRawPath();
      case "query"     -> base.getRawQuery();
      case "fragment"  -> base.getRawFragment();
      default -> throw new TentackleRuntimeException("unknown variable '" + name + "' in '" + value + "'");
    };
    if (result == null) {
      throw new TentackleRuntimeException(
          "variable '" + name + "' is not defined in base URI '" + base + "'");
    }
    return result;
  }

  private static String stripLeadingSlash(String path) {
    return path != null && path.startsWith("/") ? path.substring(1) : path;
  }

  private static boolean isVariableStart(char c) {
    return Character.isLetter(c) || c == '_';
  }

  private static boolean isVariablePart(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }


  private UriHelper() {}
}
