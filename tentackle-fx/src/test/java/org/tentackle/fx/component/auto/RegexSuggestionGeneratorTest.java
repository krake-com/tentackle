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
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class RegexSuggestionGeneratorTest {

  private static final List<String> CHOICES = List.of("24 hours a day, 7 days a week, 365 days a year", "no match at all");

  @Test
  public void autoComplete() {

    RegexSuggestionGenerator<String> autoCompletion = new RegexSuggestionGenerator<>(Pattern.compile("\\d+"));
    assertTrue(autoCompletion.apply("24").isEmpty());    // no choices so far!

    autoCompletion.setChoices(CHOICES);
    List<List<SubString>> rg = autoCompletion.apply("4 didoedeldu 36");
    assertEquals(rg.size(), 1);
    assertEquals(rg.get(0).size(), 2);
    assertEquals(rg.get(0).get(0).getSegment(), "4");
    assertEquals(rg.get(0).get(0).getBeginIndex(), 1);
    assertEquals(rg.get(0).get(0).getEndIndex(), 2);
    assertEquals(rg.get(0).get(1).getSegment(), "36");
    assertEquals(rg.get(0).get(1).getBeginIndex(), 31);
    assertEquals(rg.get(0).get(1).getEndIndex(), 33);

  }

}
