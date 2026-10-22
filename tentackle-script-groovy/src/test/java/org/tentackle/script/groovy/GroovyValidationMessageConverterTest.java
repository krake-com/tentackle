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
import org.testng.annotations.Test;

import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptFactory;
import org.tentackle.validate.ValidationUtilities;

import java.util.function.Function;

/**
 * Tests for {@link GroovyValidationMessageConverter}.
 */
public class GroovyValidationMessageConverterTest {

  private final GroovyValidationMessageConverter converter = new GroovyValidationMessageConverter();
  private final GroovyLanguage language = new GroovyLanguage();


  @Test
  public void testConvertWithoutMarkerReturnsCodeUnchanged() {
    String code = "amount > 0";
    Assert.assertEquals(converter.convert(code, language), code);
  }

  @Test
  public void testConvertRewritesI18nMarkerToValidationUtilitiesCall() {
    String code = "'error: ' + @(\"my.pattern\", arg1)";
    String result = converter.convert(code, language);
    int ndx = code.indexOf("@(");
    String expected = code.substring(0, ndx) +
                       ValidationUtilities.class.getName() + ".getInstance().format(object," +
                       code.substring(ndx + 2);
    Assert.assertEquals(result, expected);
  }

  @Test
  public void testConvertedCodeCompilesAsGroovyScript() {
    Function<String, ScriptConverter> converterProvider =
        name -> GroovyLanguage.NAME.equals(name) ? converter : null;
    Script script = ScriptFactory.getInstance().createScript("groovy", "'x' + @(\"pattern\")", false, false, converterProvider);
    Assert.assertTrue(script.getCode().contains(ValidationUtilities.class.getName()), script.getCode());
    script.validate();     // must compile fine, no ValidationBundle needed just to compile
  }

}
