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
package org.tentackle.pdo;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.misc.TimeKeeper;
import org.tentackle.persist.security.SecurityPersistenceImpl;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.pdo.SecurityPersistence;

/**
 * Determines the overhead of the {@link org.tentackle.pdo.PdoInvocationHandler}.
 * <p>
 * Because of the hotspot compiler's optimizations the results are highly questionable.
 */
public class PdoInvocationTest {

  private static final int LOOPS = 7;
  private static final int SIZE = 1000000;

  @Test(groups = "local")
  public void invocations1() {

    Security security = Pdo.create(Security.class);
    SecurityPersistence securityPersistence = (SecurityPersistence) security.getPersistenceDelegate();

    int count = 1;
    for (long j=0; j < LOOPS; j++, count *= 10) {

      TimeKeeper timeKeeper = new TimeKeeper();
      for (int i = 0; i < count; i++) {
        securityPersistence.setAllowed(true);
      }
      timeKeeper.end();
      String msg = count + " direct invocations: " + timeKeeper.millisToString() + "ms";
      System.out.println(msg);
      Reporter.log(msg + "<br>");

      timeKeeper = new TimeKeeper();
      for (int i = 0; i < count; i++) {
        security.setAllowed(true);
      }
      timeKeeper.end();
      msg = count + " PDO invocations: " + timeKeeper.millisToString() + "ms";
      System.out.println(msg);
      Reporter.log(msg + "<br>");
    }

  }

  @Test(groups = "local")
  public void invocations2() {

    TimeKeeper timeKeeper = new TimeKeeper();
    SecurityPersistenceImpl[] securityImpls = new SecurityPersistenceImpl[SIZE];
    for (int i = 0; i < SIZE; i++) {
      securityImpls[i] = new SecurityPersistenceImpl();
    }
    timeKeeper.end();
    String msg = SIZE + " direct creations: " + timeKeeper.millisToString() + "ms";
    System.out.println(msg);
    Reporter.log(msg + "<br>");

    timeKeeper = new TimeKeeper();
    Security[] securities = new Security[SIZE];
    for (int i = 0; i < SIZE; i++) {
      securities[i] = Pdo.create(Security.class);
    }
    timeKeeper.end();
    msg = SIZE + " proxy creations: " + timeKeeper.millisToString() + "ms";
    System.out.println(msg);
    Reporter.log(msg + "<br>");

    timeKeeper = new TimeKeeper();
    int sum = 0;
    for (int i = 0; i < SIZE; i++) {
      sum += securityImpls[i].getClassId();
    }
    timeKeeper.end();
    msg = SIZE + " direct invocations: " + timeKeeper.millisToString() + "ms " + sum;
    System.out.println(msg);
    Reporter.log(msg + "<br>");

    timeKeeper = new TimeKeeper();
    sum = 0;
    for (int i = 0; i < SIZE; i++) {
      sum += securities[i].getClassId();
    }
    timeKeeper.end();
    msg = SIZE + " 1st proxy invocations: " + timeKeeper.millisToString() + "ms " + sum;
    System.out.println(msg);
    Reporter.log(msg + "<br>");

    timeKeeper = new TimeKeeper();
    sum = 0;
    for (int i = 0; i < SIZE; i++) {
      sum += securities[i].getClassId();
    }
    timeKeeper.end();
    msg = SIZE + " 2nd proxy invocations: " + timeKeeper.millisToString() + "ms " + sum;
    System.out.println(msg);
    Reporter.log(msg + "<br>");

    timeKeeper = new TimeKeeper();
    sum = 0;
    for (int i = 0; i < SIZE; i++) {
      sum += securities[i].getClassId();
    }
    timeKeeper.end();
    msg = SIZE + " 3rd proxy invocations: " + timeKeeper.millisToString() + "ms " + sum;
    System.out.println(msg);
    Reporter.log(msg + "<br>");
  }

}
