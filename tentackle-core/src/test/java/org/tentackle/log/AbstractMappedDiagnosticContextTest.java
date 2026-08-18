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

package org.tentackle.log;

import org.testng.annotations.Test;

import org.tentackle.common.ParameterString;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link AbstractMappedDiagnosticContext}.
 * <p>
 * The base class implements {@code getKeys}, {@code toString} and {@code matchesPattern} on top of
 * the abstract {@code getContext}, so a plain map-backed subclass is enough to exercise it.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractMappedDiagnosticContextTest {

  /**
   * A mapped diagnostic context backed by an ordinary {@link LinkedHashMap}.
   * <p>
   * Unlike {@link DefaultMappedDiagnosticContext} it is neither thread-local nor null-hostile,
   * which keeps the base class the only thing under test.
   */
  private static class MapContext extends AbstractMappedDiagnosticContext {

    private final Map<String, String> map = new LinkedHashMap<>();

    @Override
    public Map<String, String> getContext() {
      return map;
    }

    @Override
    public void put(String key, String val) {
      map.put(key, val);
    }

    @Override
    public String get(String key) {
      return map.get(key);
    }

    @Override
    public void remove(String key) {
      map.remove(key);
    }

    @Override
    public void clear() {
      map.clear();
    }
  }


  @Test
  public void testKeysOfEmptyContext() {
    assertTrue(new MapContext().getKeys().isEmpty());
  }

  @Test
  public void testKeysReflectTheContext() {
    MapContext context = new MapContext();
    context.put("user", "harald");
    context.put("host", "localhost");
    assertEquals(context.getKeys().size(), 2);
    assertTrue(context.getKeys().contains("user"));
    assertTrue(context.getKeys().contains("host"));

    context.remove("host");
    assertEquals(context.getKeys().size(), 1);
    assertFalse(context.getKeys().contains("host"));

    context.clear();
    assertTrue(context.getKeys().isEmpty());
  }

  @Test
  public void testKeysAreAViewOfTheContext() {
    // getKeys() returns the key set of the map returned by getContext()
    MapContext context = new MapContext();
    context.put("user", "harald");
    var keys = context.getKeys();
    context.put("host", "localhost");
    assertTrue(keys.contains("host"));
  }

  @Test
  public void testToStringOfEmptyContext() {
    assertEquals(new MapContext().toString(), "");
  }

  @Test
  public void testToStringIsAParameterString() {
    MapContext context = new MapContext();
    context.put("user", "harald");
    context.put("host", "localhost");
    assertEquals(context.toString(), "user='harald', host='localhost'");
  }

  @Test
  public void testToStringKeepsInsertionOrder() {
    MapContext context = new MapContext();
    context.put("b", "2");
    context.put("a", "1");
    assertEquals(context.toString(), "b='2', a='1'");
  }

  @Test
  public void testToStringOfEmptyValue() {
    // ParameterString renders an empty value as the bare name
    MapContext context = new MapContext();
    context.put("flag", "");
    assertEquals(context.toString(), "flag");
  }

  @Test
  public void testToStringEncodesSingleQuotes() {
    MapContext context = new MapContext();
    context.put("text", "it's fine");
    assertEquals(context.toString(), "text='it''s fine'");
  }

  @Test
  public void testToStringIsParseable() throws Exception {
    MapContext context = new MapContext();
    context.put("user", "harald");
    context.put("text", "it's fine");
    ParameterString parsed = new ParameterString(context.toString());
    assertEquals(parsed.getParameter("user"), "harald");
    assertEquals(parsed.getParameter("text"), "it's fine");
  }

  @Test
  public void testToStringDropsNullValues() {
    // ParameterString.setParameter(name, null) removes the parameter
    MapContext context = new MapContext();
    context.put("user", "harald");
    context.put("nothing", null);
    assertEquals(context.toString(), "user='harald'");
  }

  @Test
  public void testMatchesPattern() {
    MapContext context = new MapContext();
    context.put("user", "harald");
    context.put("host", "localhost");
    assertTrue(context.matchesPattern(Pattern.compile(".*user='harald'.*")));
    assertTrue(context.matchesPattern(Pattern.compile("user='harald', host='localhost'")));
  }

  @Test
  public void testMatchesPatternRequiresAFullMatch() {
    // matchesPattern uses Matcher.matches(), not find()
    MapContext context = new MapContext();
    context.put("user", "harald");
    assertFalse(context.matchesPattern(Pattern.compile("user")));
    assertFalse(context.matchesPattern(Pattern.compile(".*user='someone else'.*")));
  }

  @Test
  public void testEmptyContextMatchesEmptyPattern() {
    assertTrue(new MapContext().matchesPattern(Pattern.compile("")));
    assertFalse(new MapContext().matchesPattern(Pattern.compile(".+")));
  }

}
