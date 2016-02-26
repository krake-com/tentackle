/**
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

import org.tentackle.misc.convertibletest.TestConvertible;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.tentackle.misc.convertibletest.TestConvertible.ONE;
import static org.tentackle.misc.convertibletest.TestConvertible.THREE;
import static org.tentackle.misc.convertibletest.TestConvertible.TWO;

/**
 * Tests the Convertible Helper.
 *
 * @author harald
 */
public class ConvertibleTest {

  @Test
  public void testConvert() {
    Integer oneInt = ONE.toExternal();
    Integer twoInt = TWO.toExternal();
    Integer threeInt = THREE.toExternal();
    TestConvertible oneEnum = ConvertibleHelper.toInternal(TestConvertible.class, oneInt);
    TestConvertible twoEnum = ConvertibleHelper.toInternal(TestConvertible.class, twoInt);
    TestConvertible threeEnum = ConvertibleHelper.toInternal(TestConvertible.class, threeInt);
    Assert.assertEquals(oneInt, Integer.valueOf(100));
    Assert.assertEquals(twoInt, Integer.valueOf(200));
    Assert.assertEquals(threeInt, Integer.valueOf(300));
    Assert.assertEquals(oneEnum.toExternal(), oneInt);
    Assert.assertEquals(twoEnum.toExternal(), twoInt);
    Assert.assertEquals(threeEnum.toExternal(), threeInt);
    try {
      ConvertibleHelper.toInternal(TestConvertible.class, 400);
      Assert.fail("creating internal value for external 400 should not be possible");
    }
    catch (RuntimeException e) {
      // fine
    }
  }
}
