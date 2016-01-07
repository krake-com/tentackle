/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.testng.Reporter;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

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
