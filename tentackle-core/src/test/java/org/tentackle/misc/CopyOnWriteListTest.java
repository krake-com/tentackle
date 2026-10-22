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

package org.tentackle.misc;

import org.testng.Reporter;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Test copy-on-write lists.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class CopyOnWriteListTest {

  @Test
  public void testImmutableSort() {
    List<Integer> list = new ArrayList<>();
    list.add(3);
    list.add(2);
    list.add(1);
    CopyOnWriteList<Integer> sortedList = new CopyOnWriteList<>(list);
    for (Integer i: sortedList) {
      Reporter.log(" " + i);
    }
    Reporter.log("<br>");
    assertFalse(sortedList.isCloned());
    Collections.sort(sortedList);
    assertTrue(sortedList.isCloned());
    assertEquals(sortedList.get(0), Integer.valueOf(1));
    assertEquals(sortedList.get(1), Integer.valueOf(2));
    assertEquals(sortedList.get(2), Integer.valueOf(3));
    assertEquals(list.get(0), Integer.valueOf(3));
    assertEquals(list.get(1), Integer.valueOf(2));
    assertEquals(list.get(2), Integer.valueOf(1));
  }

  @Test
  public void testViewsDoNotModifyOriginal() {
    List<Integer> list = new ArrayList<>(List.of(1, 2, 3));
    CopyOnWriteList<Integer> cowList = new CopyOnWriteList<>(list);

    List<Integer> subList = cowList.subList(0, 2);
    assertEquals(subList, List.of(1, 2));
    subList.set(0, 10);
    assertEquals(cowList, List.of(10, 2, 3));

    cowList.reversed().set(0, 30);
    assertEquals(cowList, List.of(10, 2, 30));

    assertEquals(list, List.of(1, 2, 3));
  }

  @Test
  public void testContainsAllDoesNotClone() {
    CopyOnWriteList<Integer> cowList = new CopyOnWriteList<>(new ArrayList<>(List.of(1, 2, 3)));
    assertTrue(cowList.containsAll(List.of(1, 3)));
    assertFalse(cowList.isCloned());
  }

}
