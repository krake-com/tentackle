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
package org.tentackle.session;

import org.tentackle.log.Logger;
import org.tentackle.misc.ImmutableException;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * Tests that an immutable session info really is immutable.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SessionInfoImmutabilityTest {

  @Test
  public void testSettersRespectImmutability() {
    for (Map.Entry<String, Consumer<DefaultSessionInfo>> setter: mutatingSetters().entrySet()) {
      DefaultSessionInfo sessionInfo = createSessionInfo();

      setter.getValue().accept(sessionInfo);    // still mutable: must pass

      sessionInfo.setImmutable(true);
      try {
        setter.getValue().accept(sessionInfo);
        Assert.fail(setter.getKey() + " modified an immutable session info");
      }
      catch (ImmutableException ex) {
        // expected
      }
    }
  }


  @Test
  public void testImmutableLoggingLevelSuppressesTheException() {
    DefaultSessionInfo sessionInfo = createSessionInfo();
    sessionInfo.setImmutable(true);
    sessionInfo.setImmutableLoggingLevel(Logger.Level.WARNING);

    // logged instead of thrown, for applications that cannot afford to fail here
    sessionInfo.setHostInfo("host");
    Assert.assertEquals(sessionInfo.getHostInfo(), "host");
  }


  @Test
  public void testCloneOfFinallyImmutableIsMutable() {
    DefaultSessionInfo sessionInfo = createSessionInfo();
    sessionInfo.setFinallyImmutable();
    Assert.assertTrue(sessionInfo.isImmutable());
    Assert.assertTrue(sessionInfo.isFinallyImmutable());

    DefaultSessionInfo clone = sessionInfo.clone();
    Assert.assertFalse(clone.isImmutable());
    Assert.assertFalse(clone.isFinallyImmutable(), "the clone inherited the finally-immutable flag");

    // the clone can be frozen and thawed again, just like any other mutable session info
    clone.setUserName("emil");
    clone.setImmutable(true);
    clone.setImmutable(false);
    clone.setUserName("hugo");
    Assert.assertEquals(clone.getUserName(), "hugo");

    // ... while the original stays finally immutable
    Assert.assertTrue(sessionInfo.isFinallyImmutable());
    Assert.assertThrows(ImmutableException.class, () -> sessionInfo.setImmutable(false));
  }


  /**
   * Creates a mutable session info.
   *
   * @return the session info
   */
  private DefaultSessionInfo createSessionInfo() {
    return new DefaultSessionInfo("hugo", "secret".toCharArray(), null);
  }

  /**
   * Gets all setters that must be refused by an immutable session info, by name.
   *
   * @return the setters
   */
  private Map<String, Consumer<DefaultSessionInfo>> mutatingSetters() {
    Map<String, Consumer<DefaultSessionInfo>> setters = new LinkedHashMap<>();
    setters.put("setLocale", info -> info.setLocale(Locale.GERMANY));
    setters.put("setTimeZone", info -> info.setTimeZone(TimeZone.getTimeZone("UTC")));
    setters.put("setVmInfo", info -> info.setVmInfo("vm"));
    setters.put("setOsInfo", info -> info.setOsInfo("os"));
    setters.put("setHostInfo", info -> info.setHostInfo("host"));
    setters.put("setApplicationId", info -> info.setApplicationId(4711));
    setters.put("setSessionName", info -> info.setSessionName("session"));
    // guarded all along, listed to pin the whole contract
    setters.put("setSince", info -> info.setSince(1));
    setters.put("setUserName", info -> info.setUserName("emil"));
    setters.put("setUserId", info -> info.setUserId(42));
    setters.put("setUserClassId", info -> info.setUserClassId(7));
    setters.put("setClientVersion", info -> info.setClientVersion("1.0"));
    setters.put("setApplicationName", info -> info.setApplicationName("app"));
    setters.put("setPropertiesName", info -> info.setPropertiesName("other"));
    setters.put("setLockLingerEnabled", info -> info.setLockLingerEnabled(true));
    return setters;
  }

}
