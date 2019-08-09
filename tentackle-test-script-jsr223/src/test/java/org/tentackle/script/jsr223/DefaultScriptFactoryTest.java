package org.tentackle.script.jsr223;

import org.tentackle.log.Logger;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptVariable;
import org.tentackle.script.ScriptingLanguage;
import org.testng.annotations.Test;
import org.testng.Assert;

public class DefaultScriptFactoryTest {

    private static final Logger LOGGER = Logger.get(DefaultScriptFactoryTest.class);

    @Test
    public void testGetLanguages() {

        try {
            ScriptFactory factory = ScriptFactory.getInstance();
            Assert.assertNotNull(factory);
            LOGGER.info("factory="+factory);
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
