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
public class SeparatorSuggestionGeneratorTest {

  private static final List<String> CHOICES = List.of("alpha-beta-gamma", "zzz-bb-cc", "xx-baa-bbc-caa");

  @Test
  public void autoComplete() {

    SeparatorSuggestionGenerator<String> autoCompletion = new SeparatorSuggestionGenerator<>("-");
    assertTrue(autoCompletion.apply("alpha").isEmpty());    // no choices so far!

    autoCompletion.setChoices(CHOICES);
    List<List<SubString>> aa = autoCompletion.apply("a-b");
    assertEquals(aa.size(), 2);
    assertEquals(aa.get(0).size(), 2);
    assertEquals(aa.get(0).get(0).getSegment(), "a");   // alpha
    assertEquals(aa.get(0).get(0).getBeginIndex(), 0);
    assertEquals(aa.get(0).get(0).getEndIndex(), 1);
    assertEquals(aa.get(0).get(1).getSegment(), "b");   // beta
    assertEquals(aa.get(0).get(1).getBeginIndex(), 6);
    assertEquals(aa.get(0).get(1).getEndIndex(), 7);
    assertEquals(aa.get(1).size(), 2);
    assertEquals(aa.get(1).get(0).getSegment(), "a");   // baa
    assertEquals(aa.get(1).get(0).getBeginIndex(), 4);
    assertEquals(aa.get(1).get(0).getEndIndex(), 5);
    assertEquals(aa.get(1).get(1).getSegment(), "b");   // bbc
    assertEquals(aa.get(1).get(1).getBeginIndex(), 7);
    assertEquals(aa.get(1).get(1).getEndIndex(), 8);

    List<List<SubString>> bbb = autoCompletion.apply("-BETA");
    assertEquals(bbb.size(), 1);
    assertEquals(bbb.get(0).size(), 1);
    assertEquals(bbb.get(0).get(0).getSegment(), "beta");
    assertEquals(bbb.get(0).get(0).getBeginIndex(), 6);
    assertEquals(bbb.get(0).get(0).getEndIndex(), 10);

    autoCompletion.setCaseSensitive(true);
    bbb = autoCompletion.apply("-BETA");
    assertTrue(bbb.isEmpty());
  }

}
