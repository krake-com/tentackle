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

interface StringNormalizerHolder {
  StringNormalizer INSTANCE = ServiceFactory.createService(StringNormalizer.class, DefaultStringNormalizer.class);
}

/**
 * Translates strings to a reduced char set.
 * <p>
 * The default implementation is {@link DefaultStringNormalizer}.
 * Applications can replace it via {@code @Service(StringNormalizer.class)}.
 */
public interface StringNormalizer {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static StringNormalizer getInstance() {
    return StringNormalizerHolder.INSTANCE;
  }

  /**
   * Normalizes a string (phonetically) for use as PDO.normText.
   *
   * @param str the string to be normalized
   * @return the normalized string, null if str is null
   */
  String normalize(String str);


  /**
   * Converts special Unicode characters (so-called diacritics) to standard ASCII.<br>
   * Supports also special German and Northern European "umlauts".
   * <p>
   * Characters that cannot be converted to the printable ASCII range are dropped, unless
   * {@code keepLength} is true, in which case they are replaced by a question mark.
   *
   * @param str the string to be converted
   * @param keepLength true if the length should be kept, i.e., no Ä to AE, but to A
   * @return the converted string, null if str is null
   */
  String unDiacrit(String str, boolean keepLength);

}
