
package ${persistenceImplPackage};

import ${operationPackage}.${operationInterface};
<#if persistencePackage != persistenceImplPackage>
import ${persistencePackage}.${persistenceInterface};
</#if>
<#if remoteEnabled == "true">
import ${persistenceImplPackage}.rmi.${operationInterface}RemoteDelegate;
</#if>

import java.rmi.RemoteException;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentOperationService;
import org.tentackle.pdo.Operation;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

/**
 * Persistence implementation for ${operationInterface}.
 */
@PersistentOperationService(${operationInterface}.class)
<#if abstractOperation == "true">
public class ${persistenceImplementation}<T extends ${operationInterface}<T>, P extends ${persistenceImplementation}<T,P>>
       extends ${superPersistenceImplementation}<T,P> implements ${persistenceInterface}<T> {
<#else>
public class ${persistenceImplementation} extends ${superPersistenceImplementation}<${operationInterface}, ${persistenceImplementation}> implements ${persistenceInterface} {
</#if>

  /** serial version UID. */
  private static final long serialVersionUID = 1L;

<#if abstractOperation == "true">
  private static final PersistentOperationClassVariables CLASSVARIABLES =
          new PersistentOperationClassVariables(${operationInterface}.class, ${persistenceImplementation}.class);
<#else>
  private static final PersistentOperationClassVariables<${operationInterface}, ${persistenceImplementation}> CLASSVARIABLES =
          new PersistentOperationClassVariables<>(${operationInterface}.class, ${persistenceImplementation}.class);
</#if>

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   * @param context the domain context
   */
<#if abstractOperation == "true">
  public ${persistenceImplementation}(T operation, DomainContext context) {
<#else>
  public ${persistenceImplementation}(${operationInterface} operation, DomainContext context) {
</#if>
    super(operation, context);
  }

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   * @param session the session
   */
<#if abstractOperation == "true">
  public ${persistenceImplementation}(T operation, Session session) {
<#else>
  public ${persistenceImplementation}(${operationInterface} operation, Session session) {
</#if>
    super(operation, session);
  }

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   */
<#if abstractOperation == "true">
  public ${persistenceImplementation}(T operation) {
<#else>
  public ${persistenceImplementation}(${operationInterface} operation) {
</#if>
    super(operation);
  }

  /**
   * Creates a persistent object.
   */
  public ${persistenceImplementation}() {
    super();
  }

}
