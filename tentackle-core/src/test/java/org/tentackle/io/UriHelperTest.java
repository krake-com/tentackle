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
import java.net.URISyntaxException;
import java.util.Map;

import static org.testng.Assert.assertEquals;

@SuppressWarnings("missing-explicit-ctor")
public class UriHelperTest {

  @Test
  void testIntegerParameters() throws URISyntaxException {
    Map<String, Integer> parameters = UriHelper.getQueryParameters(
      new URI("trips://localhost:8080?min=5&max=10&ini=4&fail&text=ignored").getQuery(), Integer::parseInt);
    assertEquals(parameters.size(), 3);
    assertEquals(parameters.get("min"), 5);
    assertEquals(parameters.get("max"), 10);
    assertEquals(parameters.get("ini"), 4);
  }

}
