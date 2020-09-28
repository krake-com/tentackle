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


package org.tentackle.script.groovy;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptVariable;

import java.util.Set;

/**
 * Groovy test.
 *
 * @author harald
 */
public class GroovyTest {

  private static final int LOOPS = 1000000;

  @Test
  public void testGroovy() {
    ScriptVariable monthsVar = new ScriptVariable("numberOfMonths");
    Set<ScriptVariable> variables = Set.of(
        new ScriptVariable("amount", 154000),
        new ScriptVariable("rate", 3.75 / 100),
        monthsVar);
    String code = "amount * (rate / 12) / (1 - (1 + rate / 12) ** -numberOfMonths)";
    Script script = ScriptFactory.getInstance().createScript("groovy", code, false, false, null);
    monthsVar.setValue(240);
    double result = script.execute(variables);
    Assert.assertEquals(result, 913.0480050387337);

    long startTime = System.currentTimeMillis();

    for (int i = 1; i < LOOPS; i++) {
      monthsVar.setValue(i);
      script.execute(variables);
    }
    long endTime = System.currentTimeMillis();
    Reporter.log("duration (groovy): " + (endTime - startTime) / 1000.0 + " seconds<br/>");

    startTime = System.currentTimeMillis();
    for (int i = 1; i < LOOPS; i++) {
      sameInJava(i);
    }
    endTime = System.currentTimeMillis();
    Reporter.log("duration (java): " + (endTime - startTime) / 1000.0 + " seconds<br/>");
  }

  private double sameInJava(int numberOfMonths) {
    return Math.pow(154000.0 * (0.0375 / 12) / (1 - (1 + 0.0375 / 12)), -numberOfMonths);
  }

}
