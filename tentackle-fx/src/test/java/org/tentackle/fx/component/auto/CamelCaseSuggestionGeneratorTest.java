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
public class CamelCaseSuggestionGeneratorTest {

  private static final List<String> CHOICES = List.of("ZzzBbCc", "XxAlaBbcCC", "AlphaBetaGamma");

  @Test
  public void autoComplete() {

    CamelCaseSuggestionGenerator<String> autoCompletion = new CamelCaseSuggestionGenerator<>();
    assertTrue(autoCompletion.apply("Alpha").isEmpty());    // no choices so far!

    autoCompletion.setChoices(CHOICES);
    List<List<SubString>> aa = autoCompletion.apply("AlB");
    assertEquals(aa.size(), 2);
    assertEquals(aa.get(0).size(), 2);
    assertEquals(aa.get(0).get(0).getSegment(), "Al");   // Alpha
    assertEquals(aa.get(0).get(0).getBeginIndex(), 0);
    assertEquals(aa.get(0).get(0).getEndIndex(), 2);
    assertEquals(aa.get(0).get(1).getSegment(), "B");   // Beta
    assertEquals(aa.get(0).get(1).getBeginIndex(), 5);
    assertEquals(aa.get(0).get(1).getEndIndex(), 6);
    assertEquals(aa.get(1).size(), 2);
    assertEquals(aa.get(1).get(0).getSegment(), "Al");   // Ala
    assertEquals(aa.get(1).get(0).getBeginIndex(), 2);
    assertEquals(aa.get(1).get(0).getEndIndex(), 4);
    assertEquals(aa.get(1).get(1).getSegment(), "B");   // Bbc
    assertEquals(aa.get(1).get(1).getBeginIndex(), 5);
    assertEquals(aa.get(1).get(1).getEndIndex(), 6);

    List<List<SubString>> bb = autoCompletion.apply("Beta");
    assertEquals(bb.size(), 1);
    assertEquals(bb.get(0).size(), 1);
    assertEquals(bb.get(0).get(0).getSegment(), "Beta");
    assertEquals(bb.get(0).get(0).getBeginIndex(), 5);
    assertEquals(bb.get(0).get(0).getEndIndex(), 9);

    List<List<SubString>> cc = autoCompletion.apply("CC");
    assertEquals(cc.size(), 1);
    assertEquals(cc.get(0).size(), 2);    // XxAlaBbcCC
    assertEquals(cc.get(0).get(0).getBeginIndex(), 8);
    assertEquals(cc.get(0).get(0).getEndIndex(), 9);
    assertEquals(cc.get(0).get(0).getSegment(), "C");
    assertEquals(cc.get(0).get(1).getBeginIndex(), 9);
    assertEquals(cc.get(0).get(1).getEndIndex(), 10);
    assertEquals(cc.get(0).get(1).getSegment(), "C");
  }

}
