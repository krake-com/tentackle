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

package org.tentackle.script.jsr;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.SkipException;

import org.tentackle.misc.TimeKeeper;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptVariable;

import java.util.Random;

/**
 * Multithreading penetration test.
 */
public class JSR223ThreadingTest {

  private final int threadNum;
  private final int loopNum;

  private final Random random;
  private final StringBuffer errorBuffer;


  private class Runner extends Thread {

    private final Script script;
    private double a;

    private Runner(Script script, int num) {
      super(script.getLanguage() + "-runner " + num);
      this.script = script;
      a = num;
    }

    @Override
    public void run() {
      int loop = 0;
      try {
        while (!interrupted() && loop++ < loopNum) {
          double b = random.nextDouble();
          double result = script.execute(new ScriptVariable("a", a),
                                         new ScriptVariable("b", b));
          if (result != a + b) {
            // string concat because of multi-threading
            errorBuffer.append(this).append(" failed: ").append(a).append(" + ")
                       .append(b).append(" != ").append(result).append("\n");
            break;
          }
          a = result;
        }
      }
      catch (RuntimeException rx) {
        errorBuffer.append(rx.getMessage()).append("\n");
      }
      if (loop < loopNum) {
        errorBuffer.append(this).append(" prematurely aborted in loop #").append(loop).append(" of ").append(loopNum).append("\n");
      }
    }
  }


  public JSR223ThreadingTest(String language, int threadNum, int loopNum) {
    if (!ScriptFactory.getInstance().isLanguageAvailable(language)) {
      throw new SkipException("language " + language + " not available");
    }
    this.threadNum = threadNum;
    this.loopNum = loopNum;
    random = new Random();
    errorBuffer = new StringBuffer();
  }

  public void runIt(Script script1, Script script2) {
    TimeKeeper timeKeeper = new TimeKeeper();
    Runner[] runners = new Runner[threadNum];
    for (int i=0; i < threadNum; i++) {
      Runner runner = new Runner(i % 2 == 0 ? script1 : script2, i);
      runners[i] = runner;
      runner.start();
    }
    try {
      Thread.sleep(1000);   // for slow systems (ruby...)
    }
    catch (InterruptedException e) {
      errorBuffer.append("how? " + e.getMessage());
    }
    for (int i=0; i < threadNum; i++) {
      try {
        Runner runner = runners[i];
        runner.join();
      }
      catch (InterruptedException e) {
        // string concat because of multi-threading
        errorBuffer.append("test interrupted while waiting for ").append(runners[i])
                   .append(" to terminate: ").append(e).append("\n");
        runners[i].interrupt();
      }
    }
    Reporter.log("Duration " + timeKeeper.end().millisToString() + "<br>");
    if (!errorBuffer.isEmpty()) {
      Assert.fail(errorBuffer.toString());
    }
  }

}
