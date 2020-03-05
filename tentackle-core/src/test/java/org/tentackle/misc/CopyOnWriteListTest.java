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

}
