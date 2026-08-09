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

package org.tentackle.maven.plugin.mcp.render;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches names by substring, glob, or regular expression.
 * <p>
 * Which of the three a caller wants is obvious from what it wrote, so it is derived rather than configured:
 * a {@code regex:} prefix selects a regular expression, a {@code *} or {@code ?} selects a glob, anything else
 * is a case-insensitive substring.
 *
 * @author harald
 */
public class NamePattern {

  /** The prefix selecting a regular expression. */
  public static final String REGEX_PREFIX = "regex:";

  /** Matches everything. */
  public static final NamePattern ANY = new NamePattern(null);

  private final String text;
  private final Pattern pattern;

  /**
   * Creates a pattern.
   *
   * @param text the pattern text, null or empty to match everything
   * @throws IllegalArgumentException if a regular expression is malformed
   */
  public NamePattern(String text) {
    this.text = text == null || text.isBlank() ? null : text.trim();
    this.pattern = this.text == null ? null : compile(this.text);
  }

  /**
   * Returns whether a name matches.
   *
   * @param name the name; may be null
   * @return true if it matches
   */
  public boolean matches(String name) {
    if (pattern == null) {
      return true;
    }
    return name != null && pattern.matcher(name).matches();
  }

  /**
   * Returns whether this pattern matches everything.
   *
   * @return true if unrestricted
   */
  public boolean isAny() {
    return pattern == null;
  }

  @Override
  public String toString() {
    return text == null ? "*" : text;
  }


  private static Pattern compile(String text) {
    if (text.toLowerCase(Locale.ROOT).startsWith(REGEX_PREFIX)) {
      String regex = text.substring(REGEX_PREFIX.length());
      try {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
      }
      catch (PatternSyntaxException psx) {
        throw new IllegalArgumentException("malformed regular expression '" + regex + "': " + psx.getMessage(), psx);
      }
    }
    if (text.indexOf('*') >= 0 || text.indexOf('?') >= 0) {
      return Pattern.compile(globToRegex(text), Pattern.CASE_INSENSITIVE);
    }
    return Pattern.compile(".*" + Pattern.quote(text) + ".*", Pattern.CASE_INSENSITIVE);
  }

  private static String globToRegex(String glob) {
    StringBuilder regex = new StringBuilder(glob.length() + 8);
    StringBuilder literal = new StringBuilder(glob.length());
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      if (c == '*' || c == '?') {
        if (!literal.isEmpty()) {
          regex.append(Pattern.quote(literal.toString()));
          literal.setLength(0);
        }
        regex.append(c == '*' ? ".*" : ".");
      }
      else {
        literal.append(c);
      }
    }
    if (!literal.isEmpty()) {
      regex.append(Pattern.quote(literal.toString()));
    }
    return regex.toString();
  }

}
