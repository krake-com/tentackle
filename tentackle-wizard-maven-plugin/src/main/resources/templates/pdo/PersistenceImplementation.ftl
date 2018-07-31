
package ${persistenceImplPackage};

import ${pdoPackage}.${pdoEntity};
<#if persistencePackage != persistenceImplPackage>
import ${persistencePackage}.${persistenceClass};
</#if>
<#if persistenceImplRemote = "true">
import ${persistenceImplPackage}.rmi.${pdoEntity}RemoteDelegate;
</#if>

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.tentackle.common.Binary;
import org.tentackle.common.BMoney;
import org.tentackle.common.DMoney;
import org.tentackle.common.Date;
import org.tentackle.common.Freezable;
import org.tentackle.common.Time;
import org.tentackle.common.Timestamp;
import org.tentackle.common.RemoteMethod;
import org.tentackle.dbms.DbObjectClassVariables;
import org.tentackle.dbms.PreparedStatementWrapper;
import org.tentackle.dbms.ResultSetWrapper;
import org.tentackle.dbms.StatementId;
import org.tentackle.misc.IdentifiableMap;
import org.tentackle.misc.TrackedArrayList;
import org.tentackle.misc.TrackedList;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.persist.PersistentObjectClassVariables;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentObjectService;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.Persistent;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.sql.Backend;
import org.tentackle.sql.JoinType;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationUtilities;


/**
 * Persistence implementation for ${pdoEntity}.
 *
 * @author ${user}
 */
@PersistentObjectService(${pdoEntity}.class)
<#if pdoInheritance != "none">
public class ${persistenceImplClass}<T extends ${pdoEntity}<T>, P extends ${persistenceImplClass}<T,P>>
       extends ${superPersistenceImplementation}<T,P> implements ${persistenceClass}<T> {
<#else>
public class ${persistenceImplClass} extends ${superPersistenceImplementation}<${pdoEntity}, ${persistenceImplClass}> implements ${persistenceClass} {
</#if>

  /** serial version UID. */
  private static final long serialVersionUID = -1;


  // @wurblet classVariables ClassVariables

  // @wurblet columnNames ColumnNames

  // @wurblet declare Declare


  /**
   * Creates a persistent object for a PDO.
   *
   * @param pdo the persistent domain object
   * @param context the domain context
   */
<#if pdoInheritance != "none">
  public ${persistenceImplClass}(T pdo, DomainContext context) {
<#else>
  public ${persistenceImplClass}(${pdoEntity} pdo, DomainContext context) {
</#if>
    super(pdo, context);
  }

  /**
   * Creates a persistent object for a PDO.
   *
   * @param pdo the persistent domain object
   * @param session the session
   */
<#if pdoInheritance != "none">
  public ${persistenceImplClass}(T pdo, Session session) {
<#else>
  public ${persistenceImplClass}(${pdoEntity} pdo, Session session) {
</#if>
    super(pdo, session);
  }

  /**
   * Creates a persistent object for a PDO.
   *
   * @param pdo the persistent domain object
   */
<#if pdoInheritance != "none">
  public ${persistenceImplClass}(T pdo) {
<#else>
  public ${persistenceImplClass}(${pdoEntity} pdo) {
</#if>
    super(pdo);
  }

  /**
   * Creates a persistent object.
   */
  public ${persistenceImplClass}() {
    super();
  }


  // @wurblet methods MethodsImpl

  // @wurblet relations PdoRelations

}
