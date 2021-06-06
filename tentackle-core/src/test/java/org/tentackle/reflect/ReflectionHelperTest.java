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


package org.tentackle.reflect;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;


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
  public void testPackageName() {
    Assert.assertEquals(ReflectionHelper.getPackageName("aa.bb.cc.Test"),"aa.bb.cc");
    Assert.assertEquals(ReflectionHelper.getPackageName("aa.bb.cc.Test$1"),"aa.bb.cc");
    Assert.assertEquals(ReflectionHelper.getPackageName("aa.bb.cc.test"),"aa.bb.cc.test");
    Assert.assertEquals(ReflectionHelper.getPackageName("aa.bb.cc.Outer.Inner"),"aa.bb.cc");
    Assert.assertEquals(ReflectionHelper.getPackageName("Blah"),"");
    Assert.assertEquals(ReflectionHelper.getPackageName(""),"");
  }

  @Test
  public void testOutermostClassName() {
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc.Outer.Inner1.Inner2"),"aa.bb.cc.Outer");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc.Blah"),"aa.bb.cc.Blah");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc.Blah$1"),"aa.bb.cc.Blah");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc.Blah$$Lambda$1/2147972"),"aa.bb.cc.Blah");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc"),"aa.bb.cc");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc."),"aa.bb.cc.");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("Blah"),"Blah");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("Blah."),"Blah");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("Outer.Inner1.Inner2"),"Outer");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName("Outer.Inner1$$Lambda$1/23231.Inner2"),"Outer");
    Assert.assertEquals(ReflectionHelper.getOutermostClassName(""),"");
  }

  @Test
  public void testGetValueByPath() {
    Assert.assertEquals(ReflectionHelper.getValueByPath(this, "myClass.myField"), 10);
    Assert.assertNull(ReflectionHelper.getValueByPath(this, "otherClass.myField"));

    try {
      ReflectionHelper.getValueByPath(this, "otherClass.myField*");
      Assert.fail("should have thrown a TentackleRuntimeException!");
    }
    catch (TentackleRuntimeException ex) {
      // fine
    }
  }

}
