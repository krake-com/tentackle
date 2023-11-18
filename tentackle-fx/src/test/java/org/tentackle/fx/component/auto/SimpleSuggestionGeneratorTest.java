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

package org.tentackle.fx.component.auto;

import org.testng.annotations.Test;

import org.tentackle.misc.SubString;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class SimpleSuggestionGeneratorTest {

  private static final List<String> CHOICES = List.of("aaaabbccc", "zzz", "xxaabbbccaa");

  @Test
  public void autoComplete() {

    SimpleSuggestionGenerator<String> autoCompletion = new SimpleSuggestionGenerator<>();
    assertTrue(autoCompletion.apply("aa").isEmpty());    // no choices so far!

    autoCompletion.setChoices(CHOICES);
    List<List<SubString>> aa = autoCompletion.apply("aa");
    assertEquals(aa.size(), 2);
    assertEquals(aa.get(0).size(), 1);
    assertEquals(aa.get(0).get(0).getSegment(), "aa");
    assertEquals(aa.get(0).get(0).getBeginIndex(), 0);
    assertEquals(aa.get(0).get(0).getEndIndex(), 2);
    assertEquals(aa.get(1).size(), 1);
    assertEquals(aa.get(1).get(0).getSegment(), "aa");
    assertEquals(aa.get(1).get(0).getBeginIndex(), 2);
    assertEquals(aa.get(1).get(0).getEndIndex(), 4);

    List<List<SubString>> bbb = autoCompletion.apply("BBB");
    assertEquals(bbb.size(), 1);
    assertEquals(bbb.get(0).size(), 1);
    assertEquals(bbb.get(0).get(0).getSegment(), "bbb");
    assertEquals(bbb.get(0).get(0).getBeginIndex(), 4);
    assertEquals(bbb.get(0).get(0).getEndIndex(), 7);

    autoCompletion.setCaseSensitive(true);
    bbb = autoCompletion.apply("BBB");
    assertTrue(bbb.isEmpty());
  }

}
