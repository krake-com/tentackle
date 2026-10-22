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

package org.tentackle.buildsupport.codegen;

import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link TemplateModel}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class TemplateModelTest {

  @Test
  public void putValueConvertsToStringAndMapsNullToEmptyString() {
    TemplateModel model = new TemplateModel();
    model.putValue("count", 42);
    model.putValue("nothing", null);

    assertEquals(model.get("count"), "42");
    assertEquals(model.get("nothing"), "");
  }

  @Test
  public void addPropertiesTranslatesKeysToCamelCase() {
    Properties properties = new Properties();
    properties.setProperty("db.url", "jdbc:h2:mem:test");
    properties.setProperty("plain", "value");

    TemplateModel model = new TemplateModel();
    model.addProperties(properties);

    assertEquals(model.get("dbUrl"), "jdbc:h2:mem:test");
    assertEquals(model.get("plain"), "value");
  }

  @Test
  public void addMapTranslatesKeysToCamelCaseAndStringifiesValues() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("some.nested.key", 123);
    source.put("flag", true);

    TemplateModel model = new TemplateModel();
    model.addMap(source);

    assertEquals(model.get("someNestedKey"), "123");
    assertEquals(model.get("flag"), "true");
  }

  @Test
  public void modelIsAMap() {
    TemplateModel model = new TemplateModel();
    model.putValue("key", "value");
    assertTrue(model instanceof Map);
    assertEquals(model.size(), 1);
  }

}
