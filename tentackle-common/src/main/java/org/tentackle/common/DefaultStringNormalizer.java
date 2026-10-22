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

package org.tentackle.common;

import java.util.Locale;

/**
 * The default normalizer (works sufficiently for most Western languages).
 * <p>
 * {@link #normalize(String)} converts diacritics and umlauts, converts to uppercase, keeps only letters, digits
 * and the field separator, and applies some phonetic reductions, such as {@code SCH} to {@code S},
 * {@code CH} to {@code K}, repeated letters to a single one, {@code IE} to {@code I} and dropping the {@code H}
 * between a vowel and a consonant. Words are concatenated, while the field separator is kept
 * (repeated separators collapse into one).
 * <p>
 * Notice that changing the normalization rules changes the normalized texts persisted in the database.
 *
 * @author harald
 */
// @Service does not work in tentackle-common, since the analyze goal of the tentackle-maven-plugin is not available at this build step yet.
// Needs explicit config in src/main/resources/META-INF/services.
@Service(StringNormalizer.class)
public class DefaultStringNormalizer implements StringNormalizer {

  // fixed maps for converting single-char diacritics and special letters (both same length!)
  private static final char[] CRITS = "ÄÀÁÂÃÅÆÇÈÉÊËÌÍÎÏÐÑÖÒÓÔÕ×ØÜÙÚÛÝÞäàáâãåæçèéêëìíîïñöðòóôõøüùúûýþÿß".toCharArray();
  private static final char[] NORMS = "AAAAAAACEEEEIIIIDNOOOOOXOUUUUYTaaaaaaaceeeeiiiinodooooouuuuytys".toCharArray();

  // all CRITS are within the upper half of Latin-1: direct lookup instead of a linear search
  private static final char FIRST_CRIT = 'À';
  private static final char LAST_CRIT = 'ÿ';
  private static final char[] CRIT_TABLE = createCritTable();    // 0 if no mapping

  private static char[] createCritTable() {
    char[] table = new char[LAST_CRIT - FIRST_CRIT + 1];
    for (int m = 0; m < CRITS.length; m++) {
      char c = CRITS[m];
      if (c < FIRST_CRIT || c > LAST_CRIT) {
        throw new TentackleRuntimeException("diacritic '" + c + "' out of table range");
      }
      if (table[c - FIRST_CRIT] == 0) {   // first mapping wins, as with the linear search
        table[c - FIRST_CRIT] = NORMS[m];
      }
    }
    return table;
  }


  /** to separate words during reduction. */
  private final char wordSeparator;


  /** character to separate text blocks. */
  private final char fieldSeparator;



  /**
   * Creates a normalizer.
   *
   * @param fieldSeparator separator between text fields, 0 if none
   * @param wordSeparator separator between words during reduction
   */
  public DefaultStringNormalizer(char fieldSeparator, char wordSeparator) {
    this.fieldSeparator = fieldSeparator;
    this.wordSeparator = wordSeparator;
  }

  /**
   * Creates a normalizer.<br>
   * With a comma as the field separator and space as word separator.
   */
  public DefaultStringNormalizer() {
    this(',', ' ');
  }


  @Override
  public String unDiacrit(String str, boolean keepLength)  {

    if (str != null)  {

      int len = str.length();
      StringBuilder buf = new StringBuilder(len + 8);   // a few umlauts expand to 2 chars

      for (int i=0; i < len; i++) {

        char c = str.charAt(i);

        if (!keepLength) {
          // check for german umlauts, ß and alike
          switch (c) {
            case 'Ä':
            case 'Æ':
              buf.append(i < len-1 && Character.isUpperCase(str.charAt(i+1)) ? "AE" : "Ae");
              continue;

            case 'Ü':
              buf.append(i < len-1 && Character.isUpperCase(str.charAt(i+1)) ? "UE" : "Ue");
              continue;

            case 'Ö':
              buf.append(i < len-1 && Character.isUpperCase(str.charAt(i+1)) ? "OE" : "Oe");
              continue;

            case 'ä':
            case 'æ':
              buf.append("ae");
              continue;

            case 'ü':
              buf.append("ue");
              continue;

            case 'ö':
              buf.append("oe");
              continue;

            case 'ß':
              buf.append("ss");
              continue;

            default:
              break;
          }
        }

        // convert possible diacritic (single char)
        if (c >= FIRST_CRIT && c <= LAST_CRIT) {
          char norm = CRIT_TABLE[c - FIRST_CRIT];
          if (norm != 0) {
            c = norm;
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
   * Normalizes a string (phonetically) for use as PDO.normText.
   *
   * @param str the string to be normalized
   * @return the normalized string, null if str is null
   */
  @Override
  public String normalize(String str)  {

    str = unDiacrit(str, false);     // convert special characters and umlauts

    if (str != null) {

      // convert to uppercase
      str = str.toUpperCase(Locale.ENGLISH);    // should work in all locales because of unDiacrit

      int len = str.length();
      StringBuilder sb = new StringBuilder (len);
      boolean lastCharWasSpace = false;
      boolean lastCharWasFieldSeparator = false;

      for (int i=0; i < len; i++)  {

        char c = str.charAt(i);

        if (Character.isLetterOrDigit(c)) {
          // keep only letters and digits
          lastCharWasSpace = false;
          lastCharWasFieldSeparator = false;
        }
        else if (c == fieldSeparator && c != 0) {
          if (lastCharWasFieldSeparator) {
            continue;
          }
          lastCharWasFieldSeparator = true;
          lastCharWasSpace = false;
        }
        else {
          if (lastCharWasSpace) {
            // ignore more than one invalid char between words
            continue;
          }
          lastCharWasSpace = true;
          lastCharWasFieldSeparator = false;
          c = wordSeparator;    // keep space to separate words and inhibit reduction below
        }

        /*
         * convert certain character sequences.
         * This should work with all western languages sufficiently.
         */
        if (i + 2 < len && c == 'S' && str.charAt(i+1) == 'C' && str.charAt(i+2) == 'H') {
          // sch -> s
          i += 2;
        }
        if (i + 1 < len)  {
          if (c == 'C' && str.charAt(i+1) == 'H') {
            // ch -> k
            c = 'K';
            i++;
          }
          else if (c == 'C' && str.charAt(i+1) == 'K')  {
            // ck -> k
            c = 'K';
            i++;
          }
          else if (c == 'P' && str.charAt(i+1) == 'H')  {
            // ph -> f
            c = 'F';
            i++;
          }
          else if (c == 'T' && str.charAt(i+1) == 'H')  {
            // th -> t
            c = 'T';
            i++;
          }
        }

        /*
         * Map certain characters phonetically.
         * This should work with all western languages sufficiently.
         */
        switch (c) {
          case 'D':
            c = 'T';
            break;

          case 'C':
          case 'G':
          case 'Q':
            c = 'K';
            break;

          case 'Y':
            c = 'I';
            break;

          case 'B':
            c = 'P';
            break;

          case 'W':
            c = 'V';
            break;

          case 'Z':
            c = 'S';
            break;

          default:
            break;
        }

        sb.append(c);
      }


      // next round...
      return reduce(sb);
    }

    return null;
  }


  /**
   * Applies the second round of reductions and removes the word separators.
   * <p>
   * Logically, the reductions are applied to a buffer from which characters following the
   * current one are deleted. Instead of shifting the buffer on each deletion, the buffer is split into the
   * already processed part {@code out[0..o]}, where {@code out[o]} is the current character,
   * and the unprocessed rest {@code in[j..n-1]}. Deleting the character following the current one is just
   * skipping {@code in[j]}, which keeps the reduction linear.
   *
   * @param sb the result of the first round
   * @return the normalized string
   */
  private String reduce(StringBuilder sb) {
    int n = sb.length();
    if (n == 0) {
      return "";
    }
    char[] in = new char[n];
    sb.getChars(0, n, in, 0);
    char[] out = new char[n];
    out[0] = in[0];
    int o = 0;    // index of the current character in out
    int j = 1;    // index of the character following the current one in in

    while (true) {
      char c = out[o];

      if (j < n) {
        char next = in[j];

        // solve the Mayer, Maier, Meier, Meyer problem
        if (c == 'A' && next == 'I') {
          out[o] = 'E';    /* AI,AY -> EI */
        }

        // remove repeating characters (except for numbers!), or map IE to I
        if ((Character.isLetter(c) && c == next) || (c == 'I' && next == 'E')) {
          j++;
        }
        // vowel + H + consonant --> remove the H
        else if ((c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U') && next == 'H') {
          char c3 = j + 1 < n ? in[j + 1] : ' ';
          if (c3 != 'A' && c3 != 'E' && c3 != 'I' && c3 != 'O' && c3 != 'U') {
            j++;
          }
        }
      }

      if (j >= n) {
        break;
      }
      out[++o] = in[j++];    // next character becomes the current one
    }

    // remove word separators inserted above
    int len = 0;
    for (int i = 0; i <= o; i++) {
      char c = out[i];
      if (c != wordSeparator) {
        out[len++] = c;
      }
    }
    return new String(out, 0, len);
  }


}
