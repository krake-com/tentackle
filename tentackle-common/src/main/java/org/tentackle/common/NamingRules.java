/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.common;


interface NamingRulesHolder {
  NamingRules INSTANCE = ServiceFactory.createService(NamingRules.class, NamingRules.class);
}


/**
 * Naming rules for classnames.<br>
 * Used at runtime to determine the remote delegates, at build time by some wurblets,
 * and by the wizard to generate PDO and operation sources.<br>
 * Can be replaced via {@code @Service(NamingRules.class)} for application-specific rules.
 * <p>
 * By default, the operation rules delegate to the corresponding PDO rules.
 * Hence, overriding a PDO rule also changes the operation rule, unless the operation rule is overridden as well.
 * The methods deriving the PDO or operation name from a class name return {@code null} if the
 * class name does not match the rule.
 *
 * @author harald
 */
// @Service does not work in tentackle-common, since the analyze goal of the tentackle-maven-plugin is not available at this build step yet.
// Needs explicit config in src/main/resources/META-INF/services.
@Service(NamingRules.class)    // defaults to self
public class NamingRules {

  /**
   * The naming rules singleton.
   *
   * @return the singleton
   */
  public static NamingRules getInstance() {
    return NamingRulesHolder.INSTANCE;
  }

  // by default, the suffixes for operations are the same as for PDOs

  /** classname suffix for the domain interface. */
  private static final String DOMAIN_SUFFIX = "Domain";

  /** classname suffix for the domain implementation. */
  private static final String DOMAIN_IMPL_SUFFIX = "DomainImpl";

  /** classname suffix for the persistence interface. */
  private static final String PERSISTENCE_SUFFIX = "Persistence";

  /** classname suffix for the persistence implementation. */
  private static final String PERSISTENCE_IMPL_SUFFIX = "PersistenceImpl";

  /** classname suffix for the remote interface. */
  private static final String REMOTE_SUFFIX = "RemoteDelegate";

  /** classname suffix for the remote implementation. */
  private static final String REMOTE_IMPL_SUFFIX = "RemoteDelegateImpl";

  /** sub package for remote classes. */
  private static final String REMOTE_SUB_PACKAGE = ".trip";


  /**
   * Creates the naming rules.
   */
  public NamingRules() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the simple class name of the domain interface of a PDO.
   *
   * @param pdoName the PDO name
   * @return the interface name
   */
  public String getPdoDomainInterface(String pdoName) {
    return pdoName + DOMAIN_SUFFIX;
  }

  /**
   * Gets the PDO name from the name of a domain interface.
   *
   * @param domainInterface the interface name
   * @return the PDO name, null if domainInterface is not a valid PDO domain interface name
   */
  public String getPdoFromDomainInterface(String domainInterface) {
    if (domainInterface.endsWith(DOMAIN_SUFFIX)) {
      return domainInterface.substring(0, domainInterface.length() - DOMAIN_SUFFIX.length());
    }
    return null;
  }

  /**
   * Gets the simple class name of the persistence interface of a PDO.
   *
   * @param pdoName the PDO name
   * @return the interface name
   */
  public String getPdoPersistenceInterface(String pdoName) {
    return pdoName + PERSISTENCE_SUFFIX;
  }

  /**
   * Gets the PDO name from the name of a persistence interface.
   *
   * @param persistenceInterface the interface name
   * @return the PDO name, null if persistenceInterface is not a valid PDO persistence interface name
   */
  public String getPdoFromPersistenceInterface(String persistenceInterface) {
    if (persistenceInterface.endsWith(PERSISTENCE_SUFFIX)) {
      return persistenceInterface.substring(0, persistenceInterface.length() - PERSISTENCE_SUFFIX.length());
    }
    return null;
  }

  /**
   * Gets the simple class name of the domain implementation of a PDO.
   *
   * @param pdoName the PDO name
   * @return the implementation name
   */
  public String getPdoDomainImplementation(String pdoName) {
    return pdoName + DOMAIN_IMPL_SUFFIX;
  }

  /**
   * Gets the PDO name from the name of a domain implementation.
   *
   * @param domainImplementation the implementation name
   * @return the PDO name, null if domainImplementation is not a valid PDO domain implementation name
   */
  public String getPdoFromDomainImplementation(String domainImplementation) {
    if (domainImplementation.endsWith(DOMAIN_IMPL_SUFFIX)) {
      return domainImplementation.substring(0, domainImplementation.length() - DOMAIN_IMPL_SUFFIX.length());
    }
    return null;
  }

  /**
   * Gets the simple class name of the persistence implementation of a PDO.
   *
   * @param pdoName the PDO name
   * @return the implementation name
   */
  public String getPdoPersistenceImplementation(String pdoName) {
    return pdoName + PERSISTENCE_IMPL_SUFFIX;
  }

  /**
   * Gets the PDO name from the name of a persistence implementation.
   *
   * @param persistenceImplementation the implementation name
   * @return the PDO name, null if persistenceImplementation is not a valid PDO persistence implementation name
   */
  public String getPdoFromPersistenceImplementation(String persistenceImplementation) {
    if (persistenceImplementation.endsWith(PERSISTENCE_IMPL_SUFFIX)) {
      return persistenceImplementation.substring(0, persistenceImplementation.length() - PERSISTENCE_IMPL_SUFFIX.length());
    }
    return null;
  }

  /**
   * Gets the simple class name of the remote interface of a PDO.
   *
   * @param pdoName the PDO name
   * @return the interface name
   */
  public String getPdoRemoteInterface(String pdoName) {
    return pdoName + REMOTE_SUFFIX;
  }

  /**
   * Gets the PDO name from the name of a remote interface.
   *
   * @param remoteInterface the interface name
   * @return the PDO name, null if remoteInterface is not a valid PDO remote interface name
   */
  public String getPdoFromRemoteInterface(String remoteInterface) {
    if (remoteInterface.endsWith(REMOTE_SUFFIX)) {
      return remoteInterface.substring(0, remoteInterface.length() - REMOTE_SUFFIX.length());
    }
    return null;
  }

  /**
   * Gets the simple class name of the remote implementation of a PDO.
   *
   * @param pdoName the PDO name
   * @return the implementation name
   */
  public String getPdoRemoteImplementation(String pdoName) {
    return pdoName + REMOTE_IMPL_SUFFIX;
  }

  /**
   * Gets the PDO name from the name of a remote implementation.
   *
   * @param remoteImplementation the implementation name
   * @return the PDO name, null if remoteImplementation is not a valid PDO remote implementation name
   */
  public String getPdoFromRemoteImplementation(String remoteImplementation) {
    if (remoteImplementation.endsWith(REMOTE_IMPL_SUFFIX)) {
      return remoteImplementation.substring(0, remoteImplementation.length() - REMOTE_IMPL_SUFFIX.length());
    }
    return null;
  }

  /**
   * Gets the package name of the remote interface of a PDO.
   *
   * @param pdoPersistenceImplementationPackageName the package name of the persistence implementation
   * @return the package name of the remote interface
   */
  public String getPdoRemoteInterfacePackageName(String pdoPersistenceImplementationPackageName) {
    return pdoPersistenceImplementationPackageName + REMOTE_SUB_PACKAGE;
  }

  /**
   * Gets the package name of the remote implementation of a PDO.
   *
   * @param pdoPersistenceImplementationPackageName the package name of the persistence implementation
   * @return the package name of the remote implementation
   */
  public String getPdoRemoteImplementationPackageName(String pdoPersistenceImplementationPackageName) {
    return getPdoRemoteInterfacePackageName(pdoPersistenceImplementationPackageName);
  }


  /**
   * Gets the simple class name of the domain interface of an operation.
   *
   * @param operationName the operation name
   * @return the interface name
   */
  public String getOperationDomainInterface(String operationName) {
    return getPdoDomainInterface(operationName);
  }

  /**
   * Gets the operation name from the name of a domain interface.
   *
   * @param domainInterface the interface name
   * @return the operation name, null if domainInterface is not a valid operation domain interface name
   */
  public String getOperationFromDomainInterface(String domainInterface) {
    return getPdoFromDomainInterface(domainInterface);
  }

  /**
   * Gets the simple class name of the persistence interface of an operation.
   *
   * @param operationName the operation name
   * @return the interface name
   */
  public String getOperationPersistenceInterface(String operationName) {
    return getPdoPersistenceInterface(operationName);
  }

  /**
   * Gets the operation name from the name of a persistence interface.
   *
   * @param persistenceInterface the interface name
   * @return the operation name, null if persistenceInterface is not a valid operation persistence interface name
   */
  public String getOperationFromPersistenceInterface(String persistenceInterface) {
    return getPdoFromPersistenceInterface(persistenceInterface);
  }

  /**
   * Gets the simple class name of the domain implementation of an operation.
   *
   * @param operationName the operation name
   * @return the implementation name
   */
  public String getOperationDomainImplementation(String operationName) {
    return getPdoDomainImplementation(operationName);
  }

  /**
   * Gets the operation name from the name of a domain implementation.
   *
   * @param domainImplementation the implementation name
   * @return the operation name, null if domainImplementation is not a valid operation domain implementation name
   */
  public String getOperationFromDomainImplementation(String domainImplementation) {
    return getPdoFromDomainImplementation(domainImplementation);
  }

  /**
   * Gets the simple class name of the persistence implementation of an operation.
   *
   * @param operationName the operation name
   * @return the implementation name
   */
  public String getOperationPersistenceImplementation(String operationName) {
    return getPdoPersistenceImplementation(operationName);
  }

  /**
   * Gets the operation name from the name of a persistence implementation.
   *
   * @param persistenceImplementation the implementation name
   * @return the operation name, null if persistenceImplementation is not a valid operation persistence implementation name
   */
  public String getOperationFromPersistenceImplementation(String persistenceImplementation) {
    return getPdoFromPersistenceImplementation(persistenceImplementation);
  }

  /**
   * Gets the simple class name of the remote interface of an operation.
   *
   * @param operationName the operation name
   * @return the interface name
   */
  public String getOperationRemoteInterface(String operationName) {
    return getPdoRemoteInterface(operationName);
  }

  /**
   * Gets the operation name from the name of a remote interface.
   *
   * @param remoteInterface the interface name
   * @return the operation name, null if remoteInterface is not a valid operation remote interface name
   */
  public String getOperationFromRemoteInterface(String remoteInterface) {
    return getPdoFromRemoteInterface(remoteInterface);
  }

  /**
   * Gets the simple class name of the remote implementation of an operation.
   *
   * @param operationName the operation name
   * @return the implementation name
   */
  public String getOperationRemoteImplementation(String operationName) {
    return getPdoRemoteImplementation(operationName);
  }

  /**
   * Gets the operation name from the name of a remote implementation.
   *
   * @param remoteImplementation the implementation name
   * @return the operation name, null if remoteImplementation is not a valid operation remote implementation name
   */
  public String getOperationFromRemoteImplementation(String remoteImplementation) {
    return getPdoFromRemoteImplementation(remoteImplementation);
  }

  /**
   * Gets the package name of the remote interface of an operation.
   *
   * @param operationPersistenceImplementationPackageName the package name of the persistence implementation
   * @return the package name of the remote interface
   */
  public String getOperationRemoteInterfacePackageName(String operationPersistenceImplementationPackageName) {
    return getPdoRemoteInterfacePackageName(operationPersistenceImplementationPackageName);
  }

  /**
   * Gets the package name of the remote implementation of an operation.
   *
   * @param operationPersistenceImplementationPackageName the package name of the persistence implementation
   * @return the package name of the remote implementation
   */
  public String getOperationRemoteImplementationPackageName(String operationPersistenceImplementationPackageName) {
    return getOperationRemoteInterfacePackageName(operationPersistenceImplementationPackageName);
  }

}
