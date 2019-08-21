package org.tentackle.script.truffle;

import org.tentackle.validate.validator.AbstractScriptValidationMessageConverter;
import org.tentackle.validate.validator.MessageScriptConverter;

/**
 * Converts a truffle javascript validator message script.
 *
 * @author harald
 */
@MessageScriptConverter(JavaScriptValidationMessageConverter.NAME)
public class JavaScriptValidationMessageConverter extends AbstractScriptValidationMessageConverter {

  /** the primary language name. */
  public static final String NAME = "JavaScript";

}
