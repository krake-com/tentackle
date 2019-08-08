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
                        String abbrvLC = abbrv.toLowerCase();
                        LOGGER.info("DefaultScriptFactory found scripting language {0} as {1}", language, abbrvLC);
                    }
                }
                LOGGER.info("DefaultScriptFactory found scripting language {0} by name {1}", language, language.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception thrown", e);
        }
    }

    @Test void testLanguages() {
        try {
            ScriptFactory factory = ScriptFactory.getInstance();
            ScriptingLanguage lang = factory.getLanguage("js");
            if (lang != null) {
                String code = "Math.pow(a,b)";
                Script script = factory.createScript("js", code, false, false);

                Assert.assertNotNull(script);

                script.execute(
                        new ScriptVariable("a", 7),
                        new ScriptVariable("b", 3));
                Object result = script.getResult();

                Assert.assertNotNull(result);
                Assert.assertEquals(result, 343.0);

                LOGGER.info("script js test result={0} class={1}", result, result.getClass().getName());
            }
        } catch ( Exception e ) {
            e.printStackTrace();
            Assert.fail("Exception thrown", e);
        }
    }

}
