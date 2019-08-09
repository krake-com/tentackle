package org.tentackle.script.jsr223;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptVariable;
import org.tentackle.script.ScriptingLanguage;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.log.Logger;

public class JSR223ScriptFactoryTest {

    private static final Logger LOGGER = Logger.get(JSR223ScriptFactoryTest.class);

    @Test
    public void testGetLanguages() {
        try {
            JSR223ScriptFactory factory = JSR223ScriptFactory.getInstance();

            for (ScriptingLanguage language : factory.getLanguages()) {
                for (String abbrv : language.getAbbreviations()) {
                    if ( abbrv != null ) {
                        LOGGER.info("DefaultScriptFactory found scripting language {0} as {1}", language, abbrv);
                    }
                }
                LOGGER.info("DefaultScriptFactory found scripting language {0} by name {1}", language, language.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception thrown", e);
        }
    }

}
