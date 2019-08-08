package org.tentackle.script.jsr223;

import org.tentackle.validate.validator.AbstractScriptValidationMessageConverter;
import org.tentackle.validate.validator.MessageScriptConverter;

@MessageScriptConverter(ECMAScriptValidationMessageConverter.NAME)
public class ECMAScriptValidationMessageConverter extends AbstractScriptValidationMessageConverter {

    /** the primary language name. */
    public static final String NAME = "ECMAScript";

    public ECMAScriptValidationMessageConverter() {
        super();
    }
}
