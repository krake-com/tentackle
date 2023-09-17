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

import org.tentackle.misc.TimeKeeper;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptVariable;

import java.util.Random;

/**
 * Multithreading penetration test.
 */
public class GroovyThreadingTest {

  private static final int THREAD_NUM = 100;
  private static final int LOOPS = 100000;

  private final Random random = new Random();
  private final StringBuffer errorBuffer = new StringBuffer();


  private class Runner extends Thread {

    private final Script script;
    private double a;

    private Runner(Script script, int num) {
      super("Runner " + num);
      this.script = script;
      a = num;
    }

    @Override
    public void run() {
      int loop = 0;
      try {
        while (!interrupted() && loop++ < LOOPS) {
          double b = random.nextDouble();
          double result = script.execute(new ScriptVariable("a", a),
                                         new ScriptVariable("b", b));
          if (result != a + b) {
            // string concat because of multi-threading
            errorBuffer.append(this).append(" failed: ").append(a)
                       .append(" + ").append(b).append(" != ").append(result).append("\n");
            break;
          }
          a = result;
        }
      }
      catch (RuntimeException rx) {
        errorBuffer.append(rx.getMessage()).append("\n");
      }
      if (loop < LOOPS) {
        errorBuffer.append(this).append(" interrupted at ").append(loop).append("\n");
      }
    }
  }


  @Test
  public void runIt() {
    Script script1 = ScriptFactory.getInstance().createScript("groovy", "a + b", false, true, null);
    Script script2 = ScriptFactory.getInstance().createScript("groovy", "b + a", false, true, null);
    TimeKeeper timeKeeper = new TimeKeeper();
    Runner[] runners = new Runner[THREAD_NUM];
    for (int i=0; i < THREAD_NUM; i++) {
      Runner runner = new Runner(i % 2 == 0 ? script1 : script2, i);
      runners[i] = runner;
      runner.start();
    }
    for (int i=0; i < THREAD_NUM; i++) {
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
