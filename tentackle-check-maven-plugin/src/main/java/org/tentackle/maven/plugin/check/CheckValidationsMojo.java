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


package org.tentackle.maven.plugin.check;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.tentackle.buildsupport.AbstractTentackleProcessor;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.LocaleProvider;
import org.tentackle.maven.plugin.check.CheckValidationsProcessor.ValidatorEntry;
import org.tentackle.misc.CompoundValue;
import org.tentackle.script.Script;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.Validator;
import org.tentackle.validate.ValidatorCompoundValueFactory;

import java.io.File;
import java.util.Locale;
import java.util.MissingResourceException;


/**
 * Verifies all annotation-based validations.<br>
 * Scans for validator annotations (annotated with &#64;Validation), creates the validators and
 * their compound values for the annotation's value, message and condition. Value and condition scripts
 * are compiled once. Message scripts are evaluated for each configured locale, which resolves the
 * translations of i18n-scripts (see &#64;MessageScriptConverter) as well.
 * <p>
 * The plugin ships no scripting language. The provider of the language, such as tentackle-script-groovy,
 * must be added as a plugin dependency.
 *
 * @author harald
 */
@Mojo(name = "validations",
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true)
public class CheckValidationsMojo extends AbstractCheckMojo {

  private final CheckValidationsProcessor processor;
  private int errors;
  private int scriptCount;


  /**
   * Creates a mojo.
   */
  public CheckValidationsMojo() {
    processor = new CheckValidationsProcessor();
    addProcessor(processor);
  }


  @Override
  protected void initializeProcessor(AbstractTentackleProcessor processor, File srcDir) throws MojoFailureException {
    super.initializeProcessor(processor, srcDir);
    ((CheckValidationsProcessor) processor).setProcessingClassLoader(getProcessingClassloader());
  }

  @Override
  public void finishExecute() throws MojoExecutionException {

    // set the classloader (otherwise ValidationUtilities doesn't know the classpath)
    ValidationUtilities.getInstance().setValidationBundleClassLoader(getProcessingClassloader());

    int count = processor.getValidators().size();
    errors = processor.getErrors();

    // values and conditions don't depend on the locale
    for (ValidatorEntry validatorEntry: processor.getValidators()) {
      checkValue(validatorEntry);
      checkCondition(validatorEntry);
    }

    // check validation messages for all locales
    try {
      for (Locale bundleLocale : bundleLocales) {
        LocaleProvider.getInstance().setCurrentLocale(bundleLocale);
        for (ValidatorEntry validatorEntry: processor.getValidators()) {
          checkMessage(validatorEntry);
        }
      }
    }
    finally {
      // don't leave the locale behind in the build thread
      LocaleProvider.getInstance().setCurrentLocale(null);
    }

    // show results
    StringBuilder localestr = new StringBuilder();
    for (Locale bundleLocale : bundleLocales) {
      if (!localestr.isEmpty()) {
        localestr.append(", ");
      }
      localestr.append(bundleLocale);
    }

    String msg = count + " annotation" + (count == 1 ? "" : "s") + ", " +
                 scriptCount + " script" + (scriptCount == 1 ? "" : "s") + " checked for " + localestr + ", ";

    if (errors > 0) {
      msg += errors + " errors";
      getLogger().error(msg);
      throw new MojoExecutionException(msg);
    }
    else {
      getLogger().info(msg + "no errors");
    }
  }


  /**
   * Checks the message script for the current locale.<br>
   * The script is evaluated, which also resolves the translations of an i18n-script.
   *
   * @param validatorEntry the validator
   */
  private void checkMessage(ValidatorEntry validatorEntry) {
    try {
      Validator validator = validatorEntry.validator();
      Class<?> enclosingClass = getProcessingClassloader().loadClass(validatorEntry.className());
      String message = validator.getMessage();
      if (message != null && !message.isEmpty()) {
        CompoundValue cv = ValidatorCompoundValueFactory.getInstance().createMessageParameter(message);
        Script script = cv.getScript();
        if (script != null) {
          scriptCount++;
          cv.getValue(enclosingClass);
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
          getLogger().error("{} for {}", vx.getMessage(), validatorEntry);
          return;
        }
      }
      getLogger().error("validation message check failed for validator {}", validatorEntry, ex);
    }
  }


  /**
   * Checks the condition script.
   *
   * @param validatorEntry the validator
   */
  private void checkCondition(ValidatorEntry validatorEntry) {
    try {
      Validator validator = validatorEntry.validator();
      String condition = validator.getCondition();
      if (condition != null && !condition.isEmpty()) {
        CompoundValue cv = ValidatorCompoundValueFactory.getInstance().createConditionParameter(condition);
        Script script = cv.getScript();
        if (script != null) {
          scriptCount++;
          script.validate();
        }
      }
    }
    catch (RuntimeException ex) {
      getLogger().error("validation condition check failed for validator {}", validatorEntry, ex);
      errors++;
    }
  }

  /**
   * Checks the value script.
   *
   * @param validatorEntry the validator
   */
  private void checkValue(ValidatorEntry validatorEntry) {
    try {
      Validator validator = validatorEntry.validator();
      String value = validator.getValue();
      if (value != null && !value.isEmpty()) {
        CompoundValue cv = ValidatorCompoundValueFactory.getInstance().createValueParameter(value, Object.class);
        Script script = cv.getScript();
        if (script != null) {
          scriptCount++;
          script.validate();
        }
      }
    }
    catch (RuntimeException ex) {
      getLogger().error("validation value check failed for validator {}", validatorEntry, ex);
      errors++;
    }
  }

}
