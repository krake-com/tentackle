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
import java.util.function.Function;

/**
 * The default normalizer (works sufficiently for most western languages).
 *
 * @author harald
 */
public class DefaultStringNormalizer implements Function<String,String> {


  /** to separate words during reduction. */
  private static final char WORD_SEPARATOR = ' ';


  /** character to separate text blocks. */
  private final char fieldSeparator;



  /**
   * Creates a normalizer.
   *
   * @param fieldSeparator separator between text fields, 0 if none
   */
  public DefaultStringNormalizer(char fieldSeparator) {
    this.fieldSeparator = fieldSeparator;
  }

  /**
   * Creates normalizer.<br>
   * With a comma as the field separator.
   */
  public DefaultStringNormalizer() {
    this(',');
  }



  /**
   * Normalizes a string (phonetically) for use as PDO.normText.
   *
   * @param str the string to be normalized
   * @return the normalized string
   */
  @Override
  public String apply(String str)  {

    str = StringHelper.unDiacrit(str, false);     // convert special characters and umlauts

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
          c = WORD_SEPARATOR;    // keep space to separate words and inhibit reduction below
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
          case 'G':
            c = 'K';
            break;
          case 'C':
            c = 'K';
            break;
          case 'Y':
            c = 'I';
            break;
          case 'B':
            c = 'P';
            break;
          case 'Q':
            c = 'K';
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

      len = sb.length();

      for (int i = 0; i < len; i++) {

        char c = sb.charAt(i);

        if (i + 1 < len)  {

          // solve the Mayer, Maier, Meier, Meyer problem
          if (c == 'A' && sb.charAt(i+1) == 'I')  {
            sb.setCharAt(i, 'E');    /* AI,AY -> EI */
          }

          // remove repeating characters (except for numbers!)
          if (Character.isLetter(c) && c == sb.charAt(i+1)) {
            sb.deleteCharAt(i+1);
            len--;
            continue;
          }

          // map IE to I
          if (c == 'I' && sb.charAt(i+1) == 'E')  {
            sb.deleteCharAt(i+1);
            len--;
            continue;
          }
        }

        // vowel + H + consonant --> remove the H
        if (i + 1 < len)  {
          char c2 = sb.charAt(i+1);
          char c3 = i + 2 < len ? sb.charAt(i+2) : ' ';
          if ((c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U') &&
              c2 == 'H' &&
              (c3 != 'A' || c3 != 'E' || c3 != 'I' || c3 != 'O' || c3 != 'U')) {
            sb.deleteCharAt(i+1);
            len--;
          }
        }
      }

      // remove word separators inserted above
      len = sb.length();
      if (len > 0) {
        StringBuilder fb = new StringBuilder(len);
        for (int i=0; i < len; i++) {
          char c = sb.charAt(i);
          if (c != WORD_SEPARATOR) {
            fb.append(c);
          }
        }
        return fb.toString();
      }
      else  {
        return "";
      }
    }

    return null;
  }


}
