
package ${persistencePackage};

import ${pdoPackage}.${pdoEntity};
import java.util.Collection;
import java.util.List;
import org.tentackle.bind.Bindable;
import org.tentackle.common.Binary;
import org.tentackle.common.BMoney;
import org.tentackle.common.DMoney;
import org.tentackle.common.Date;
import org.tentackle.common.Time;
import org.tentackle.common.Timestamp;
import org.tentackle.misc.TrackedList;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainKey;
import org.tentackle.pdo.PersistentObject;
import org.tentackle.session.Persistent;


/**
 * Persistence interface for ${pdoEntity}.
 *
 * @author ${user}
 */
<#if pdoInheritance != "none">
public interface ${persistenceClass}<T extends ${pdoEntity}<T>> extends ${superPersistenceInterface}<T> {
<#else>
public interface ${persistenceClass} extends ${superPersistenceInterface}<${pdoEntity}> {
</#if>

  // @wurblet attributeNames AttributeNames

  // @wurblet columnLenghts ColumnLengths

  // @wurblet methods Methods

  // @wurblet relations Relations

}
