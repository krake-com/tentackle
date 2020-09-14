package org.tentackle.script.truffle.R;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.Test;

import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptVariable;

public class RLanguageTest {


  @Test
  public void testFastR() {

    if (!ScriptFactory.getInstance().isLanguageAvailable("R")) {
      throw new SkipException("testValidate test skipped, R language not available");
    }

    String code = "amount * (rate / 12) / (1 - (1 + rate / 12) ^ -numberOfMonths)";
    Script script = ScriptFactory.getInstance().createScript("R", code, false, false, null);

    Object result = script.execute(
        new ScriptVariable("amount", 154000),
        new ScriptVariable("rate", 3.75 / 100),
        new ScriptVariable("numberOfMonths", 240));

    // does R calculate different than javaScript
    //Assert.assertEquals(result, 913.0480050387337);
    Assert.assertEquals(result, 913.0480050387341);

    long startTime = System.currentTimeMillis();
    for (int i = 1; i < 10000; i++) {
      script.execute(
          new ScriptVariable("amount", 154000),
          new ScriptVariable("rate", 3.75 / 100),
          new ScriptVariable("numberOfMonths", i));
    }
    long endTime = System.currentTimeMillis();
    Reporter.log("duration (R): " + (endTime - startTime) / 1000.0 + " seconds<br/>");

    startTime = System.currentTimeMillis();
    for (int i = 1; i < 10000; i++) {
      sameInJava(i);
    }
    endTime = System.currentTimeMillis();
    Reporter.log("duration (java): " + (endTime - startTime) / 1000.0 + " seconds<br/>");
  }

  private double sameInJava(int numberOfMonths) {
    return Math.pow(154000.0 * (0.0375 / 12) / (1 - (1 + 0.0375 / 12)), -numberOfMonths);
  }
}
