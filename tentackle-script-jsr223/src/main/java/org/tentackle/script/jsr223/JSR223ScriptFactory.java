package org.tentackle.script.jsr223;

import org.tentackle.script.ScriptFactory;
import org.tentackle.common.ServiceFactory;

public interface JSR223ScriptFactory extends ScriptFactory {

    interface JSR223ScriptFactoryHolder {
        JSR223ScriptFactory INSTANCE = ServiceFactory.createService(JSR223ScriptFactory.class, DefaultJSR223ScriptFactory.class);
    }

    static JSR223ScriptFactory getInstance() {
        return JSR223ScriptFactoryHolder.INSTANCE;
    }

}
