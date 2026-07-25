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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import java.util.Comparator;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * Tests the transformation list helpers used by the column filters.
 */
@SuppressWarnings("missing-explicit-ctor")
public class FxUtilitiesListTest {

  private final FxUtilities utilities = FxUtilities.getInstance();


  @Test
  public void testPlainList() {
    ObservableList<String> list = FXCollections.observableArrayList("a", "b", "c");
    assertSame(utilities.getRootSource(list), list);
    assertEquals(utilities.getSourceIndex(list, 1), 1);
    utilities.setSourceElement(list, 1, "x");
    assertEquals(list, List.of("a", "x", "c"));
  }

  @Test
  public void testFilteredAndSorted() {
    ObservableList<String> source = FXCollections.observableArrayList("delta", "alpha", "charlie", "bravo");
    FilteredList<String> filtered = new FilteredList<>(source, text -> !text.startsWith("c"));
    SortedList<String> sorted = new SortedList<>(filtered, Comparator.naturalOrder());

    assertEquals(sorted, List.of("alpha", "bravo", "delta"));
    assertSame(utilities.getRootSource(sorted), source);

    // "alpha" is displayed first but is the second element of the source
    assertEquals(utilities.getSourceIndex(sorted, 0), 1);
    assertEquals(utilities.getSourceIndex(sorted, 2), 0);

    utilities.setSourceElement(sorted, 0, "amber");
    assertEquals(source, List.of("delta", "amber", "charlie", "bravo"));
  }

  @Test
  public void testSourceIndexOfHiddenElement() {
    ObservableList<String> source = FXCollections.observableArrayList("a", "b");
    FilteredList<String> filtered = new FilteredList<>(source, "a"::equals);
    assertEquals(utilities.getSourceIndex(filtered, 0), 0);
    assertEquals(filtered.size(), 1);
  }

}
