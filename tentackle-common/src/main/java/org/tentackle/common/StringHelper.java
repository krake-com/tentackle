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
 * License along with this library; if not, write to the DFree Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.common;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Some handy methods for strings.
 *
 * @author  harald
 */
public final class StringHelper {

  /**
   * Limit size of collection to be logged.<br>
   * If an object is logged, and it is a collection
   * and the size exceeds {@code maxLogCollectionSize}, only the number
   * of items is logged in {@link #objectToLoggableString}
   * instead of each item.
   */
  private static int maxLogCollectionSize = 10;

  /**
   * Sets the size limit of collections to be logged.<br>
   * If an object is logged, and it is a collection
   * and the size exceeds {@code maxLogCollectionSize}, only the number
   * of items is logged in {@link #objectToLoggableString}
   * instead of each item.
   *
   * @param maxLogCollectionSize the limit
   */
  public static void setMaxLogCollectionSize(int maxLogCollectionSize) {
    StringHelper.maxLogCollectionSize = maxLogCollectionSize;
  }

  /**
   * Gets the size limit of collections to be logged.
   *
   * @return the limit (default is 10)
   */
  public static int getMaxLogCollectionSize() {
    return maxLogCollectionSize;
  }



  private static final byte[] EMPTY_BYTES = new byte[0];


  /**
   * Null safe {@link String#equalsIgnoreCase(String)}.
   *
   * @param a first string, may be null
   * @param b second string, may be null
   * @return true if equal ignoring the case or both are null
   */
  public static boolean equalsIgnoreCase(String a, String b) {
    return (a == b) || (a != null && a.equalsIgnoreCase(b));      // == is ok!
  }


  /**
   * Converts a string to uppercase allowing null values.<br>
   * The conversion is done with {@link Locale#ROOT}.
   *
   * @param str the string
   * @return the uppercase string or null
   */
  public static String toUpper(String str) {
    if (str != null) {
      str = str.toUpperCase(Locale.ROOT);
    }
    return str;
  }


  /**
   * Converts a string to lowercase allowing null values.<br>
   * The conversion is done with {@link Locale#ROOT}.
   *
   * @param str the string
   * @return the lowercase string or null
   */
  public static String toLower(String str) {
    if (str != null) {
      str = str.toLowerCase(Locale.ROOT);
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
    if (str != null && !str.isEmpty()) {
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
    if (str != null && !str.isEmpty()) {
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
   * Returns the first index of the given characters.
   *
   * @param str the string
   * @param anyOf the characters
   * @return the index to the first character found, -1 if no such character
   */
  public static int indexAnyOf(String str, String anyOf) {
    int index = -1;
    if (anyOf != null && str != null) {
      for (int i = 0; i < str.length(); i++) {
        char c = str.charAt(i);
        if (anyOf.indexOf(c) >= 0) {
          index = i;
          break;
        }
      }
    }
    return index;
  }


  /**
   * Takes a string, surrounds it with double-quotes and escapes all double-quotes
   * already in the string according to Unix rules. Formfeed, linefeed, carriage return
   * and tab will be converted to their well-known escape sequence. All other ISO Controls
   * are converted to spaces.
   * <p>
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
          if (c == '\f') {
            buf.append("\\f");
            continue;
          }
          if (c == '\t') {
            buf.append("\\t");
            continue;
          }
          if (c == '\n') {
            buf.append("\\n");
            continue;
          }
          if (c == '\r') {
            buf.append("\\r");
            continue;
          }
          // transform any controls/separators to spaces
          c = ' ';
        }
        buf.append(c);
      }
    }
    buf.append('"');
    return buf.toString();
  }


  /**
   * Converts a character that followed a backslash to an escaped character.<br>
   * The special escapes are:<p>
   * \n = new line<br>
   * \r = carriage return<br>
   * \t = tab<br>
   * \f = form feed<br>
   *
   * @param c the character following a backslash in a string
   * @return the converted escape character
   */
  public static char charToEscaped(char c) {
    return switch (c) {
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'f' -> '\f';
      default -> c;
    };
  }


  /**
   * Converts an escape character to a parsable string.<br>
   * The special escapes are:<p>
   * \n = new line<br>
   * \r = carriage return<br>
   * \t = tab<br>
   * \f = form feed<br>
   *
   * @param c the escape character
   * @return the parsable string
   */
  public static String escapedToString(char c) {
    return switch (c) {
      case '\n' -> "\\n";
      case '\r' -> "\\r";
      case '\t' -> "\\t";
      case '\f' -> "\\f";
      default -> String.valueOf(c);
    };
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
    if (str != null && !str.isEmpty()) {
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
          buf.append(charToEscaped(c));
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
   * Converts a string to a string parsable by {@link #parseString(java.lang.String)}.
   *
   * @param str the string
   * @return the parsable string
   */
  public static String toParsableString(String str) {
    StringBuilder buf = new StringBuilder();
    boolean needQuotes = false;
    for (int i=0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c == '\'' || c == '"' || c == '\\') {
        buf.append('\\').append(c);
        needQuotes = true;
      }
      else {
        if (Character.isWhitespace(c)) {
          needQuotes = true;
          buf.append(escapedToString(c));
        }
        else {
          buf.append(c);
        }
      }
    }
    if (needQuotes) {
      buf.insert(0, '"');
      buf.append('"');
    }
    return buf.toString();
  }


  /**
   * Splits a string keeping strings together.<br>
   * The strings may be enclosed in double- or single quotes which
   * will not be removed. Those special characters may be escaped
   * by a backslash. The backslash itself can be written as a double backslash.
   * Special escapes are:<p>
   * \n = new line<br>
   * \r = carriage return<br>
   * \t = tab<br>
   * \f = form feed<br>
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
          part.append(charToEscaped(c));
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
   * Checks whether given string is a fully qualified classname (FQCN).<br>
   * The class must belong to a package!
   *
   * @param className the classname
   * @return true if FQCN
   */
  public static boolean isFQCN(String className) {
    return className.contains(".") && isValidJavaClassName(className);
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
    if (className.isEmpty()) {
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
   * Checks that given string is a valid Java package name.<br>
   * This is very restrictive verification. Package names
   * must be all lowercase alphabetic or digits and must contain only dots and underscores.
   *
   * @param packageName the package name
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
   * Reads a textfile from a resource.
   * <p>
   * Example:
   * <pre>
   *   String model = StringHelper.readTextFromResource("/org/tentackle/model/TestModel.txt");
   * </pre>
   *
   * @param caller the caller class, null to determine via {@link StackWalker}
   * @param resourceName the name of the resource
   * @return the loaded text
   * @throws IOException if loading failed
   * @throws FileNotFoundException if no such resource found
   */
  public static String readTextFromResource(Class<?> caller, String resourceName) throws IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(FileHelper.createInputStream(caller, resourceName)))) {
      StringBuilder buf = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        buf.append(line);
        buf.append('\n');
      }
      return buf.toString();
    }
  }


  /**
   * Checks if string contains only whitespaces.
   *
   * @param str the string to check, may be null
   * @return true if null, empty or all whitespace, false if at least one non-whitespace-character found
   */
  public static boolean isAllWhitespace(String str) {
    return str == null || str.isBlank();
  }



  /**
   * All java reserved words.
   */
  private static final String[] JAVA_RESERVED_WORDS = {
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
      for (String rw: JAVA_RESERVED_WORDS) {
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
   * Strips enclosing double quotes.
   *
   * @param str the original string
   * @return the string with its double quotes removed
   */
  public static String stripEnclosingDoubleQuotes(String str) {
    if (str != null && !str.isEmpty()) {
      if (str.charAt(0) == '"') {
        // strip enclosing quotes
        str = str.substring(1);
      }
      if (!str.isEmpty() && str.charAt(str.length() - 1) == '"') {
        str = str.substring(0, str.length() - 1);
      }
    }
    return str;
  }


  /**
   * Takes a string and returns one with a given length, cutting or
   * filling up with fillchars, whatever appropriate.
   *
   * @param str the string
   * @param length the length of the returned string
   * @param filler the character to fill up if str is too short
   * @return the string with the desired length
   */
  public static String toFixedLength(String str, int length, char filler)  {
    int len = 0;
    StringBuilder buf = new StringBuilder();
    if (str != null)  {
      len = str.length();
      if (len > length) {
        buf.append(str, 0, length);
        len = length;
      }
      else  {
        buf.append(str);
      }
    }
    while (len < length)  {
      buf.append(filler);
      len++;
    }
    return buf.toString();
  }

  /**
   * Takes a string and returns one with a given length, cutting or
   * filling up with spaces, whatever appropriate.
   *
   * @param str the string
   * @param length the length of the returned string
   * @return the string with the desired length
   */
  public static String toFixedLength(String str, int length)  {
    return toFixedLength(str, length, ' ');
  }


  /**
   * Takes a string and returns one with a given length, cutting or
   * filling up with fillchars from the left, whatever appropriate.
   *
   * @param str the string
   * @param length the length of the returned string
   * @param filler the character to fill up if str is too short
   * @return the string with the desired length
   */
  public static String toFixedLengthLeftFill(String str, int length, char filler)  {
    int len = 0;
    StringBuilder buf = new StringBuilder();
    if (str != null)  {
      len = str.length();
      if (len > length) {
        buf.append(str, 0, length);
        len = length;
      }
      else  {
        buf.append(str);
      }
    }
    while (len < length)  {
      buf.insert(0, filler);
      len++;
    }
    return buf.toString();
  }

  /**
   * Takes a string and returns one with a given length, cutting or
   * filling up with spaces from the left, whatever appropriate.
   *
   * @param str the string
   * @param length the length of the returned string
   * @return the string with the desired length
   */
  public static String toFixedLengthLeftFill(String str, int length)  {
    return toFixedLengthLeftFill(str, length, ' ');
  }


  /**
   * Filters illegal chars for Java variable names.
   *
   * @param str the string
   * @return the java conform string
   */
  public static String toVarName(String str)  {
    if (str != null)  {
      int len = str.length();
      StringBuilder sbuf = new StringBuilder();
      for (int i=0; i < len; i++) {
        char c = str.charAt(i);
        if ((c >= '0' && c <= '9') ||
            (c >= 'A' && c <= 'Z') ||
            (c >= 'a' && c <= 'z')) {
          sbuf.append(c);
        }
        else if (c == '_' || Character.isWhitespace(c)) {
          sbuf.append('_');
        }
      }
      str = sbuf.toString();
    }
    return str;
  }


  /**
   * Converts a dotted path to camelCase.<br>
   * Example:<br>
   * {@code "alpha.beta.gamma"} is converted to {@code "alphaBetaGamma"}.
   *
   * @param str the dotted path
   * @return the camel case path
   */
  public static String toCamelCase(String str) {
    StringBuilder buf = new StringBuilder();
    boolean lastWasDot = false;
    for (int i=0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c == '.') {
        lastWasDot = true;
      }
      else if (lastWasDot) {
        buf.append(Character.toUpperCase(c));
        lastWasDot = false;
      }
      else {
        buf.append(c);
      }
    }
    return buf.toString();
  }


  /**
   * Converts a camelCase string to a string with delimiters.
   * <p>
   * Example:<br>
   * {@code "alphaBetaGamma"} is converted to {@code "alpha.beta.gamma"}, with delimiter {@code "."}.
   *
   * @param str the camelcase string
   * @param delimiter the delimiter to insert between parts, null or empty if none
   * @return the converted string
   */
  public static String camelCaseToDelimited(String str, String delimiter) {
    StringBuilder buf = new StringBuilder();
    for (int i=0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (Character.isUpperCase(c)) {
        if (delimiter != null) {
          buf.append(delimiter);
        }
        c = Character.toLowerCase(c);
      }
      buf.append(c);
    }
    return buf.toString();
  }


  /**
   * Checks if a string contains only digits or whitespaces, i.e.
   * no illegal char in a number string.
   *
   * @param str the string to check
   * @param whitespaceAllowed true if whitespaces are allowed
   *
   * @return true if no illegal char detected, false otherwise
   */
  public static boolean isAllDigits(String str, boolean whitespaceAllowed) {
    if (str != null)  {
      int len = str.length();
      for (int i=0; i < len; i++) {
        char c = str.charAt(i);
        if (!Character.isDigit(c) &&
            (!whitespaceAllowed || !Character.isWhitespace(c))) {
          return false;
        }
      }
      return true;    // all digits or whitespaces
    }
    return true;    // null string is the same as empty string
  }


  /**
   * checks if a string contains only digits, i.e.
   * no non-number char in string, even no whitespace.
   *
   * @param str the string to check
   *
   * @return true if no illegal char detected, false otherwise
   */
  public static boolean isAllDigits(String str) {
    return isAllDigits(str, false);
  }


  /**
   * Trims a string.<br>
   * The method is null-safe.
   *
   * @param s the string, may be null
   * @param max the maximum number of characters, 0 = minimum length
   * @return the trimmed string, null if s == null
   */
  public static String trim(String s, int max) {
    if (s != null)  {
      s = s.trim();
      if (max > 0 && s.length() > max)  {
        s = s.substring(0, max);
      }
    }
    return s;
  }


  /**
   * Trims a string.<br>
   * The method is null-safe.
   *
   * @param s the string, may be null
   * @return the trimmed string, null if s == null
   * @see #trim(java.lang.String, int)
   */
  public static String trim(String s) {
    return trim(s, 0);
  }


  /**
   * Cuts trailing characters.
   * <p>
   * Removes all trailing characters of given value from the string.
   * If the string consists of those characters only, the
   * returned string will empty.
   *
   * @param str the string
   * @param filler the filler to remove
   * @return the trimmed string, never null
   */
  public static String trimRight(String str, char filler) {
    if (str == null) {
      str = "";
    }
    else if (!str.isEmpty()) {
      // find index of last non-filler character
      int i=str.length();
      while (--i >= 0) {
        char c = str.charAt(i);
        if (c != filler) {
          break;
        }
      }
      str = str.substring(0, i + 1);
    }
    return str;
  }

  /**
   * Cuts leading characters.
   * <p>
   * Removes all leading characters of given value from the string.
   * If the string consists of those characters only, the
   * returned string will be empty.
   *
   * @param str the string
   * @param filler the filler to remove
   * @return the trimmed string, never null
   */
  public static String trimLeft(String str, char filler) {
    if (str == null) {
      str = "";
    }
    else {
      StringBuilder buf = new StringBuilder();
      boolean fillerFound = false;
      for (int i=0; i < str.length(); i++) {
        char c = str.charAt(i);
        if (fillerFound || c != filler) {
          buf.append(c);
          fillerFound = true;
        }
      }
      str = buf.toString();
    }
    return str;
  }


  /**
   * Fills up a string from the left with a filler character to match a given length.<br>
   * If the string is already longer, nothing happens.
   *
   * @param str the string
   * @param length the desired length
   * @param filler the filler character
   * @return the filled string, never null
   */
  public static String fillLeft(String str, int length, char filler) {
    int diff = length - (str == null ? 0 : str.length());
    if (diff > 0) {
      StringBuilder buf = new StringBuilder();
      while (diff-- > 0) {
        buf.append(filler);
      }
      if (str != null) {
        buf.append(str);
      }
      str = buf.toString();
    }
    else if (diff == 0 && str == null) {
      str = "";
    }
    return str;
  }

  /**
   * Fills up a string to the right with a filler character to match a given length.<br>
   * If the string is already longer, nothing happens.
   *
   * @param str the string
   * @param length the desired length
   * @param filler the filler character
   * @return the filled string, never null
   */
  public static String fillRight(String str, int length, char filler) {
    int diff = length - (str == null ? 0 : str.length());
    if (diff > 0) {
      StringBuilder buf = new StringBuilder();
      if (str != null) {
        buf.append(str);
      }
      while (diff-- > 0) {
        buf.append(filler);
      }
      str = buf.toString();
    }
    else if (diff == 0 && str == null) {
      str = "";
    }
    return str;
  }



  /**
   * Gets the part of a string before a given delimiter.
   *
   * @param str the string
   * @param delimiter the delimiter
   * @return the first part up to but excluding delimiter or the string if no delimiter at all
   */
  public static String firstBefore(String str, char delimiter) {
    if (str != null) {
      int ndx = str.indexOf(delimiter);
      if (ndx >= 0) {
        str = str.substring(0, ndx);
      }
    }
    return str;
  }


  /**
   * Gets the part of a string after a given delimiter.
   *
   * @param str the string
   * @param delimiter the delimiter
   * @return the last part following delimiter or the string if no delimiter at all
   */
  public static String lastAfter(String str, char delimiter) {
    if (str != null) {
      int ndx = str.lastIndexOf(delimiter);
      if (ndx >= 0) {
        str = str.substring(ndx + 1);
      }
    }
    return str;
  }


  /**
   * Gets the first line from a multi-line string.
   * Nice in tables.
   * @param str the multiline string
   * @return the first line, null if str == null
   */
  public static String firstLine(String str) {
    return firstBefore(str, '\n');
  }


  private static final char[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
      'a', 'b', 'c', 'd', 'e', 'f'
  };

  /**
   * Creates a human-readable hex-String out of a byte-array (e.g. from MessageDigest MD5sum).
   *
   * @param binaryData the data, may be null
   * @return the formatted hex string , null if data was null
   */
  public static String toHexString(byte[] binaryData)  {
    if (binaryData != null) {
      char[] text = new char[binaryData.length << 1];
      int j = 0;
      for (byte b : binaryData) {
        text[j++] = HEX_DIGITS[(b & 0xf0) >> 4];
        text[j++] = HEX_DIGITS[b & 0x0f];
      }
      return new String(text);
    }
    return null;
  }


  /**
   * Converts a single (unicode) char to a byte-array.
   * @param c the character
   * @return the byte[2] array
   */
  public static byte[] toBytes(char c)  {
    return new byte[] { (byte)(c & 0xff), (byte)((c >> 8) & 0xff) };
  }


  /**
   * Converts a char-array to a byte-array.
   * @param chars the character array
   * @return the byte array, empty if chars is null
   */
  public static byte[] toBytes(char[] chars)  {
    if (chars != null) {
      byte[] b = new byte[chars.length << 1];
      int j = 0;
      for (char c : chars) {
        b[j++] = (byte) (c & 0xff);
        b[j++] = (byte) ((c >> 8) & 0xff);
      }
      return b;
    }
    return EMPTY_BYTES;
  }


  /**
   * Builds a hash from an array of chars.<br>
   * Note that this method converts the characters to bytes via {@link #toBytes(char[])}
   * before applying the hash.
   *
   * @param algorithm the hashing algorithm (MD5, SHA-1, SHA-256 are supported by all java runtimes, at least)
   * @param salt the "salt", null if plain hash
   * @param input is the input array of chars
   * @return the hash as a string, null if input == null
   */
  public static String hash(String algorithm, char[] salt, char[] input) {
    if (input != null) {
      try {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] inputBytes = toBytes(input);
        if (salt != null && salt.length > 0 && input.length > 0) {
          byte[] saltyBytes = toBytes(salt);
          byte[] saltyInputBytes = new byte[saltyBytes.length + inputBytes.length];
          System.arraycopy(saltyBytes, 0, saltyInputBytes, 0, saltyBytes.length);
          System.arraycopy(inputBytes, 0, saltyInputBytes, saltyBytes.length, inputBytes.length);
          inputBytes = saltyInputBytes;
        }
        return toHexString(md.digest(inputBytes));
      }
      catch (NoSuchAlgorithmException e)  {
        throw new TentackleRuntimeException("hashing failed", e);
      }
    }
    return null;
  }


  /**
   * Converts a value to a loggable string.
   *
   * @param object the object
   * @return the string
   */
  public static String objectToLoggableString(Object object) {
    if (object == null) {
      return "<null>";
    }
    else  {
      if (object instanceof String str) {
        StringBuilder buf = new StringBuilder();
        buf.append('"');
        if (str.indexOf('\n') >= 0) {
          // multiline
          buf.append(firstLine(str));
          buf.append("...");
        }
        else  {
          buf.append(str);
        }
        buf.append('"');
        return buf.toString();
      }
      else if (maxLogCollectionSize > 0 && object instanceof Collection &&
               ((Collection<?>) object).size() > maxLogCollectionSize) {
        return "[" + ((Collection<?>) object).size() + " items]";
      }
      else {
        return object.toString();
      }
    }
  }


  /**
   * Counts the number of occurrences of a given character in a string.
   *
   * @param str the string to test
   * @param c the character to check for
   * @return the number of times c is part of str
   */
  public static int countOccurrences(String str, char c) {
    int count = 0;
    if (str != null && !str.isEmpty()) {
      int ndx = 0;
      while (ndx >= 0) {
        ndx = str.indexOf(c, ndx + 1);
        if (ndx > 0) {
          count++;
        }
      }
    }
    return count;
  }


  /**
   * Creates a string from an object array.
   *
   * @param objArray the array of objects
   * @param separator the string between two objects
   * @return the string
   */
  public static String objectArrayToString(Object[] objArray, String separator) {
    StringBuilder buf = new StringBuilder();
    if (objArray != null) {
      boolean addSeparator = false;
      for (Object obj: objArray) {
        if (addSeparator) {
          buf.append(separator);
        }
        else {
          addSeparator = true;
        }
        buf.append(objectToLoggableString(obj));
      }
    }
    return buf.toString();
  }


  /**
   * Null-safe string to a char-array conversion.
   *
   * @param str the string, may be null
   * @return the character array, null if str was null
   * @see String#toCharArray()
   */
  public static char[] toCharArray(String str) {
    return str == null ? null : str.toCharArray();
  }

  /**
   * Null-safe char array filler.
   *
   * @param buf the char buffer, may be null
   * @param val the value to fill
   * @see Arrays#fill(char[], char)
   */
  public static void fill(char[] buf, char val) {
    if (buf != null) {
      Arrays.fill(buf, val);
    }
  }

  /**
   * Null-safe char array filler.<br>
   * Fills with blanks.
   *
   * @param buf the char buff, may be null
   * @see #fill(char[], char)
   */
  public static void blank(char[] buf) {
    fill(buf,' ');
  }


  /**
   * Converts a multiline string to an HTML-string that
   * can be displayed in a label.
   * Useful to print multiline labels.
   *
   * @param text the input string
   * @return the HTML string
   */
  public static String toHTML(String text) {
    StringBuilder buf = new StringBuilder("<HTML>");
    if (text != null) {
      buf.append(text.replace("\n", "<BR>"));
    }
    buf.append("</HTML>");
    return buf.toString();
  }


  /**
   * Removes a trailing text from a string.<br>
   * Stops when the first trailing string is found.
   *
   * @param str the original string
   * @param trail the trailing string(s) to remove
   * @return the shortened text, str if nothing removed
   */
  public static String removeTrailingText(String str, String... trail) {
    if (str != null && trail != null) {
      for (String t : trail) {
        if (str.endsWith(t)) {
          str = str.substring(0, str.length() - t.length());
          break;
        }
      }
    }
    return str;
  }



  /**
   * Converts to string containing only the letters A-z or digits.<br>
   * All other characters will be converted to an underscore.
   * All diacrits will be converted first (see {@link StringNormalizer#unDiacrit}).
   * The resulting string will not contain more than one underscore in a row.
   * <p>
   * Nice to create filenames.
   *
   * @param str the string
   * @return the filename
   */
  public static String toAsciiLetterOrDigit(String str) {
    if (str != null) {
      StringBuilder buf = new StringBuilder(StringNormalizer.getInstance().unDiacrit(str, false));  // translate all diacrits
      // convert everything that is not a digit or [a-z][A-Z] to an underscore
      // avoiding more than one underscore in a row.
      boolean wasUnderscore = false;
      for (int i=0; i < buf.length(); i++) {
        char c = buf.charAt(i);
        if (c == '_' ||
            ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z') && (c < '0' || c > '9'))) {
          if (wasUnderscore) {
            buf.deleteCharAt(i--);
          }
          else  {
            buf.setCharAt(i, '_');
            wasUnderscore = true;
          }
        }
        else  {
          wasUnderscore = false;
        }
      }
      return buf.toString();
    }
    return null;
  }


  /**
   * Gets the name of the operating system from the system properties.
   *
   * @return the normalized platform name
   */
  public static String getPlatform() {
    String osName = System.getProperty("os.name");
    if (osName == null) {
      throw new TentackleRuntimeException("missing system property 'os.name'");
    }
    osName = osName.trim().toLowerCase(Locale.ROOT);
    StringBuilder buf = new StringBuilder();
    boolean isWindows = osName.contains("win");
    for (int i = 0; i < osName.length(); i++) {
      char c = osName.charAt(i);
      if (Character.isLetter(c)) {
        buf.append(c);
      }
      else if (isWindows) {
        break;    // cut xp, 7, 10, whatever
      }
    }
    return buf.toString();

  }


  /**
   * Gets the name of the architecture from the system properties.
   *
   * @return the normalized architecture name
   */
  public static String getArchitecture() {
    String osArch = System.getProperty("os.arch");
    if (osArch == null) {
      throw new TentackleRuntimeException("missing system property 'os.arch'");
    }
    return osArch.trim().toLowerCase(Locale.ROOT);
  }


  /**
   * Gets the uppercase letters only.<br>
   * Useful to get the camel case letters of a string.
   *
   * @param str the string
   * @return the upper case letters
   */
  public static String filterUpperCase(String str) {
    if (str != null) {
      StringBuilder buf = new StringBuilder();
      for (int i = 0; i < str.length(); i++) {
        char c = str.charAt(i);
        if (Character.isUpperCase(c)) {
          buf.append(c);
        }
      }
      return buf.toString();
    }
    return null;
  }


  /**
   * Evaluates a string replacing variables.<br>
   * The semantics are very much the same as known from the maven resources plugin, but
   * with the extension of optional default values and that <code>@</code> turns off nested variable processing.
   * <p>
   * Syntax: <code>$|&#64;{name[?|!default]}</code>, where <code>name</code> is the variable name
   * and <code>default</code> is the default value to be used if there is no such variable (!) or
   * variable is empty (@{link {@link #isAllWhitespace(String)}}) or doesn't exist (?).
   * <p>
   * Characters can be quoted with a backslash.
   * A double backslash is treated as a single backslash.
   * <p>
   * Examples:
   * <pre>
   *    &#64;{user.home}/somedir
   *    &#64;{user.home?/tmp}/somedir
   *    &#64;{user.home!/tmp}/somedir
   * </pre>
   * Notice that <code>${...}</code> may lead to unexpected results, if the variable's value contains backslashes,
   * as with windows path names, for example.
   *
   * @param str the string
   * @param variableProvider the variables as properties
   * @return the processed string
   */
  public static String evaluate(String str, Function<String,String> variableProvider) {
    return evaluateImpl(str, variableProvider, new HashSet<>());
  }

  /**
   * Recursive workhorse for {@link #evaluate(String, Function)}.
   *
   * @param str the string
   * @param variableProvider the variables as properties
   * @param processedVariables set of recursively processed variables to detect endless loops
   * @return the processed string
   */
  private static String evaluateImpl(String str, Function<String,String> variableProvider, Set<String> processedVariables) {

    StringBuilder buf = new StringBuilder();  // output buffer
    StringBuilder variableName = null;        // holds the name of the variable, null if not parsing a variable
    char varLead = '@';                       // '$' with nesting, '@' without nesting
    char defLead = '?';                       // '?' empty or missing, '!' missing only
    StringBuilder defaultValue = null;        // holds the default value, null not parsing a default value
    boolean quoted = false;                   // true if next character is quoted

    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (quoted) {
        if (variableName == null) {
          buf.append(c);
        }
        else {
          if (defaultValue == null) {
            variableName.append(c);
          }
          else {
            defaultValue.append(c);
          }
        }
        quoted = false;
      }
      else {
        if (c == '\\') {
          quoted = true;
        }
        else {
          if (variableName == null) {
            if (c == '$' || c == '@') {
              if (i < str.length() - 1 && str.charAt(i + 1) == '{') {
                variableName = new StringBuilder();
                varLead = c;
                ++i;
              }
              else {
                buf.append(c);
              }
            }
            else {
              buf.append(c);
            }
          }
          else {
            if (c == '}') {
              String key = variableName.toString();
              String value = variableProvider.apply(key);
              if (value != null && (defaultValue == null || defLead == '!' || !isAllWhitespace(value))) {
                if (varLead == '$') {
                  Set<String> variableBranch = new HashSet<>(processedVariables);
                  if (variableBranch.add(key)) {
                    value = evaluateImpl(value, variableProvider, variableBranch);
                  }
                }
                buf.append(value);
              }
              else {
                if (defaultValue != null) {
                  buf.append(defaultValue);
                }
                else {
                  // no such variable and no default
                  buf.append(varLead).append('{').append(variableName).append('}');
                }
              }
              // else: treat as empty
              variableName = null;
              defaultValue = null;
            }
            else {
              if ((c == '?' || c == '!') && defaultValue == null) {
                defaultValue = new StringBuilder();
                defLead = c;
              }
              else {
                if (defaultValue == null) {
                  variableName.append(c);
                }
                else {
                  defaultValue.append(c);
                }
              }
            }
          }
        }
      }
    }
    if (variableName != null) {
      // missing closing }
      buf.append(varLead).append('{').append(variableName);
      if (defaultValue != null) {
        buf.append(defLead).append(defaultValue);
      }
    }
    return buf.toString();
  }


  /**
   * Determines the Levenshtein distance of two strings.<br>
   * Notice that for s1 and s2 null values are treated as empty strings.
   *
   * @param s1 the first string
   * @param s2 the second string
   * @return the distance
   */
  public static int levenshteinDistance(String s1, String s2) {
    if (s1 == null) {
      s1 = "";
    }
    if (s2 == null) {
      s2 = "";
    }
    int[][] d = new int[s1.length() + 1][s2.length() + 1];

    for (int i1 = 0; i1 <= s1.length(); i1++) {
      for (int i2 = 0; i2 <= s2.length(); i2++) {
        if (i1 == 0) {
          d[i1][i2] = i2;
        }
        else if (i2 == 0) {
          d[i1][i2] = i1;
        }
        else {
          d[i1][i2] = Math.min(d[i1 - 1][i2 - 1] + (s1.charAt(i1 - 1) == s2.charAt(i2 - 1) ? 0 : 1),
                               Math.min(d[i1 - 1][i2] + 1, d[i1][i2 - 1] + 1));
        }
      }
    }

    return d[s1.length()][s2.length()];
  }


  /**
   * Same as {@link String#startsWith(String, int)}, but for more multiple prefixes.
   *
   * @param str the string to check
   * @param offset where to begin looking in {@code str}
   * @param prefixes the prefixes
   * @return the prefix, null if no match
   */
  public static String startsWithAnyOf(String str, int offset, String... prefixes) {
    if (str != null) {
      for (String prefix : prefixes) {
        if (str.startsWith(prefix, offset)) {
          return prefix;
        }
      }
    }
    return null;
  }


  /**
   * Same as {@link String#startsWith(String)}, but for more multiple prefixes.
   *
   * @param str the string to check
   * @param prefixes the prefixes
   * @return the prefix, null if no match
   */
  public static String startsWithAnyOf(String str, String... prefixes) {
    return startsWithAnyOf(str, 0, prefixes);
  }


  /**
   * Same as {@link String#endsWith(String)}, but for more multiple suffixes.
   *
   * @param str the string to check
   * @param suffixes the suffixes
   * @return the suffix, null if no match
   */
  public static String endsWithAnyOf(String str, String... suffixes) {
    if (str != null) {
      for (String suffix : suffixes) {
        if (str.endsWith(suffix)) {
          return suffix;
        }
      }
    }
    return null;
  }


  /**
   * prevent instantiation.
   */
  private StringHelper() {}

}
