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

package org.tentackle.script.jsr;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.validator.MessageScriptConverter;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

/**
 * Tests for {@link ECMAScriptValidationMessageConverter}.
 * <p>
 * Nashorn was removed from the JDK, so there is no real ECMAScript engine on the test
 * classpath to compile against (unlike the Groovy/Ruby converter tests). The converter's
 * {@code convert()} logic is plain string rewriting though, so it is exercised here with
 * a JSR223 language that has no variable translator (Groovy's), same as identity would be
 * for a real ECMAScript engine.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ECMAScriptValidationMessageConverterTest {

  private final ECMAScriptValidationMessageConverter converter = new ECMAScriptValidationMessageConverter();

  private static JSR223Language groovyLanguage() {
    ScriptEngineFactory factory = new ScriptEngineManager().getEngineFactories().stream()
        .filter(f -> f.getNames().contains("groovy"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no groovy JSR223 engine on test classpath"));
    return new JSR223Language(factory);
  }

  @Test
  public void testNameConstant() {
    Assert.assertEquals(ECMAScriptValidationMessageConverter.NAME, "ECMAScript");
  }

  @Test
  public void testAnnotatedForItsOwnLanguageName() {
    MessageScriptConverter annotation = ECMAScriptValidationMessageConverter.class.getAnnotation(MessageScriptConverter.class);
    Assert.assertNotNull(annotation);
    Assert.assertEquals(annotation.value(), ECMAScriptValidationMessageConverter.NAME);
  }

  @Test
  public void testConvertWithoutMarkerReturnsCodeUnchanged() {
    String code = "amount > 0";
    Assert.assertEquals(converter.convert(code, groovyLanguage()), code);
  }

  @Test
  public void testConvertRewritesI18nMarkerToValidationUtilitiesCall() {
    String code = "'error: ' + @(\"my.pattern\", arg1)";
    String result = converter.convert(code, groovyLanguage());
    int ndx = code.indexOf("@(");
    String expected = code.substring(0, ndx) +
                       ValidationUtilities.class.getName() + ".getInstance().format(object," +
                       code.substring(ndx + 2);
    Assert.assertEquals(result, expected);
  }

}
