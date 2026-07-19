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

package org.tentackle.io;

import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link UriHelper} query parameter parsing.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class UriHelperTest {

  @Test
  void testIntegerParameters() {
    Map<String, Integer> parameters = UriHelper.getQueryParameters(
      URI.create("trips://localhost:8080?min=5&max=10&ini=4&fail&text=ignored").getQuery(), Integer::parseInt);
    assertEquals(parameters.size(), 3);
    assertEquals(parameters.get("min"), 5);
    assertEquals(parameters.get("max"), 10);
    assertEquals(parameters.get("ini"), 4);
    // "fail" (no '=') and "text=ignored" (not an integer) are dropped
    assertNull(parameters.get("text"));
    assertNull(parameters.get("fail"));
  }

  @Test
  void testNullQuery() {
    // a URI without a query string yields getQuery() == null
    Map<String, Integer> parameters = UriHelper.getQueryParameters(null, Integer::parseInt);
    assertTrue(parameters.isEmpty());
  }

  @Test
  void testEmptyQuery() {
    Map<String, Integer> parameters = UriHelper.getQueryParameters("", Integer::parseInt);
    assertTrue(parameters.isEmpty());
  }

  @Test
  void testStringParameters() {
    // identity converter never fails, so all well-formed pairs survive
    Map<String, String> parameters = UriHelper.getQueryParameters("a=1&b=hello&c=true", Function.identity());
    assertEquals(parameters.size(), 3);
    assertEquals(parameters.get("a"), "1");
    assertEquals(parameters.get("b"), "hello");
    assertEquals(parameters.get("c"), "true");
  }

  @Test
  void testMalformedPairsAreIgnored() {
    // "flag" has no '=', "a==b" splits into three parts, "empty=" has no value part after split
    Map<String, String> parameters = UriHelper.getQueryParameters("flag&a==b&empty=&ok=yes", Function.identity());
    assertEquals(parameters.size(), 1);
    assertEquals(parameters.get("ok"), "yes");
  }

  @Test
  void testConversionFailureIsIgnored() {
    // only the convertible values are kept, the rest are silently dropped
    Map<String, Integer> parameters = UriHelper.getQueryParameters("a=1&b=x&c=3", Integer::parseInt);
    assertEquals(parameters.size(), 2);
    assertEquals(parameters.get("a"), 1);
    assertEquals(parameters.get("c"), 3);
    assertNull(parameters.get("b"));
  }

  @Test
  void testDuplicateKeyKeepsLastValue() {
    Map<String, Integer> parameters = UriHelper.getQueryParameters("a=1&a=2", Integer::parseInt);
    assertEquals(parameters.size(), 1);
    assertEquals(parameters.get("a"), 2);
  }
}
