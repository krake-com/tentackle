package org.tentackle.script.jsr223;

import org.tentackle.common.ServiceFactory;
import org.tentackle.script.DefaultScriptFactory;
import org.tentackle.script.ScriptFactory;

public interface JSR223ScriptFactory extends ScriptFactory {

    interface JSR223ScriptFactoryHolder {
        JSR223ScriptFactory INSTANCE = ServiceFactory.createService(JSR223ScriptFactory.class, DefaultJSR223ScriptFactory.class);
    }

    static JSR223ScriptFactory getInstance() {
        return JSR223ScriptFactoryHolder.INSTANCE;
    }

}
