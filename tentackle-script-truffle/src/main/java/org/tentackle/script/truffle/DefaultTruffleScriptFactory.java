package org.tentackle.script.truffle;

import org.tentackle.common.Service;

@Service(TruffleScriptFactory.class)
public class DefaultTruffleScriptFactory extends TruffleScriptFactoryImpl implements TruffleScriptFactory {

    public DefaultTruffleScriptFactory() {
        super();
    }
}
