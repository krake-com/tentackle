/**
 * Tentackle - http://www.tentackle.org
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



package org.tentackle.common;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides Methods to format names of methods and variables.
 *
 * @author harald
 */
public final class BasicStringHelper {


  /**
   * Converts a string to uppercase allowing null values.
   *
   * @param str the string
   * @return the uppercase string or null
   */
  public static String toUpper(String str) {
    if (str != null) {
      str = str.toUpperCase();
    }
    return str;
  }


  /**
   * Converts a string to lowercase allowing null values.
   *
   * @param str the string
   * @return the lowercase string or null
   */
  public static String toLower(String str) {
    if (str != null) {
      str = str.toLowerCase();
    }
    return str;
  }


  /**
   * Converts the first character of string to uppercase.
   *
   * @param str the string
   * @return the converted string
   */
  public static String firstToUpper(String str) {
    if (str != null && str.length() > 0) {
      StringBuilder buf = new StringBuilder(str);
      buf.deleteCharAt(0);
      buf.insert(0, Character.toUpperCase(str.charAt(0)));
      return buf.toString();
    }
    return null;
  }


  /**
   * Converts the first character of string to uppercase.
   *
   * @param str the string
   * @return the converted string
   */
  public static String firstToLower(String str) {
    if (str != null && str.length() > 0) {
      StringBuilder buf = new StringBuilder(str);
      buf.deleteCharAt(0);
      buf.insert(0, Character.toLowerCase(str.charAt(0)));
      return buf.toString();
    }
    return null;
  }


  /**
   * Gets a classname without any optional generics.
   *
   * @param className the original classname
   * @return classname without generics
   */
  public static String getPlainClassName(String className) {
    int ndx = className.indexOf('<');
    if (ndx > 0) {
      return className.substring(0, ndx).trim();
    }
    else  {
      return className;
    }
  }


  /**
   * Checks whether given string introduces a continuation line.
   * <p>
   * This is the case if the last character is an unquoted backslash.
   * @param line the source line
   * @return null if line does not introduce a continuation line
   */
  public static String getContinuedLine(String line) {
    if (line != null) {
      int len = line.length();
      if (len > 0 && line.charAt(len - 1) == '\\') {
        // ends with double quote: check if number of quotes is odd
        int ndx = len - 1;
        int slashCount = 1;
        while (--ndx >= 0) {
          if (line.charAt(ndx) == '\\') {
            slashCount++;
          }
          else  {
            break;
          }
        }
        if ((slashCount & 1) == 1) {
          // odd
          return line.substring(0, len - 1);
        }
      }
    }
    return null;
  }


  /**
   * Takes a string, surrounds it with double-quotes and escapes all double-quotes
   * already in the string according to Unix rules.
   * Example:
   * <pre>
   * <code>Length 5" --&gt; "Length 5\""</code>
   * </pre>
   *
   * @param str the string
   * @return the string in double quotes
   */
  public static String toDoubleQuotes(String str) {
    StringBuilder buf = new StringBuilder();
    buf.append('"');
    if (str != null)  {
      int len = str.length();
      for (int i=0; i < len; i++) {
        char c = str.charAt(i);
        if (c == '"' || c == '\\') {
          buf.append('\\');
        }
        else if (Character.isISOControl(c)) {
          c = ' ';    // transform any controls to spaces
        }
        buf.append(c);
      }
    }
    buf.append('"');
    return buf.toString();
  }


  /**
   * Parses a string.<br>
   * The string may be enclosed in double- or single quotes which
   * will be removed. Those special characters may be escaped
   * by a backslash. The backslash itself can be written as a double backslash.
   * Special escapes are:<p>
   * \n = new line
   * \r = carriage return
   * \t = tab
   * \f = form feed
   *
   * @param str the source string
   * @return a string, never null
   */
  public static String parseString(String str) {
    StringBuilder buf = new StringBuilder();
    if (str != null && str.length() > 0) {
      char quoteChar = str.charAt(0);
      if (quoteChar != '"' && quoteChar != '\'') {
        quoteChar = 0;    // not quoted
      }
      if (quoteChar != 0) {
        // last character must be the same quote char
        char lastChar = str.charAt(str.length() - 1);
        if (lastChar != quoteChar || str.length() < 2) {
          throw new TentackleRuntimeException("String <" + str + "> contains unbalanced quotes");
        }
        // cut off quotes
        str = str.substring(1, str.length() - 1);
      }

      boolean escaped = false;
      for (int i=0; i < str.length(); i++) {
        char c = str.charAt(i);
        if (escaped) {
          // check for special escapes
          if (c == 'n') {
            buf.append('\n');
          }
          else if (c == 'r') {
            buf.append('\r');
          }
          else if (c == 't') {
            buf.append('\t');
          }
          else if (c == 'f') {
            buf.append('\f');
          }
          else  {
            buf.append(c);
          }
          escaped = false;
        }
        else  {
          if (c == '\\') {
            escaped = true;
          }
          else  {
            buf.append(c);
          }
        }
      }
    }
    return buf.toString();
  }


  /**
   * Splits a string keeping strings together.<br>
   * The strings may be enclosed in double- or single quotes which
   * will not be removed. Those special characters may be escaped
   * by a backslash. The backslash itself can be written as a double backslash.
   * Special escapes are:<p>
   * \n = new line
   * \r = carriage return
   * \t = tab
   * \f = form feed
   *
   * @param str the string
   * @param delimiters the delimiter characters
   * @return the strings, never null
   */
  public static List<String> split(String str, String delimiters) {
    List<String> parts = new ArrayList<>();
    if (str != null) {
      StringBuilder part = null;
      boolean escaped = false;
      char quoteChar = 0;
      for (int i = 0; i < str.length(); i++) {
        char c = str.charAt(i);
        if (escaped) {
          // check for special escapes
          if (c == 'n') {
            part.append('\n');
          }
          else if (c == 'r') {
            part.append('\r');
          }
          else if (c == 't') {
            part.append('\t');
          }
          else if (c == 'f') {
            part.append('\f');
          }
          else  {
            part.append(c);
          }
          escaped = false;
        }
        else  {
          if (c == '\\') {
            escaped = true;
            if (part == null) {
              part = new StringBuilder();
            }
          }
          else  {
            if (c == '\'' || c == '"') {
              // unescaped quote character
              if (part == null) {
                // leading quote: new part
                part = new StringBuilder();
                part.append(c);
                quoteChar = c;
              }
              else if (quoteChar != 0 && quoteChar != c) {
                // not the closing quote
                part.append(c);
              }
              else {
                // closing quote: add part
                part.append(c);
                parts.add(part.toString());
                part = null;
                quoteChar = 0;
              }
            }
            else if (delimiters.indexOf(c) >= 0) {
              // is a delimiter
              if (quoteChar == 0) {   // not within string
                if (part != null) {
                  // close part
                  parts.add(part.toString());
                  part = null;
                }
                // else: ignore delimiter between parts
              }
              else {
                part.append(c);
              }
            }
            else {
              if (part == null) {
                part = new StringBuilder();
              }
              part.append(c);
            }
          }
        }
      }
      if (part != null) {
        parts.add(part.toString());
      }
    }
    return parts;
  }


  /**
   * Checks that given string is a valid Java classname.<br>
   * Both classnames with a full classpath or without are validated.
   *
   * @param className the classname
   * @return true if valid
   */
  public static boolean isValidJavaClassName(String className) {
    int dotIndex = className.lastIndexOf('.');
    if (dotIndex > 0) {
      String packageName = className.substring(0, dotIndex);
      className = className.substring(dotIndex + 1);
      if (!isValidJavaPackageName(packageName)) {
        return false;
      }
    }
    if (className.length() < 1) {
      return false;
    }
    char firstChar = className.charAt(0);
    if (!Character.isAlphabetic(firstChar) ||
        !Character.isUpperCase(firstChar)) {
      return false;
    }
    for (int pos = 0; pos < className.length(); pos++) {
      char c = className.charAt(pos);
      if (!Character.isAlphabetic(c) && !Character.isDigit(c) && c != '_') {
        return false;
      }
    }
    return true;
  }


  /**
   * Checks that given string is a valid Java packagename.<br>
   * This is very restrictive verification. Packagenames
   * must be all lowercase alphabetic or digits and must contain only dots and underscores.
   *
   * @param packageName the packagename
   * @return true if valid
   */
  public static boolean isValidJavaPackageName(String packageName) {
    boolean lastWasDot = true;
    for (int pos = 0; pos < packageName.length(); pos++) {
      char c = packageName.charAt(pos);
      if (c == '.') {
        lastWasDot = true;
      }
      else  {
        if (lastWasDot) {
          lastWasDot = false;
          if (Character.isDigit(c)) {
            return false;   // must not start with a digit
          }
        }
        else  {
          if (!Character.isDigit(c) &&    // digit is ok if not at start of package name
              (Character.isUpperCase(c) || (!Character.isAlphabetic(c) && c != '_'))) {
            // mus be lowercase alphabetic or underscore
            return false;
          }
        }
      }
    }
    return true;
  }


  /**
   * Returns whether the given string is a valid java identifier.
   *
   * @param str the string
   * @return true if valid, false if not
   */
  public static boolean isValidJavaIdentifier(String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }
    char[] c = str.toCharArray();
    if (!Character.isJavaIdentifierStart(c[0])) {
      return false;
    }
    for (int i = 1; i < c.length; i++) {
      if (!Character.isJavaIdentifierPart(c[i])) {
        return false;
      }
    }
    return true;
  }


  /**
   * Reads a textfile from a resource.<br>
   * The textfile must be in the classpath.
   * <p>
   * Example:
   * <pre>
   *   String model = BasicStringHelper.readTestFromResource("org/tentackle/model/TestModel.txt");
   * </pre>
   *
   * @param resourceName the name of the resource
   * @return the loaded text
   * @throws IOException if loading failed or no such resource
   */
  public static String readTextFromResource(String resourceName) throws IOException {

    InputStream is = null;

    try {
      is = BasicStringHelper.class.getResourceAsStream(resourceName);
      if (is == null) {
        // try other variant
        is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (is == null) {
          throw new FileNotFoundException("no such resource: " + resourceName);
        }
      }
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      StringBuilder buf = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        buf.append(line);
        buf.append('\n');
      }
      return buf.toString();
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException ex) {
        }
      }
    }
  }


  /**
   * Checks if string contains only whitespaces.
   *
   * @param str the string to check
   * @return true if null, empty or all whitespace, false if at least one non-whitespace-character found
   */
  public static boolean isAllWhitespace(String str) {
    if (str != null)  {
      int len = str.length();
      for (int i=0; i < len; i++) {
        char c = str.charAt(i);
        if (Character.isWhitespace(c) == false) {
          return false;
        }
      }
      return true;    // all whitespaces
    }
    return true;  // no non-whitespace at all
  }



  /**
   * All java reserved words.
   */
  public static final String[] javaReservedWords = {
    "abstract",
    "assert",
    "boolean",
    "break",
    "byte",
    "case",
    "catch",
    "char",
    "class",
    "const",
    "continue",
    "default",
    "double",
    "do",
    "else",
    "enum",
    "extends",
    "false",
    "final",
    "finally",
    "float",
    "for",
    "goto",
    "if",
    "implements",
    "import",
    "instanceof",
    "int",
    "interface",
    "long",
    "native",
    "new",
    "null",
    "package",
    "private",
    "protected",
    "public",
    "return",
    "short",
    "static",
    "strictfp",
    "super",
    "switch",
    "synchronized",
    "this",
    "throw",
    "throws",
    "transient",
    "true",
    "try",
    "void",
    "volatile",
    "while"
  };


  /**
   * Returns whether given string is a reserved java keyword.
   *
   * @param word the string to test
   * @return true if java reserved word
   */
  public static boolean isReservedWord(String word) {
    if (word != null) {
      for (String rw: javaReservedWords) {
        if (rw.equals(word)) {
          return true;
        }
      }
    }
    return false;
  }


  /**
   * Gets the word-string for a digit.<br>
   * Example:
   * <pre>
   * '0' --&gt; "zero"
   * </pre>
   *
   * @param digit the digit character
   * @return the word-string
   */
  public static String digitToString(Character digit) {
    return CommonCommonBundle.getString(String.valueOf(digit));
  }


  /**
   * prevent instantiation.
   */
  private BasicStringHelper() { }

}
