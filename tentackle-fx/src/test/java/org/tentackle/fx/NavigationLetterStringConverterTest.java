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

package org.tentackle.fx;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Unit tests for {@link NavigationLetterStringConverter}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class NavigationLetterStringConverterTest {

  /**
   * Converter over city names, navigating by the (upper-cased) first letter.
   */
  private static class CityConverter extends NavigationLetterStringConverter<String> {
    CityConverter(List<String> cities) {
      super(cities);
    }

    @Override
    public String getNavigationLetters(String object) {
      return object.substring(0, 1).toUpperCase();
    }

    @Override
    public String getName(String object) {
      return object;
    }
  }

  private CityConverter newConverter() {
    // deliberately unsorted, with two entries sharing the same navigation letter
    return new CityConverter(List.of("Bonn", "Aachen", "Berlin", "Cologne"));
  }

  @Test
  public void testSortedByNavigationLetterThenName() {
    assertEquals(newConverter().getSortedObjects().stream().toList(),
                 List.of("Aachen", "Berlin", "Bonn", "Cologne"));
  }

  @Test
  public void testToStringReturnsNavigationLetters() {
    CityConverter converter = newConverter();
    assertEquals(converter.toString("Berlin"), "B");
    assertNull(converter.toString(null));
  }

  @Test
  public void testFromStringJumpsToFirstMatch() {
    CityConverter converter = newConverter();
    assertEquals(converter.fromString("A"), "Aachen");
    assertEquals(converter.fromString("B"), "Berlin");   // first of the two 'B' entries
    assertEquals(converter.fromString("C"), "Cologne");
  }

  @Test
  public void testFromStringJumpsToNextGreaterOrEqual() {
    CityConverter converter = newConverter();
    // no exact match: navigation lands on the next entry whose key is >= the input
    assertEquals(converter.fromString("BB"), "Cologne");
  }

  @Test
  public void testFromStringNullOrBeyondEnd() {
    CityConverter converter = newConverter();
    assertNull(converter.fromString(null));
    assertNull(converter.fromString("Z"), "past the last entry -> null");
  }

}
