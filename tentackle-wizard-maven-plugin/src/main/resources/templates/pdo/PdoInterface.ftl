
package ${pdoPackage};

import ${domainPackage}.${domainClass};
import ${persistencePackage}.${persistenceClass};
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.Plural;
import org.tentackle.pdo.Singular;
import org.tentackle.session.ClassId;
import org.tentackle.session.TableName;

/*
 * @{
<#if pdoTablename != "">
 * tablename = ${pdoTablename}
</#if>
<#if pdoEntityId != "0">
 * classid   = ${pdoEntityId}
</#if>
 * mapping   = $model/$tablename.map
 * @}
 */

/*
 * @> $mapping
 *
 * # ${pdoShortDescription}
 * name := $classname
<#if pdoTablename != "">
 * table := $tablename
</#if>
<#if pdoEntityId != "0">
 * id := $classid
</#if>
<#if pdoExtends != "">
 * extends := ${pdoExtends}
</#if>
<#if pdoInheritance != "none">
 * inheritance := ${pdoInheritance}
</#if>
 * integrity := $integrity
 *
 * ## attributes
 *
 * ## indexes
 *
 * ## relations
 *
 * ## validations
 *
 * @<
 */

/**
 * ${pdoShortDescription}.
 * <p>
 * ${pdoLongDescription}
 *
 * @author ${user}
 */
<#if pdoTablename != "">
@TableName(value=/**/"${pdoTablename}"/**/,    // @wurblet < Inject --string $tablename
           mapSchema=/**/false/**/,     // @wurblet < Inject $mapSchema
           prefix=/**/""/**/)       // @wurblet < Inject --string $tablePrefix
</#if>
<#if pdoEntityId != "0">
@ClassId(/**/${pdoEntityId}/**/) // @wurblet < Inject $classid
</#if>
<#if pdoInheritance != "plain">
@Singular("${pdoEntity}")
@Plural("${pdoEntity}s")
</#if>
<#if pdoInheritance != "none">
public interface ${pdoEntity}<T extends ${pdoEntity}<T>> extends ${superPdoInterface}<T>, ${persistenceClass}<T>, ${domainClass}<T> {
<#else>
public interface ${pdoEntity} extends ${superPdoInterface}<${pdoEntity}>, ${persistenceClass}, ${domainClass} {
</#if>

  // @wurblet(fold=expanded) modelComment ModelComment

  // @wurblet uniqueDomainKey UniqueDomainKey

}
