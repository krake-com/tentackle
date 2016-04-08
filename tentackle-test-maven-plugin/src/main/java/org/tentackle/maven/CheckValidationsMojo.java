/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.maven;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.tentackle.buildsupport.AbstractTentackleProcessor;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.ServiceFactory;
import org.tentackle.maven.CheckValidationsProcessor.ValidatorEntry;
import org.tentackle.misc.CompoundValue;
import org.tentackle.misc.StringHelper;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.validate.Validator;
import org.tentackle.validate.validator.MessageScriptConverter;


/**
 * Mojo to verify all validations.
 *
 * @author harald
 */
@Mojo(name = "validations",
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class CheckValidationsMojo extends AbstractTestMojo {

  private final CheckValidationsProcessor processor;

  /**
   * Creates a mojo.
   */
  public CheckValidationsMojo() {
    processor = new CheckValidationsProcessor();
    addProcessor(processor);
  }


  @Override
  protected void initializeProcessor(AbstractTentackleProcessor processor, File srcDir) {
    super.initializeProcessor(processor, srcDir);
    ((CheckValidationsProcessor) processor).setProcessingClassLoader(getProcessingClassloader());
  }

  @Override
  public void finishExecute() throws MojoExecutionException, MojoFailureException {

    Thread.currentThread().setContextClassLoader(getProcessingClassloader());

    int count = processor.getValidators().size();
    int errors = processor.getErrors();

    // load converters
    Map<String, ScriptConverter> messageConverterMap = new HashMap<>();
    for (Map.Entry<String,String> entry:
         ServiceFactory.getServiceFinder().createNameMap(MessageScriptConverter.class.getName()).entrySet()) {
      try {
        String language = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
        ScriptConverter converter = (ScriptConverter) Class.forName(entry.getValue()).newInstance();
        messageConverterMap.put(language, converter);
      }
      catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
        throw new MojoFailureException("message script converter configuration failed", ex);
      }
    }

    // check validation messages for all locales
    for (Locale bundleLocale : bundleLocales) {
      LocaleProvider.getInstance().setCurrentLocale(bundleLocale);

      for (ValidatorEntry validatorEntry: processor.getValidators()) {
        try {
          Validator validator = validatorEntry.getValidator();
          Class<?> enclosingClass = getProcessingClassloader().loadClass(validatorEntry.getClassName());
          String message = validator.getMessage();
          if (message != null && !message.isEmpty()) {
            CompoundValue cv = new CompoundValue(message, String.class);
            Script script = cv.getScript();
            if (script != null) {
              // set the translator
              ScriptConverter converter = messageConverterMap.get(script.getLanguage().getName());
              if (converter == null) {
                throw new MojoFailureException("missing message script converter for " + script.getLanguage());
              }
              script.setConverter(converter);
            }
            cv.getValue(enclosingClass);
          }
        }
        catch (ClassNotFoundException | RuntimeException ex) {
          getLog().error("validation message creation failed", ex);
          errors++;
        }
      }
    }

    // show results
    StringBuilder localestr = new StringBuilder();
    for (Locale bundleLocale : bundleLocales) {
      if (localestr.length() > 0) {
        localestr.append(", ");
      }
      localestr.append(bundleLocale);
    }

    String msg = count + " annotation" + (count == 1 ? "" : "s") + " checked for " + localestr + ", ";

    if (errors > 0) {
      msg += errors + " errors";
      getLog().error(msg);
      throw new MojoExecutionException(msg);
    }
    else {
      getLog().info(msg + "no errors");
    }
  }

}
