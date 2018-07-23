
package ${domainPackage};

import ${pdoPackage}.${pdoEntity};
import org.tentackle.pdo.DomainObject;


/**
 * Domain interface for ${pdoEntity}.
 *
 * @author ${user}
 */
<#if pdoInheritance != "none">
public interface ${domainClass}<T extends ${pdoEntity}<T>> extends ${superDomainInterface}<T> {
<#else>
public interface ${domainClass} extends ${superDomainInterface}<${pdoEntity}> {
</#if>

}
