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

import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.validator.MessageScriptConverter;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.Map;

/**
 * Tests for {@link JRubyValidationMessageConverter} and {@link GroovyJSR223ValidationMessageConverter}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class JRubyValidationMessageConverterTest {

  private static JSR223Language language(String engineName) {
    ScriptEngineFactory factory = new ScriptEngineManager().getEngineFactories().stream()
        .filter(f -> f.getNames().contains(engineName))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no " + engineName + " JSR223 engine on test classpath"));
    return new JSR223Language(factory);
  }

  private static String converterFor(String languageName) {
    for (Map.Entry<String, String> entry :
        ServiceFactory.getServiceFinder().createNameMap(MessageScriptConverter.class.getName()).entrySet()) {
      if (StringHelper.stripEnclosingDoubleQuotes(entry.getKey()).equals(languageName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Test
  public void testNamesMatchTheEngineLanguageNames() {
    Assert.assertEquals(language("jruby").getName(), JRubyValidationMessageConverter.NAME);
    Assert.assertEquals(language("groovy").getName(), GroovyJSR223ValidationMessageConverter.NAME);
  }

  @Test
  public void testRegisteredAsMappedServices() {
    Assert.assertEquals(converterFor(JRubyValidationMessageConverter.NAME), JRubyValidationMessageConverter.class.getName());
    Assert.assertEquals(converterFor(GroovyJSR223ValidationMessageConverter.NAME),
                        GroovyJSR223ValidationMessageConverter.class.getName());
  }

  @Test
  public void testJRubyConvertReferencesObjectAsInstanceVariable() {
    String code = "'error: ' + @('my.pattern', arg1)";
    String result = new JRubyValidationMessageConverter().convert(code, language("jruby"));
    Assert.assertEquals(result, "'error: ' + " + ValidationUtilities.class.getName() +
                                ".getInstance().format(@object,'my.pattern', arg1)");
  }

  @Test
  public void testGroovyConvertReferencesPlainObject() {
    String code = "'error: ' + @('my.pattern', arg1)";
    String result = new GroovyJSR223ValidationMessageConverter().convert(code, language("groovy"));
    Assert.assertEquals(result, "'error: ' + " + ValidationUtilities.class.getName() +
                                ".getInstance().format(object,'my.pattern', arg1)");
  }

}
