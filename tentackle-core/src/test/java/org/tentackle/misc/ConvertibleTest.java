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

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.misc.convertibles.SomeConvertible;

import static org.tentackle.misc.convertibles.SomeConvertible.ONE;
import static org.tentackle.misc.convertibles.SomeConvertible.THREE;
import static org.tentackle.misc.convertibles.SomeConvertible.TWO;

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
    SomeConvertible oneEnum = SomeConvertible.toInternal(oneInt);
    SomeConvertible twoEnum = SomeConvertible.toInternal(twoInt);
    SomeConvertible threeEnum = SomeConvertible.toInternal(threeInt);
    Assert.assertEquals(oneInt, Integer.valueOf(100));
    Assert.assertEquals(twoInt, Integer.valueOf(200));
    Assert.assertEquals(threeInt, Integer.valueOf(300));
    Assert.assertEquals(oneEnum.toExternal(), oneInt);
    Assert.assertEquals(twoEnum.toExternal(), twoInt);
    Assert.assertEquals(threeEnum.toExternal(), threeInt);
    try {
      SomeConvertible.toInternal(400);
      Assert.fail("creating internal value for external 400 should not be possible");
    }
    catch (RuntimeException e) {
      // fine
    }
  }
}
