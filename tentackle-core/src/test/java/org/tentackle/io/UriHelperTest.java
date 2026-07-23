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

import org.tentackle.common.TentackleRuntimeException;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link UriHelper} query parameter parsing and variable expansion.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class UriHelperTest {

  private static final URI BASE = URI.create("trips://localhost:18000/myapp?idle=10&backlog=100");

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


  @Test
  public void testExpandNothingToExpand() {
    // fast path: no '$' means the string is returned unchanged, base is irrelevant
    assertEquals(UriHelper.expand("trips://host:1234/app", null), "trips://host:1234/app");
    assertEquals(UriHelper.expand("trips://host:1234/app", BASE), "trips://host:1234/app");
  }

  @Test
  public void testExpandJavadocExample() {
    assertEquals(UriHelper.expand("tripcs://$host:${port+1}/$path?$query", BASE),
                 "tripcs://localhost:18001/myapp?idle=10&backlog=100");
  }

  @Test
  public void testExpandBareVariables() {
    assertEquals(UriHelper.expand("$scheme://$host:$port/$path", BASE), "trips://localhost:18000/myapp");
    assertEquals(UriHelper.expand("$rawpath", BASE), "/myapp");
    assertEquals(UriHelper.expand("$authority", BASE), "localhost:18000");
  }

  @Test
  public void testExpandVariablesAreCaseInsensitive() {
    assertEquals(UriHelper.expand("$HOST:${PORT+1}", BASE), "localhost:18001");
  }

  @Test
  public void testExpandArithmeticOperations() {
    assertEquals(UriHelper.expand("${port+1}", BASE), "18001");
    assertEquals(UriHelper.expand("${port-1}", BASE), "17999");
    assertEquals(UriHelper.expand("${port*2}", BASE), "36000");
    assertEquals(UriHelper.expand("${port/3}", BASE), "6000");
    assertEquals(UriHelper.expand("${port%7}", BASE), "3");   // 18000 % 7 == 3
  }

  @Test
  public void testExpandWhitespaceInsideBracesIsIgnored() {
    assertEquals(UriHelper.expand("${ port + 1 }", BASE), "18001");
  }

  @Test
  public void testExpandEscapedDollar() {
    assertEquals(UriHelper.expand("\\$host", BASE), "$host");
    assertEquals(UriHelper.expand("$host\\$", BASE), "localhost$");
  }

  @Test
  public void testExpandAdjacentVariables() {
    // adjacent braced and bare forms compose correctly
    assertEquals(UriHelper.expand("${port+1}-$host", BASE), "18001-localhost");
  }

  @Test
  public void testExpandNoBaseButVariableUsed() {
    assertThrows(TentackleRuntimeException.class, () -> UriHelper.expand("$host", null));
  }

  @Test
  public void testExpandUnknownVariable() {
    assertThrows(TentackleRuntimeException.class, () -> UriHelper.expand("$bogus", BASE));
    assertThrows(TentackleRuntimeException.class, () -> UriHelper.expand("${bogus+1}", BASE));
  }

  @Test
  public void testExpandUnterminatedBrace() {
    assertThrows(TentackleRuntimeException.class, () -> UriHelper.expand("${port+1", BASE));
  }

  @Test
  public void testExpandArithmeticOnNonNumericVariable() {
    assertThrows(TentackleRuntimeException.class, () -> UriHelper.expand("${host+1}", BASE));
  }

  @Test
  public void testExpandUndefinedComponentThrows() {
    URI noPort = URI.create("trips://localhost/myapp");
    assertThrows(TentackleRuntimeException.class, () -> UriHelper.expand("${port+1}", noPort));
    URI noQuery = URI.create("trips://localhost:18000/myapp");
    assertThrows(TentackleRuntimeException.class, () -> UriHelper.expand("$query", noQuery));
  }

  @Test
  public void testExpandTrailingDollarIsLiteral() {
    assertEquals(UriHelper.expand("$host$", BASE), "localhost$");
  }
}
