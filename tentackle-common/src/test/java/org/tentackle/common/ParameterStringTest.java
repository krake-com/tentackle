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

package org.tentackle.common;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.text.ParseException;
import java.util.Properties;

import static org.testng.Assert.assertEquals;

/**
 * Tests the ParameterString.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ParameterStringTest {

  @Test
  public void testParameterString() {

    try {
      String parStr = "user='harald', enabled, offset='10', skipLine";
      ParameterString ps = new ParameterString(parStr);
      assertEquals(ps.toString(), parStr);
      ps.setParameter("userId", "harr's");
      parStr += ", userId='harr''s'";
      assertEquals(parStr, ps.toString());
      ps.setParameter("offset", null);
      parStr = "user='harald', enabled, skipLine, userId='harr''s'";
      assertEquals(ps.toString(), parStr);
    }
    catch (ParseException ex) {
      Assert.fail(ex.toString());
    }

    try {
      String parStr = "user='harald";
      ParameterString ps = new ParameterString();
      ps.parse(parStr);
      Assert.fail("should have failed");
    }
    catch (ParseException ex) {
      assertEquals(ex.getErrorOffset(), 5);
    }
  }

  @Test
  public void testAccessors() throws ParseException {
    ParameterString ps = new ParameterString("user='harald', enabled, offset='10'");
    assertEquals(ps.getParameter("user"), "harald");
    assertEquals(ps.getParameter("offset"), "10");
    // a name without a value is stored as the empty string (presence flag, e.g. for booleans)
    assertEquals(ps.getParameter("enabled"), "");
    // unknown parameter
    Assert.assertNull(ps.getParameter("missing"));
    // insertion order is preserved
    assertEquals(ps.getParameterNames(), new String[] { "user", "enabled", "offset" });
  }

  @Test
  public void testSetParameterReturnsPreviousValue() {
    ParameterString ps = new ParameterString();
    Assert.assertNull(ps.setParameter("x", "1"));
    assertEquals(ps.setParameter("x", "2"), "1");
    // setting null removes and returns the previous value
    assertEquals(ps.setParameter("x", null), "2");
    Assert.assertNull(ps.getParameter("x"));
  }

  @Test
  public void testToProperties() throws ParseException {
    ParameterString ps = new ParameterString("user='harald', enabled, offset='10'");
    Properties props = ps.toProperties();
    assertEquals(props.getProperty("user"), "harald");
    assertEquals(props.getProperty("offset"), "10");
    assertEquals(props.getProperty("enabled"), "");
    assertEquals(props.size(), 3);
  }

  @Test
  public void testQuoteRoundtrip() throws ParseException {
    // a value containing single quotes is encoded as doubled quotes and parses back to the original
    ParameterString ps = new ParameterString();
    ps.setParameter("q", "it's a 'test'");
    String external = ps.toString();
    assertEquals(external, "q='it''s a ''test'''");
    ParameterString reparsed = new ParameterString(external);
    assertEquals(reparsed.getParameter("q"), "it's a 'test'");
  }

  @Test
  public void testEmptyAndNull() throws ParseException {
    assertEquals(new ParameterString("").toString(), "");
    // parse(null) clears without failing
    ParameterString ps = new ParameterString("a='1'");
    ps.parse(null);
    assertEquals(ps.getParameterNames().length, 0);
  }

  @Test
  public void testCommaAfterNameWithoutValue() throws ParseException {
    ParameterString ps = new ParameterString("flag,other='x',last");
    assertEquals(ps.getParameterNames(), new String[] { "flag", "other", "last" });
    assertEquals(ps.getParameter("flag"), "");
    assertEquals(ps.getParameter("other"), "x");
    assertEquals(ps.getParameter("last"), "");
    assertEquals(ps.toString(), "flag, other='x', last");
  }

}
