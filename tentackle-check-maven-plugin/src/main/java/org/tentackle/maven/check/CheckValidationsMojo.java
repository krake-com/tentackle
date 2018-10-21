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


package org.tentackle.maven.check;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.tentackle.buildsupport.AbstractTentackleProcessor;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.ServiceFactory;
import org.tentackle.maven.check.CheckValidationsProcessor.ValidatorEntry;
import org.tentackle.misc.CompoundValue;
import org.tentackle.common.StringHelper;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.Validator;
import org.tentackle.validate.validator.MessageScriptConverter;
import org.tentackle.validate.validator.ValidationScriptConverter;


/**
 * Mojo to verify all annotation-based validations.<br>
 * Scans for validator annotations (annotated with &#64;Validation), creates the validators and
 * their compound values for message and condition. If the compound values are scripts,
 * the scripts will be compiled. If a message-script is an i18n-script (see &#64;MessageScriptConverter),
 * the script will be evaluated for each configured locale.
 *
 * @author harald
 */
@Mojo(name = "validations",
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class CheckValidationsMojo extends AbstractCheckMojo {

  private final CheckValidationsProcessor processor;
  private final Map<String, ScriptConverter> messageConverterMap;
  private final Map<String, ScriptConverter> validationConverterMap;
  private int errors;
  private int scriptCount;


  /**
   * Creates a mojo.
   */
  public CheckValidationsMojo() {
    processor = new CheckValidationsProcessor();
    addProcessor(processor);
    messageConverterMap = new HashMap<>();
    validationConverterMap = new HashMap<>();
  }


  @Override
  protected void initializeProcessor(AbstractTentackleProcessor processor, File srcDir) throws MojoFailureException {
    super.initializeProcessor(processor, srcDir);
    ((CheckValidationsProcessor) processor).setProcessingClassLoader(getProcessingClassloader());
  }

  @Override
  public void finishExecute() throws MojoExecutionException, MojoFailureException {

    // set the classloader (otherwise ValidationUtilities doesn't know the classpath)
    ValidationUtilities.getInstance().setValidationBundleClassLoader(getProcessingClassloader());

    int count = processor.getValidators().size();
    errors = processor.getErrors();

    // load converters
    for (Map.Entry<String,String> entry:
         ServiceFactory.getServiceFinder().createNameMap(MessageScriptConverter.class.getName()).entrySet()) {
      try {
        String language = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
        ScriptConverter converter = (ScriptConverter) Class.forName(entry.getValue()).getDeclaredConstructor().newInstance();
        messageConverterMap.put(language, converter);
      }
      catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
             InvocationTargetException | NoSuchMethodException ex) {
        throw new MojoFailureException("message script converter configuration failed", ex);
      }
    }
    for (Map.Entry<String,String> entry:
         ServiceFactory.getServiceFinder().createNameMap(ValidationScriptConverter.class.getName()).entrySet()) {
      try {
        String language = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
        ScriptConverter converter = (ScriptConverter) Class.forName(entry.getValue()).getDeclaredConstructor().newInstance();
        validationConverterMap.put(language, converter);
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
             NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        throw new MojoFailureException("validation script converter configuration failed", ex);
      }
    }

    // check validation messages for all locales
    for (Locale bundleLocale : bundleLocales) {
      LocaleProvider.getInstance().setCurrentLocale(bundleLocale);

      for (ValidatorEntry validatorEntry: processor.getValidators()) {
        checkMessage(validatorEntry);
        checkCondition(validatorEntry);
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

    String msg = count + " annotation" + (count == 1 ? "" : "s") + ", " +
                 scriptCount + " script" + (scriptCount == 1 ? "" : "s") + " checked for " + localestr + ", ";

    if (errors > 0) {
      msg += errors + " errors";
      getLog().error(msg);
      throw new MojoExecutionException(msg);
    }
    else {
      getLog().info(msg + "no errors");
    }
  }


  /**
   * Checks the message script.
   *
   * @param validatorEntry the validator
   * @throws MojoFailureException if no message script converter for the scripting language
   */
  private void checkMessage(ValidatorEntry validatorEntry) throws MojoFailureException {
    try {
      Validator validator = validatorEntry.getValidator();
      Class<?> enclosingClass = getProcessingClassloader().loadClass(validatorEntry.getClassName());
      String message = validator.getMessage();
      if (message != null && !message.isEmpty()) {
        CompoundValue cv = new CompoundValue(message, String.class);
        if (cv.getType() == CompoundValue.Type.SCRIPT) {
          Script script = cv.getScript();
          if (script != null) {
            scriptCount++;
            if (message.contains("@(")) {   // i18n script
              // set the translator
              ScriptConverter converter = messageConverterMap.get(script.getLanguage().getName());
              if (converter == null) {
                throw new MojoFailureException("missing message script converter for " + script.getLanguage());
              }
              script.setConverter(converter);
              cv.getValue(enclosingClass);
            }
            else {
              // some other script: just compile
              script.parse();
            }
          }
        }
      }
    }
    catch (ClassNotFoundException | RuntimeException ex) {
      errors++;
      MissingResourceException mx = ExceptionHelper.extractException(MissingResourceException.class, true, ex);
      if (mx != null) {
        // no stacktrace for missing resource: just print the missing translation
        ValidationRuntimeException vx = ExceptionHelper.extractException(ValidationRuntimeException.class, true, ex);
        if (vx != null) {
          getLog().error(vx.getMessage() + " for " + validatorEntry);
          return;
        }
      }
      getLog().error("validation message check failed for validator " + validatorEntry, ex);
    }
  }


  /**
   * Checks the condition script.
   *
   * @param validatorEntry the validator
   */
  private void checkCondition(ValidatorEntry validatorEntry) {
    try {
      Validator validator = validatorEntry.getValidator();
      String condition = validator.getCondition();
      if (condition != null && !condition.isEmpty()) {
        CompoundValue cv = new CompoundValue(condition, Boolean.class);
        if (cv.getType() == CompoundValue.Type.SCRIPT) {
          Script script = cv.getScript();
          if (script != null) {
            script.setConverter(validationConverterMap.get(script.getLanguage().getName()));
            scriptCount++;
            script.parse();
          }
        }
      }
    }
    catch (RuntimeException ex) {
      getLog().error("validation condition check failed for validator " + validatorEntry, ex);
      errors++;
    }
  }

}
