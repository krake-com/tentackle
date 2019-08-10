/**
 * Tentackle - http://www.tentackle.org
 * <p>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


package org.tentackle.script.jsr223.js;

import java.sql.Timestamp;

import org.tentackle.misc.CompoundValue;
import org.tentackle.script.DefaultScriptFactory;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptingLanguage;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

/**
 * CompoundValue Test.
 *
 * @author harald, bei
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
        try {

            if ( ! ScriptFactory.getInstance().isLanguageAvailable("js") ) {
                Reporter.log("testValidate test skipped, js language not available");
            } else {

                ScriptFactory.getInstance().setDefaultLanguage("js");

                Object result = new CompoundValue("#!{ 200 > 100 }").getValue();
                Assert.assertTrue(result instanceof Boolean);
                Assert.assertTrue((Boolean) result);

                result = new CompoundValue("#!{ 123.0 + 101.0 }", String.class).getValue();
                Reporter.log(result.toString() + "<br/>");
                Assert.assertTrue(result instanceof String);
                Assert.assertTrue(result.equals("224.0") || result.equals("224"));

                CompoundValue par = new CompoundValue("#!{ 'the method says ' + object.sayHello() }");
                result = par.getValue(this);
                Assert.assertTrue(result instanceof String);
                Assert.assertTrue(result.equals("the method says " + sayHello()));

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
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception thrown", e);
        }
    }

}
