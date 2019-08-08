package org.tentackle.script.jsr223;

import org.tentackle.common.Service;

@Service(JSR223ScriptFactory.class)
public class DefaultJSR223ScriptFactory extends JSR223ScriptFactoryImpl implements JSR223ScriptFactory {

    public DefaultJSR223ScriptFactory() {
        super();
    }
}
