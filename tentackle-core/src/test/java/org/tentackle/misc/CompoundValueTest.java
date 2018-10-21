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

import java.sql.Timestamp;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

/**
 * CompoundValue Test.
 *
 * @author harald
 */
public class CompoundValueTest {


  /**
   * The method grabbed by CompoundValue("$sayHello").
   * @return the hello string
   */
  public String sayHello() {
    return "hello!";
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

    result = new CompoundValue("$org.tentackle.misc.DateHelper.now").getValue();
    Assert.assertTrue(result instanceof Timestamp);
    Reporter.log(result.toString() + "<br/>");

    result = new CompoundValue("#!{ 200 > 100 }").getValue();
    Assert.assertTrue(result instanceof Boolean);
    Assert.assertTrue((Boolean) result);

    result = new CompoundValue("#!{ 123.0 + 101.0 }", String.class).getValue();
    Reporter.log(result.toString() + "<br/>");
    Assert.assertTrue(result instanceof String);
    Assert.assertEquals(result, "224.0");

    par = new CompoundValue("#!{ 'the method says ' + object.sayHello() }");
    result = par.getValue(this);
    Assert.assertTrue(result instanceof String);
    Assert.assertEquals(result, "the method says " + sayHello());

    par = new CompoundValue("#!js{ 'the method says ' + object.sayHello() }");
    result = par.getValue(this);
    Assert.assertTrue(result instanceof String);
    Assert.assertEquals(((String) result), "the method says " + sayHello());

    // test parsing only
    new CompoundValue("100", Integer.class).parse(this.getClass());
    new CompoundValue("$sayHello").parse(this.getClass());
    new CompoundValue("$org.tentackle.misc.DateHelper.now").parse(this.getClass());
    new CompoundValue("#!{ 200 > 100 }").parse(this.getClass());
    new CompoundValue("#!{ 123.0 + 101.0 }", String.class).parse(this.getClass());
    new CompoundValue("#!{ 'the method says ' + object.sayHello() }").parse(this.getClass());
    new CompoundValue("#!js{ 'the method says ' + object.sayHello }").parse(this.getClass());
  }

}
