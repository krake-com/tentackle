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
 * License along with this library; if not, write to the DFree Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */



package org.tentackle.misc;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.tentackle.common.BasicStringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;


/**
 * Some handy methods for strings.
 *
 * @author  harald
 */
public final class StringHelper {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(StringHelper.class);


  /**
   * Limit size of collection to be logged.<br>
   * If an object is logged and it is a collection
   * and the size exceeds {@code maxLogCollectionSize}, only the number
   * of items is logged in {@link #objectToLoggableString}
   * instead of each item.
   */
  public static int maxLogCollectionSize = 10;

  // fixed maps for converting single-char diacrits
  private static final String CRITS = "ÄÀÁÂÃÅÆÇÈÉÊËÌÍÎÏÐÑÖÒÓÔÕ×ØÜÙÚÛÝÞäàáâãåæçèéêëìíîïñöðòóôõøüùúûýþÿß";
  private static final String NORMS = "AAAAAAACEEEEIIIIDNOOOOOXOUUUUYBaaaaaaaceeeeiiiinooooooouuuuybys";
  private static char[] critMap;    // fast map built once from CRITS and NORMS

  private static final byte[] EMPTY_BYTES = new byte[0];


  /**
   * Converts special unicode characters (so-called diacrits) to standard ascii.<br>
   * Supports also special german and northern european "umlauts".
   *
   * @param str the string to be converted
   * @param keepLength true if the length should be kept, i.e. no Ä to AE, but to A
   * @return the converted string
   */
  public static String unDiacrit(String str, boolean keepLength)  {

    // build maptable if not yet done
    int mapLen;
    try {
      mapLen = critMap.length >> 1;    // causes Nullp if not prepared
    }
    catch (NullPointerException e)  {
      // prepare fast map
      mapLen = CRITS.length();
      if (mapLen != NORMS.length()) {
        throw new IllegalStateException();
      }
      critMap = new char[mapLen + mapLen];
      for (int i=0; i < mapLen; i++) {
        critMap[i]          = CRITS.charAt(i);
        critMap[i + mapLen] = NORMS.charAt(i);
      }
    }

    if (str != null)  {

      StringBuilder buf = new StringBuilder();
      int len = str.length();

      for (int i=0; i < len; i++) {

        char c = str.charAt(i);

        if (!keepLength) {
          // check for german umlauts, ß and alike
          if (c == 'Ä' || c == 'Æ') {
            buf.append(i < len-1 && Character.isUpperCase(str.charAt(i+1)) ? "AE" : "Ae");
            continue;
          }
          else if (c == 'Ü')  {
            buf.append(i < len-1 && Character.isUpperCase(str.charAt(i+1)) ? "UE" : "Ue");
            continue;
          }
          else if (c == 'Ö')  {
            buf.append(i < len-1 && Character.isUpperCase(str.charAt(i+1)) ? "OE" : "Oe");
            continue;
          }
          else if (c == 'ä' || c == 'æ') {
            buf.append("ae");
            continue;
          }
          else if (c == 'ü') {
            buf.append("ue");
            continue;
          }
          else if (c == 'ö') {
            buf.append("oe");
            continue;
          }
          else if (c == 'ß') {
            buf.append("ss");
            continue;
          }
        }

        // convert possible diacrit (single char)
        for (int m=0; m < mapLen; m++) {
          if (critMap[m] == c)  {
            c = critMap[m+mapLen];
            break;
          }
        }

        if (c >= 32 && c <= 127)  {
          // append legal character
          buf.append(c);
        }
        else if (keepLength) {
          buf.append('?');
        }
      }
      return buf.toString();
    }
    return null;
  }



  /**
   * The default normalizer used within the application.
   * Defaults to DefaultStringNormalizer.
   */
  public static Function<String,String> stringNormalizer = new DefaultStringNormalizer();

  /**
   * Normalizes a string (phonetically) for use as PDO.normText.
   *
   * @param str the string to be normalized
   * @param normalizer the normalizer to use, null = global default
   * @return the normalized string
   */
  public static String normalize (String str, Function<String,String> normalizer)  {
    return normalizer == null ? stringNormalizer.apply(str) : normalizer.apply(str);
  }

  /**
   * Normalizes a string (phonetically) for use as PDO.normText
   * using {@link #stringNormalizer}.
   *
   * @param str the string to be normalized
   * @return the normalized string
   */
  public static String normalize (String str)  {
    return normalize(str, null);
  }


  /**
   * Takes a string, surrounds it with double-quotes and escapes all double-quotes
   * already in the string according to Unix rules.
   * Example:
   * <pre>
   * Length 5" --&gt; "Length 5\""
   * </pre>
   *
   * @param str the string
   * @return the string in double quotes
   */
  public static String toDoubleQuotes(String str) {
    return BasicStringHelper.toDoubleQuotes(str);
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
    return BasicStringHelper.parseString(str);
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
    return BasicStringHelper.split(str, delimiters);
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
        buf.append(str.substring(0, length));
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
        buf.append(str.substring(0, length));
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
   * Filters illegal chars for DTA-Files (German "DATENAUSTAUSCH" banking format).
   *
   * @param str the string
   * @return the DTA conform string
   */
  public static String toDTA(String str)  {
    if (str != null)  {
      str = str.toUpperCase();
      int len = str.length();
      StringBuilder sbuf = new StringBuilder();
      for (int i=0; i < len; i++) {
        char c = str.charAt(i);
        if ((c >= '0' && c <= '9') ||
            (c >= 'A' && c <= 'Z') ||
            c == ' ' || c == '.' || c == ',' ||
            c == '&' || c == '-' || c == '/' ||
            c == '+' || c == '*' || c == '$' ||
            c == '%' || c == 'Ä' || c == 'Ö' ||
            c == 'Ü' || c == 'ß') {
          sbuf.append(c);
        }
        else  {
          sbuf.append(' ');
        }
      }
      str = sbuf.toString();
    }
    return str;
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
   * Trims a string.
   * The method is nullpointer safe.
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
   * Trims a string.
   * The method is nullpointer safe.
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
   * Checks if string contains only whitespaces.
   *
   * @param str the string to check
   * @return true if null, empty or all whitespace, false if at least one non-whitespace-character found
   */
  public static boolean isAllWhitespace(String str) {
    return BasicStringHelper.isAllWhitespace(str);
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
      byte b;

      for (int i=0; i < binaryData.length; ++i) {
        b = binaryData[i];
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
      char c;
      for (int i=0; i < chars.length; ++i)  {
        c = chars[i];
        b[j++] = (byte)(c & 0xff);
        b[j++] = (byte)((c >> 8) & 0xff);
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
      if (object instanceof String) {
        String str = (String) object;
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
               ((Collection) object).size() > maxLogCollectionSize) {
        return "[" + ((Collection) object).size() + " items]";
      }
      else {
        return object.toString();
      }
    }
  }



  /**
   * Converts the first character of string to uppercase.
   *
   * @param str the string
   * @return the converted string
   */
  public static String firstToUpper(String str) {
    return BasicStringHelper.firstToUpper(str);
  }


  /**
   * Converts the first character of string to uppercase.
   *
   * @param str the string
   * @return the converted string
   */
  public static String firstToLower(String str) {
    return BasicStringHelper.firstToLower(str);
  }


  /**
   * Checks whether given string introduces a continuation line.
   * <p>
   * This is the case if the last character is an unquoted backslash.
   * @param line the source line
   * @return null if line does not introduce a continuation line
   */
  public static String getContinuedLine(String line) {
    return BasicStringHelper.getContinuedLine(line);
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
    if (str != null && str.length() > 0) {
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
        buf.append(objectToLoggableString(obj));
        addSeparator = true;
      }
    }
    return buf.toString();
  }



  /**
   * Transforms a string to a char-array.
   * nullp-safe.
   *
   * @param str the string
   * @return the character array
   */
  public static char[] toCharArray(String str) {
    return str == null ? null : str.toCharArray();
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
   * Removes a trailing text from a string.
   *
   * @param str the original string
   * @param trail the trailing string to remove, if found
   * @return the shortened text
   */
  public static String removeTrailingText(String str, String trail) {
    if (str.endsWith(trail)) {
      str = str.substring(0, str.length() - trail.length());
    }
    return str;
  }



  /**
   * Converts to string containing only the letters A-z or digits.<br>
   * All other characters will be converted to an underscore.
   * All diacrits will be converted first (see {@link #unDiacrit}.
   * The resulting string will not contain more than one underscore in a row.
   * <p>
   * Nice to create filenames.
   *
   * @param str the string
   * @return the filename
   */
  public static String toAsciiLetterOrDigit(String str) {
    if (str != null) {
      StringBuilder buf = new StringBuilder(unDiacrit(str, false));  // translate all diacrits
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
   * Checks that given string is a valid Java classname.<br>
   * Both classnames with a full classpath or without are validated.
   *
   * @param className the classname
   * @return true if valid
   */
  public static boolean isValidJavaClassName(String className) {
    return BasicStringHelper.isValidJavaClassName(className);
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
    return BasicStringHelper.isValidJavaPackageName(packageName);
  }


  /**
   * Returns whether the given string is a valid java identifier.
   *
   * @param str the string
   * @return true if valid, false if not
   */
  public static boolean isValidJavaIdentifier(String str) {
    return BasicStringHelper.isValidJavaIdentifier(str);
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
    return BasicStringHelper.readTextFromResource(resourceName);
  }


  /**
   * Returns whether given string is a reserved java keyword.
   *
   * @param word the string to test
   * @return true if java reserved word
   */
  public static boolean isReservedWord(String word) {
    return BasicStringHelper.isReservedWord(word);
  }


  /**
   * Converts a string to uppercase allowing null values.
   *
   * @param str the string
   * @return the uppercase string or null
   */
  public String toUpper(String str) {
    return BasicStringHelper.toUpper(str);
  }


  /**
   * Converts a string to lowercase allowing null values.
   *
   * @param str the string
   * @return the lowercase string or null
   */
  public String toLower(String str) {
    return BasicStringHelper.toLower(str);
  }


  /**
   * Gets the word-string for a digit.<br>
   * Example:
   * <pre>
   * '0' -&gt; "zero"
   * </pre>
   *
   * @param digit the digit character
   * @return the word-string
   */
  public static String digitToString(Character digit) {
    return BasicStringHelper.digitToString(digit);
  }


  /**
   * Converts a string to a string that can be used as a parameter
   * for a LIKE-clause.
   * The following rules apply to the source-string:
   * <ul>
   * <li>"bla"  -&gt; "%bla%"</li>
   * <li>"=bla" -&gt; "bla%"</li>
   * <li>"bla=" -&gt; "%bla"</li>
   * <li>"="    -&gt; "%=%" (pathological case)</li>
   * </ul>
   *
   * @param pattern the search pattern
   * @return the LIKE string
   */
  public static String toLikeString(String pattern) {

    String sqlPattern;    // returned LIKE-string

    if (pattern == null || pattern.length() == 0)  {
      sqlPattern = "%";
    }
    else  {
      if (pattern.compareTo("=") == 0)  {
        // pathological case!
        sqlPattern = "%=%";
      }
      else  {
        sqlPattern = pattern;
      }
    }

    char c = sqlPattern.charAt(0);
    if (c == '=') {
      // cut off leading '=' as it is not SQL-syntax
      sqlPattern = sqlPattern.substring(1);
    }
    else if (c != '%') {
      // prepend LIKE '%' if not '=' given
      sqlPattern = "%" + sqlPattern;
    }
    c = sqlPattern.charAt(sqlPattern.length()-1);
    if (c == '=') {
      // cut off trailing '=' as it is not SQL-syntax
      sqlPattern = sqlPattern.substring(0, sqlPattern.length()-1);
    }
    else if (c != '%') {
      // append LIKE '%' if not '=' given
      sqlPattern += '%';
    }

    return sqlPattern;
  }


  /**
   * Converts a string to a string that can be used as a parameter
   * for a LIKE-clause, nullpointer safe.
   *
   * @param pattern the search pattern
   * @return the LIKE string
   *
   * @see #toLikeString(java.lang.String)
   */
  public static String toLikeStringWithNull(String pattern) {
    if (pattern != null && pattern.trim().length() > 0) {
      return toLikeString(pattern);
    }
    return null;
  }


  /**
   * Converts an SQL-like-String to a regular expression.
   * <pre>
   * Example: "%blah_foo" -&gt; ".*blah.foo"
   * </pre>
   *
   * @param likeString the LIKE-string
   * @return the regex
   */
  public static Pattern likeStringToPattern(String likeString)  {
    StringBuilder regex = new StringBuilder();
    if (likeString != null) {
      int len = likeString.length();
      for (int i=0; i < len; i++) {
        char c = likeString.charAt(i);
        if (c == '%') {
          regex.append(".*");
        }
        else if (c == '_') {
          regex.append(".");
        }
        else  {
          regex.append(c);
        }
      }
    }
    return Pattern.compile(regex.toString());
  }


  /**
   * prevent instantiation.
   */
  private StringHelper() {}

}
