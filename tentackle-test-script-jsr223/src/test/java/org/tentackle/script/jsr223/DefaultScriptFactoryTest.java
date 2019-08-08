package org.tentackle.script.jsr223;

import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.script.DefaultScriptFactory;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptVariable;
import org.tentackle.script.ScriptingLanguage;
import org.testng.annotations.Test;
import org.testng.Assert;

public class DefaultScriptFactoryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScriptFactoryTest.class);

    @Test
    public void testGetLanguages() {

        try {
            DefaultScriptFactory factory = new DefaultScriptFactory();

            for (ScriptingLanguage language : factory.getLanguages()) {
                for (String abbrv : language.getAbbreviations()) {
                    String abbrvLC = abbrv.toLowerCase();
                    LOGGER.info("DefaultScriptFactory found scripting language {0} as {1}", language, abbrvLC);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception thrown", e);
        }
    }

    @Test void testLanguages() {
        try {
            DefaultScriptFactory factory = new DefaultScriptFactory();
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
