
package ${domainImplPackage};


import ${pdoPackage}.${pdoEntity};
<#if domainPackage != domainImplPackage>
import ${domainPackage}.${domainClass};
</#if>

import org.tentackle.domain.AbstractDomainObject;
import org.tentackle.pdo.DomainException;
import org.tentackle.pdo.DomainObjectService;


/**
 * Domain implementation for ${pdoEntity}.
 *
 * @author ${user}
 */
@DomainObjectService(${pdoEntity}.class)
<#if pdoInheritance != "none">
public class ${domainImplClass}<T extends ${pdoEntity}<T>, D extends ${domainImplClass}<T,D>>
       extends ${superDomainImplementation}<T,D> implements ${domainClass}<T> {
<#else>
public class ${domainImplClass} extends ${superDomainImplementation}<${pdoEntity}, ${domainImplClass}> implements ${domainClass} {
</#if>

  /** serial version UID. */
  private static final long serialVersionUID = 1L;


  /**
   * Creates a domain object for a PDO.
   *
   * @param pdo the persistent domain object
   */
<#if pdoInheritance != "none">
  public ${domainImplClass}(T pdo) {
<#else>
  public ${domainImplClass}(${pdoEntity} pdo) {
</#if>
    super(pdo);
  }

  /**
   * Creates a domain object.
   */
  public ${domainImplClass}() {
    super();
  }


  // @wurblet domainMethods DomainMethods

}
