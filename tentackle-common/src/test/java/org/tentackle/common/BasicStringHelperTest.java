/*
 * Tentackle - http://www.tentackle.org.
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

import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


/**
 * String helper test.
 *
 * @author harald
 */
public class BasicStringHelperTest {

  @Test
  public void testJavaNames() {
    assertTrue(BasicStringHelper.isValidJavaClassName("org.tentackle.common.Blah"));
    assertTrue(BasicStringHelper.isValidJavaClassName("Blah"));
    assertTrue(BasicStringHelper.isValidJavaClassName("Blah1"));
    assertFalse(BasicStringHelper.isValidJavaClassName("0Blah"));
    assertFalse(BasicStringHelper.isValidJavaClassName("blah"));
    assertFalse(BasicStringHelper.isValidJavaClassName("org.tentackle.4common.Blah"));

    List<String> parts = BasicStringHelper.split(" 'part \\\\one' , '\" part two\" '", " ,");
    assertEquals(parts.size(), 2);
    assertEquals(parts.get(0), "'part \\one'");
    assertEquals(parts.get(1), "'\" part two\" '");

    parts = BasicStringHelper.split("FlowPaneBuilder = FlowPane", ":= ");
    assertEquals(parts.size(), 2);
    assertEquals(parts.get(0), "FlowPaneBuilder");
    assertEquals(parts.get(1), "FlowPane");

  }
}
