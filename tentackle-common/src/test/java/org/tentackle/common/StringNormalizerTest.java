/*
 * Tentackle - https://tentackle.org.
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

import org.testng.annotations.Test;

import java.util.Random;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * String normalizer test.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class StringNormalizerTest {

  @Test
  public void testNormalizer() {
    assertEquals(StringNormalizer.getInstance().normalize(" Mülleralia-Intercom & Co.KG   , GREEN"), "MUELERALIAINTERKOMKOK,KREN");
  }

  @Test
  public void testHBeforeVowelIsKept() {
    StringNormalizer normalizer = StringNormalizer.getInstance();
    // vowel + H + consonant: H is removed
    assertEquals(normalizer.normalize("Mahler"), "MALER");
    // vowel + H + vowel: H is kept
    assertEquals(normalizer.normalize("Johann"), "JOHAN");
  }

  @Test
  public void testDiacriticsMap() {
    StringNormalizer normalizer = StringNormalizer.getInstance();
    assertEquals(normalizer.unDiacrit("Ðð", true), "Dd");
    assertEquals(normalizer.unDiacrit("Þþ", true), "Tt");
    assertEquals(normalizer.unDiacrit("éñø", true), "eno");
  }

  @Test
  public void testSameAsReference() {
    // the normalized texts are persisted: the optimized implementation must not change any result
    String alphabet = "AEIOUaeiouHhSCHchKkPpTtYyDdGgQqBbWwZzLMNRlmnr0123456789  ,,.-&'" +
                      "ÄÖÜäöüßÆæÀÁÂÃÅÇÈÉÊËÌÍÎÏÐÑÒÓÔÕ×ØÙÚÛÝÞàáâãåçèéêëìíîïðñòóôõøùúûýþÿ÷ΩЖ€\t\n";
    StringNormalizer[][] pairs = {
      { new DefaultStringNormalizer(), new ReferenceStringNormalizer() },
      { new DefaultStringNormalizer('\0', ' '), new ReferenceStringNormalizer('\0', ' ') },
      { new DefaultStringNormalizer(';', '_'), new ReferenceStringNormalizer(';', '_') }
    };
    Random random = new Random(4711);
    for (int run = 0; run < 200_000; run++) {
      int length = random.nextInt(run % 100 == 0 ? 200 : 16);
      StringBuilder buf = new StringBuilder(length);
      for (int i = 0; i < length; i++) {
        buf.append(alphabet.charAt(random.nextInt(alphabet.length())));
      }
      String str = buf.toString();
      for (StringNormalizer[] pair : pairs) {
        assertEquals(pair[0].normalize(str), pair[1].normalize(str), "normalize <" + str + ">");
      }
      assertEquals(pairs[0][0].unDiacrit(str, true), pairs[0][1].unDiacrit(str, true), "unDiacrit keep <" + str + ">");
      assertEquals(pairs[0][0].unDiacrit(str, false), pairs[0][1].unDiacrit(str, false), "unDiacrit <" + str + ">");
    }
    assertNull(new DefaultStringNormalizer().normalize(null));
    assertEquals(new DefaultStringNormalizer().normalize(""), "");
  }

}
