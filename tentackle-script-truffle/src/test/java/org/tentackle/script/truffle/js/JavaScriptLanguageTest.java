package org.tentackle.script.truffle.js;

import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptVariable;
import org.tentackle.script.ScriptingLanguage;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

public class JavaScriptLanguageTest {


    @Test
    public void testJavaScript() {

        if ( ! ScriptFactory.getInstance().isLanguageAvailable("js") ) {
            Reporter.log("testValidate test skipped, js language not available");
        } else {

            String code = "amount * (rate / 12) / (1 - Math.pow((1 + rate / 12), -numberOfMonths))";
            Script script = ScriptFactory.getInstance().createScript("js", code, false, false);

            script.execute(
                    new ScriptVariable("amount", 154000),
                    new ScriptVariable("rate", 3.75 / 100),
                    new ScriptVariable("numberOfMonths", 240));
            Object result = script.getResult();
            Assert.assertEquals(result, 913.0480050387337);

            long startTime = System.currentTimeMillis();
            for (int i = 1; i < 10000; i++) {
                script.execute(
                        new ScriptVariable("amount", 154000),
                        new ScriptVariable("rate", 3.75 / 100),
                        new ScriptVariable("numberOfMonths", i));
            }
            long endTime = System.currentTimeMillis();
            Reporter.log("duration (javascript): " + (endTime - startTime) / 1000.0 + " seconds<br/>");

            startTime = System.currentTimeMillis();
            for (int i = 1; i < 10000; i++) {
                sameInJava(i);
            }
            endTime = System.currentTimeMillis();
            Reporter.log("duration (java): " + (endTime - startTime) / 1000.0 + " seconds<br/>");
        }
    }

    private double sameInJava(int numberOfMonths) {
        return Math.pow(154000.0 * (0.0375 / 12) / (1 - (1 + 0.0375 / 12)), -numberOfMonths);
    }
}
