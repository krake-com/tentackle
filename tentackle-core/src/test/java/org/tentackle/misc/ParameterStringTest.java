/*
 * Tentackle - http://www.tentackle.org
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

import java.text.ParseException;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the ParameterString.
 *
 * @author harald
 */
public class ParameterStringTest {

  @Test
  public void testParamaterString() {

    try {
      String parStr = "user='harald', enabled, offset='10', skipLine";
      ParameterString ps = new ParameterString(parStr);
      Assert.assertEquals(parStr, ps.toString());
      ps.setParameter("userId", "harr's");
      parStr += ", userId='harr''s'";
      Assert.assertEquals(parStr, ps.toString());
      ps.setParameter("offset", null);
      parStr = "user='harald', enabled, skipLine, userId='harr''s'";
      Assert.assertEquals(parStr, ps.toString());
    }
    catch (ParseException ex) {
      Assert.fail(ex.toString());
    }

    try {
      String parStr = "user='harald";
      ParameterString ps = new ParameterString();
      ps.parse(parStr);
      Assert.fail("should have failed");
    }
    catch (ParseException ex) {
      Assert.assertEquals(5, ex.getErrorOffset());
    }
  }

}
