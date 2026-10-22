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
import org.testng.Reporter;
import org.testng.annotations.Test;

import java.sql.Timestamp;

/**
 * CompoundValue Test.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class CompoundValueTest {

  private static final String HELLO = "hello!";

  /**
   * The method grabbed by CompoundValue("$sayHello").
   * @return the hello string
   */
  public String sayHello() {
    return HELLO;
  }

  public boolean thisIsFalse() {
    return false;
  }

  @Test
  public void testCompountValue() {
    CompoundValue par = new CompoundValue("100", Integer.class);
    Object result = par.getValue();
    Assert.assertTrue(result instanceof Integer);
    Assert.assertEquals((int) ((Integer) result), 100);

    par = new CompoundValue("$sayHello");
    result = par.getValue(this);
    Assert.assertTrue(result instanceof String);
    Assert.assertEquals(result, sayHello());

    par = new CompoundValue("!$thisIsFalse");
    result = par.getValue(this);
    Assert.assertTrue(result instanceof Boolean);
    Assert.assertEquals(result, !thisIsFalse());

    result = new CompoundValue("$org.tentackle.common.DateHelper.now").getValue();
    Assert.assertTrue(result instanceof Timestamp);
    Reporter.log(result + "<br/>");

    // scripts are tested in the corresponding scripting language tt-modules
  }

  @Test
  public void testMalformed() {
    Assert.assertThrows(IllegalArgumentException.class, () -> new CompoundValue(""));
    Assert.assertThrows(IllegalArgumentException.class, () -> new CompoundValue("$"));
    Assert.assertThrows(IllegalArgumentException.class, () -> new CompoundValue("!$"));
    Assert.assertThrows(IllegalArgumentException.class, () -> new CompoundValue("!x"));
    Assert.assertThrows(IllegalArgumentException.class, () -> new CompoundValue("\\"));
    // a static reference needs a member
    Assert.assertThrows(IllegalArgumentException.class, () -> new CompoundValue("$org.tentackle.common.DateHelper"));
    Assert.assertThrows(IllegalArgumentException.class, () -> new CompoundValue("$org.tentackle.common.DateHelper."));
  }

  @Test
  public void testEscapedConstant() {
    CompoundValue par = new CompoundValue("\\$abc", String.class);
    Assert.assertEquals(par.getType(), CompoundValue.Type.CONSTANT);
    Assert.assertEquals(par.getValue(), "$abc");
  }

}
