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


package org.tentackle.reflect;

import org.tentackle.misc.ApplicationException;
import org.tentackle.misc.DateHelper;
import org.testng.Assert;
import org.testng.annotations.Test;


/**
 * Tests the reflection helper.
 *
 * @author harald
 */
public class ReflectionHelperTest {

  public static class MyClass {

    public int myField = 10;

    @Override
    public String toString() {
      return "MyClass";
    }
  }


  public MyClass otherClass;


  public MyClass getMyClass() {
    return new MyClass();
  }


  @Test
  public void testClassBaseName() {
    Assert.assertEquals(ReflectionHelper.getClassBaseName(getClass()), "ReflectionHelperTest");
  }

  @Test
  public void testGetValueByPath() {

    try {
      Assert.assertTrue(ReflectionHelper.verifyPath(DateHelper.class, "now"));
    }
    catch (ApplicationException ex) {
      Assert.fail(ex.getAllMessages());
    }

    try {
      Assert.assertTrue(ReflectionHelper.getValueByPath(this, "myClass.myField").equals(10));
    }
    catch (ApplicationException ex) {
      Assert.fail(ex.getAllMessages());
    }

    try {
      Assert.assertTrue(ReflectionHelper.getValueByPath(this, "otherClass.myField") == null);
    }
    catch (ApplicationException ex) {
      Assert.fail(ex.getAllMessages());
    }

    try {
      ReflectionHelper.getValueByPath(this, "otherClass.myField*");
      Assert.fail("should have thrown an ApplicationException!");
    }
    catch (ApplicationException ex) {
      // fine
    }
  }

}
