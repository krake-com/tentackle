package org.tentackle.script.truffle;

import org.tentackle.common.ServiceFactory;
import org.tentackle.script.DefaultScriptFactory;
import org.tentackle.script.ScriptFactory;

public interface TruffleScriptFactory extends ScriptFactory {

    interface TruffleScriptFactoryHolder {
        TruffleScriptFactory INSTANCE = ServiceFactory.createService(TruffleScriptFactory.class, DefaultTruffleScriptFactory.class);
    }

    static TruffleScriptFactory getInstance() {
        return TruffleScriptFactoryHolder.INSTANCE;
    }

}
