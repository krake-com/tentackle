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

package org.tentackle.security;

import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoListener;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.pdo.SecurityDomain;
import org.tentackle.security.pdo.SecurityPersistence;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationListener;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionUtilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An ACL-based SecurityManager.
 * <p>
 * The rules themselves are PDOs ({@link Security}) and thus are stored in the database.
 * <p>
 * The list of rules is interpreted as an ACL (access control list).
 * Whenever a permission is checked, the rules are processed
 * until a rule fires. If no rule fires, the default result is returned,
 * which is either granted or denied.
 * <p>
 * The rules are processed per grantee in the order given by {@link #determineGranteesToCheck},
 * i.e., by default first the rules for the user, then the rules for all grantees.
 * Within a grantee, the rules are processed by priority (lowest first).
 * For PDOs, the rules for the object are checked before the rules for its class.
 *
 * @author harald
 */
public class DefaultSecurityManager implements SecurityManager {

  private static final Logger LOGGER = Logger.get(DefaultSecurityManager.class);


  /**
   * Security rule wrapper to hold the lazily generated security result.
   */
  private class Rule {
    private final Security security;
    private SecurityResult result;

    public Rule(Security security) {
      this.security = security;
    }

    /**
     * Gets the cached result.
     *
     * @return the result, never null
     */
    public SecurityResult getResult() {
      if (result == null) {
        SecurityPersistence spo = (SecurityPersistence) security.getPersistenceDelegate();   // bypass IH to speed up
        if (spo.isAllowed()) {
          result = createAcceptedSecurityResult(spo.getMessage(), false);
        }
        else {
          result = createDeniedSecurityResult(spo.getMessage(), false);
        }
      }
      return result;
    }
  }


  /**
   * key for a non-pdo class-only rule.
   */
  private static class ClassKey implements Comparable<ClassKey> {

    private final int granteeClassId;
    private final long granteeId;
    private final String className;
    private final int priority;
    private final long ruleId;

    // construct the entry
    public ClassKey(Security sec)  {
      this.granteeClassId = sec.getGranteeClassId();
      this.granteeId = sec.getGranteeId();
      this.className = sec.getObjectClassName();
      this.priority  = sec.getPriority();
      this.ruleId = sec.getId();
    }

    // to get the first/last key
    public ClassKey(GranteeDescriptor grantee, String className, int priority, long ruleId) {
      this.granteeClassId = grantee.getGranteeClassId();
      this.granteeId = grantee.getGranteeId();
      this.className = className;
      this.priority  = priority;
      this.ruleId = ruleId;
    }

    @Override
    public int compareTo(ClassKey k) {
      int rv = Integer.compare(granteeClassId, k.granteeClassId);
      if (rv == 0) {
        rv = Long.compare(granteeId, k.granteeId);
        if (rv == 0) {
          rv = className.compareTo(k.className);
          if (rv == 0)  {
            rv = Integer.compare(priority, k.priority);
            if (rv == 0) {
              rv = Long.compare(ruleId, k.ruleId);
            }
          }
        }
      }
      return rv;
    }

    @Override
    public int hashCode() {
      int hash = 3;
      hash = 67 * hash + granteeClassId;
      hash = 67 * hash + Long.hashCode(granteeId);
      hash = 67 * hash + Objects.hashCode(className);
      hash = 67 * hash + priority;
      hash = 67 * hash + Long.hashCode(ruleId);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final ClassKey other = (ClassKey) obj;
      if (this.granteeClassId != other.granteeClassId) {
        return false;
      }
      if (this.granteeId != other.granteeId) {
        return false;
      }
      if (this.priority != other.priority) {
        return false;
      }
      if (this.ruleId != other.ruleId) {
        return false;
      }
      return Objects.equals(this.className, other.className);
    }

  }


  /**
   * key for a pdo rule.
   */
  private static class PdoKey implements Comparable<PdoKey> {

    private final int granteeClassId;
    private final long granteeId;
    private final int objectClassId;
    private final long objectId;
    private final int priority;
    private final long ruleId;

    // construct the entry
    public PdoKey(Security sec)  {
      this.granteeClassId = sec.getGranteeClassId();
      this.granteeId = sec.getGranteeId();
      this.objectClassId = sec.getObjectClassId();
      this.objectId = sec.getObjectId();
      this.priority = sec.getPriority();
      this.ruleId = sec.getId();
    }

    // to get the first/last key
    public PdoKey(GranteeDescriptor grantee, int objectClassId, long objectId, int priority, long ruleId) {
      this.granteeClassId = grantee.getGranteeClassId();
      this.granteeId = grantee.getGranteeId();
      this.objectClassId = objectClassId;
      this.objectId = objectId;
      this.priority  = priority;
      this.ruleId = ruleId;
    }

    @Override
    public int compareTo(PdoKey k) {
      int rv = Integer.compare(granteeClassId, k.granteeClassId);
      if (rv == 0) {
        rv = Long.compare(granteeId, k.granteeId);
        if (rv == 0) {
          rv = Integer.compare(objectClassId, k.objectClassId);
          if (rv == 0) {
            rv = Long.compare(objectId, k.objectId);
            if (rv == 0) {
              rv = Integer.compare(priority, k.priority);
              if (rv == 0) {
                rv = Long.compare(ruleId, k.ruleId);
              }
            }
          }
        }
      }
      return rv;
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 73 * hash + granteeClassId;
      hash = 73 * hash + Long.hashCode(granteeId);
      hash = 73 * hash + objectClassId;
      hash = 73 * hash + Long.hashCode(objectId);
      hash = 73 * hash + priority;
      hash = 73 * hash + Long.hashCode(ruleId);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final PdoKey other = (PdoKey) obj;
      if (this.granteeClassId != other.granteeClassId) {
        return false;
      }
      if (this.granteeId != other.granteeId) {
        return false;
      }
      if (this.objectClassId != other.objectClassId) {
        return false;
      }
      if (this.objectId != other.objectId) {
        return false;
      }
      return this.priority == other.priority && this.ruleId == other.ruleId;
    }

  }


  /**
   * key for a pdo class-only rule.
   */
  private static class PdoClassKey implements Comparable<PdoClassKey> {

    private final int granteeClassId;
    private final long granteeId;
    private final int objectClassId;
    private final int priority;
    private final long ruleId;

    // construct the entry
    public PdoClassKey(Security sec)  {
      this.granteeClassId = sec.getGranteeClassId();
      this.granteeId = sec.getGranteeId();
      this.objectClassId = sec.getObjectClassId();
      this.priority = sec.getPriority();
      this.ruleId = sec.getId();
    }

    // to get the first/last key
    public PdoClassKey(GranteeDescriptor grantee, int objectClassId, int priority, long ruleId) {
      this.granteeClassId = grantee.getGranteeClassId();
      this.granteeId = grantee.getGranteeId();
      this.objectClassId = objectClassId;
      this.priority  = priority;
      this.ruleId = ruleId;
    }

    @Override
    public int compareTo(PdoClassKey k) {
      int rv = Integer.compare(granteeClassId, k.granteeClassId);
      if (rv == 0) {
        rv = Long.compare(granteeId, k.granteeId);
        if (rv == 0) {
          rv = Integer.compare(objectClassId, k.objectClassId);
          if (rv == 0) {
            rv = Integer.compare(priority, k.priority);
            if (rv == 0) {
              rv = Long.compare(ruleId, k.ruleId);
            }
          }
        }
      }
      return rv;
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 37 * hash + granteeClassId;
      hash = 37 * hash + Long.hashCode(granteeId);
      hash = 37 * hash + objectClassId;
      hash = 37 * hash + priority;
      hash = 37 * hash + Long.hashCode(ruleId);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final PdoClassKey other = (PdoClassKey) obj;
      if (this.granteeClassId != other.granteeClassId) {
        return false;
      }
      if (this.granteeId != other.granteeId) {
        return false;
      }
      if (this.objectClassId != other.objectClassId) {
        return false;
      }
      return this.priority == other.priority && this.ruleId == other.ruleId;
    }

  }


  // messages for predefined results
  private static final String DISABLED_SECURITY_MANAGER_MESSAGE = "security manager is disabled";
  private static final String NO_IDENTIFIABLE_RULES_MESSAGE = "no identifiable-rules match";
  private static final String NO_CLASS_RULES_MESSAGE = "no class-rules match";
  private static final String NO_GRANTEE_MESSAGE = "no grantee";


  private final Map<GranteeDescriptor, Collection<GranteeDescriptor>> granteeMap;   // maps root grantee to grantee descriptors
  private volatile TreeMap<ClassKey,Rule> classMap;           // non-pdo class rules (immutable map once created)
  private volatile TreeMap<PdoKey,Rule> pdoMap;               // object rules (immutable map once created)
  private volatile TreeMap<PdoClassKey,Rule> pdoClassMap;     // pdo class-only rules (immutable map once created)

  private volatile boolean invalid;                           // true if cache needs initialization
  private volatile boolean enabled;                           // true if security manager is enabled
  private volatile boolean acceptByDefault;                   // true if isAccepted() by default
  private ModificationListener securityListener;              // the listener to invalidate the security manager

  private final SecurityResult disabledResult;                // accept result if sec manager is disabled -> grants all
  private final SecurityResult identifiableAcceptedResult;    // accept result if no identifiable rules found
  private final SecurityResult identifiableDeniedResult;      // denied result if no identifiable rules found
  private final SecurityResult classAcceptedResult;           // accept result if no class rules found
  private final SecurityResult classDeniedResult;             // denied result if no class rules found
  private final SecurityResult noGranteeResult;               // accept result if no grantee (usually the system user)


  /**
   * Creates a security manager.<br>
   * The manager is disabled by default and must be enabled when the application is started up.
   * When disabled, all requests are granted, no matter if accept by default or deny by default.
   */
  public DefaultSecurityManager() {
    invalid = true;
    acceptByDefault = true;
    granteeMap = new ConcurrentHashMap<>();

    // create some predefined security results
    disabledResult = createAcceptedSecurityResult(DISABLED_SECURITY_MANAGER_MESSAGE, true);
    identifiableAcceptedResult = createAcceptedSecurityResult(NO_IDENTIFIABLE_RULES_MESSAGE, true);
    identifiableDeniedResult = createDeniedSecurityResult(NO_IDENTIFIABLE_RULES_MESSAGE, true);
    classAcceptedResult = createAcceptedSecurityResult(NO_CLASS_RULES_MESSAGE, true);
    classDeniedResult = createDeniedSecurityResult(NO_CLASS_RULES_MESSAGE, true);
    noGranteeResult = createAcceptedSecurityResult(NO_GRANTEE_MESSAGE, true);
  }

  @Override
  public void invalidate() {
    invalid = true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public boolean isAcceptByDefault() {
    return acceptByDefault;
  }

  @Override
  public void setAcceptByDefault(boolean acceptByDefault) {
    this.acceptByDefault = acceptByDefault;
  }

  @Override
  public SecurityResult evaluate(DomainContext context, Permission permission, int objectClassId, long objectId) {
    SecurityResult result = evaluateImpl(context, permission, objectClassId, objectId, null);
    if (result == null && objectId != 0) {
      // no explicit object rule fired: check class rules
      result = evaluateImpl(context, permission, objectClassId, 0, null);
    }
    if (result == null) {
      result = acceptByDefault ? identifiableAcceptedResult : identifiableDeniedResult;
    }
    return result;
  }

  @Override
  public SecurityResult evaluate(DomainContext context, Permission permission, Class<?> clazz) {
    SecurityResult result = evaluateImpl(context, permission, 0, 0, clazz);
    if (result == null) {
      result = acceptByDefault ? classAcceptedResult : classDeniedResult;
    }
    return result;
  }

  @Override
  public void removeObsoleteRules(Session session) {
    long txVoucher = session.begin("removeObsoleteRules");
    try {
      for (Security rule: createSecurityInstance(Pdo.createDomainContext(session)).selectAll()) {
        if (rule.getObjectId() != 0 &&
            // pdo vanished
            Pdo.create(SessionUtilities.getInstance().getClassName(
                rule.getObjectClassId()), session).selectSerial(rule.getObjectId()) == -1
            ||
            rule.getGranteeId() != 0 &&
            // grantee vanished
            Pdo.create(SessionUtilities.getInstance().getClassName(
                rule.getGranteeClassId()), session).selectSerial(rule.getGranteeId()) == -1) {

          rule.delete();
        }
      }
      session.commit(txVoucher);    // this will invalidate via listener
    }
    catch (RuntimeException ex) {
      session.rollback(txVoucher);
      LOGGER.severe("removing rules failed", ex);
    }
  }


  /**
   * Determines the session grantee.
   * <p>
   * If the grantee is null (for example a system user) all permissions will be granted.
   *
   * @param context the domain context
   * @return the grantee, null if accept all permissions
   */
  protected GranteeDescriptor determineGrantee(DomainContext context) {
    long granteeId = context.getSessionInfo().getUserId();
    if (granteeId <= 0) {
      return null;    // system user -> always ok
    }
    int granteeClassId = context.getSessionInfo().getUserClassId();
    if (granteeClassId == 0) {
      throw new SecurityException("missing grantee's class id");
    }
    return new GranteeDescriptor(granteeClassId, granteeId);
  }

  /**
   * Determines the grantees to check.<p>
   * Override this method to implement user groups, for example.
   * The grantees are checked in the order of the returned collection.
   * <p>
   * The result is cached per session grantee.
   * Since the method is invoked while a permission is being evaluated, it must not trigger
   * a security check for the same user, for example, by selecting PDOs with a domain context
   * of that user. Use a system context instead.
   *
   * @param context the domain context
   * @param grantee the session's grantee
   * @return the grantee descriptors
   */
  protected Collection<GranteeDescriptor> determineGranteesToCheck(DomainContext context, GranteeDescriptor grantee) {
    Collection<GranteeDescriptor> grantees = new ArrayList<>();
    // check rules for grantee
    grantees.add(grantee);
    // check rules for all
    grantees.add(new GranteeDescriptor(0, 0));
    return grantees;
  }

  /**
   * Creates an accepting security result.
   *
   * @param message the message
   * @param byDefault true if accepted by default
   * @return the result
   */
  protected SecurityResult createAcceptedSecurityResult(String message, boolean byDefault) {
    return new DefaultSecurityResult(message, true, byDefault);
  }

  /**
   * Creates a denying security result.
   *
   * @param message the message
   * @param byDefault true if denied by default
   * @return the result
   */
  protected SecurityResult createDeniedSecurityResult(String message, boolean byDefault) {
    return new DefaultSecurityResult(message, false, byDefault);
  }

  /**
   * Evaluates a permission for a pdo or regular class.
   *
   * @param context the domain context
   * @param permission the requested permission
   * @param objectClassId the class id, 0 if non-pdo class rule
   * @param objectId the object id, 0 for all objects of given class
   * @param clazz the non-pdo class, null if PDO rule
   * @return the SecurityResult, null if no matching rule found
   */
  protected SecurityResult evaluateImpl(DomainContext context, Permission permission,
                                        int objectClassId, long objectId, Class<?> clazz) {

    if (isEnabled()) {

      if (context == null) {
        throw new SecurityException("invalid domain context: null");
      }

      if (permission == null) {
        throw new SecurityException("invalid permission: null");
      }

      if (objectId < 0) {
        throw new SecurityException("invalid object ID: " + objectId);
      }

      if (objectClassId == 0 && clazz == null) {
        throw new SecurityException("no class or classId given");
      }

      GranteeDescriptor sessionGrantee = determineGrantee(context);
      if (sessionGrantee == null) {
        return noGranteeResult;
      }

      if (invalid) {
        if (context.getSession().isRemote()) {
          createSecurityInstance(context).assertRemoteSecurityManagerInitialized();
        }
        initialize(context.getSession());
      }

      // determine source map only once (must not change during loop below)
      TreeMap<PdoKey, Rule> localPdoMap;
      TreeMap<ClassKey, Rule> localClassMap;
      TreeMap<PdoClassKey, Rule> localPdoClassMap;
      if (clazz != null) {
        localClassMap = classMap;
        if (localClassMap.isEmpty()) {
          return null;
        }
        localPdoMap = null;
        localPdoClassMap = null;
      }
      else if (objectId == 0) {
        localPdoClassMap = pdoClassMap;
        if (localPdoClassMap.isEmpty()) {
          return null;
        }
        localPdoMap = null;
        localClassMap = null;
      }
      else {
        localPdoMap = pdoMap;
        if (localPdoMap.isEmpty()) {
          return null;
        }
        localClassMap = null;
        localPdoClassMap = null;
      }

      boolean fineLoggable = LOGGER.isFineLoggable();   // a little speedup

      Collection<GranteeDescriptor> granteesToCheck =
          granteeMap.computeIfAbsent(sessionGrantee, g -> determineGranteesToCheck(context, g));

      for (GranteeDescriptor grantee : granteesToCheck) {

        if (fineLoggable) {
          LOGGER.fine("Checking grantee={0}[{1}], context={2}, permission={3}, object={4}[{5}], class={6}",
                  sessionGrantee.getGranteeClassId(), sessionGrantee.getGranteeId(),
                  context, permission, objectClassId, objectId, clazz);
        }

        // the map of applicable security rules
        SortedMap<?, Rule> map;

        // all rules of the grantee for the target, whatever their priority
        if (localClassMap != null) {
          map = localClassMap.subMap(new ClassKey(grantee, clazz.getName(), Integer.MIN_VALUE, Long.MIN_VALUE), true,
                                     new ClassKey(grantee, clazz.getName(), Integer.MAX_VALUE, Long.MAX_VALUE), true);
        }
        else if (localPdoClassMap != null) {
          map = localPdoClassMap.subMap(new PdoClassKey(grantee, objectClassId, Integer.MIN_VALUE, Long.MIN_VALUE), true,
                                        new PdoClassKey(grantee, objectClassId, Integer.MAX_VALUE, Long.MAX_VALUE), true);
        }
        else {
          map = localPdoMap.subMap(new PdoKey(grantee, objectClassId, objectId, Integer.MIN_VALUE, Long.MIN_VALUE), true,
                                   new PdoKey(grantee, objectClassId, objectId, Integer.MAX_VALUE, Long.MAX_VALUE), true);
        }

        // walk through the security settings of the map
        // they are sorted by priority (rules with the same priority by their ID)!
        for (Rule rule : map.values()) {
          Security sec = rule.security;
          if (fineLoggable) {
            LOGGER.fine("evaluate {0}", sec);
          }
          // check if rule fires (bypassing IH for speed)
          if (((SecurityDomain) sec.getDomainDelegate()).evaluate(context, permission)) {
            if (fineLoggable) {
              LOGGER.fine(sec.isAllowed() ? "-> ACCEPT" : "-> DENY");
            }
            return rule.getResult();
          }
        }
      }

      return null;
    }

    return disabledResult;
  }

  /**
   * Initializes the security manager.
   *
   * @param session the session to load the security rules
   */
  protected synchronized void initialize(Session session) {
    if (invalid) {    // double check (invalid is volatile)

      // new instances to allow walking along the subMap during init (see evaluateImpl above)
      TreeMap<ClassKey, Rule> newClassMap = new TreeMap<>();
      TreeMap<PdoKey, Rule> newPdoMap = new TreeMap<>();
      TreeMap<PdoClassKey, Rule> newPdoClassMap = new TreeMap<>();

      // add all rules
      Collection<? extends Security> secRules = createSecurityInstance(Pdo.createDomainContext(session)).selectAllCached();
      for (Security sec : secRules) {
        Rule rule = new Rule(sec);
        if (sec.getObjectClassId() == 0) {
          newClassMap.put(new ClassKey(sec), rule);
        }
        else {
          if (sec.getObjectId() == 0) {
            newPdoClassMap.put(new PdoClassKey(sec), rule);
          }
          else {
            newPdoMap.put(new PdoKey(sec), rule);
          }
        }
      }

      if (securityListener == null) {
        // register listener once
        securityListener = createModificationListener();
        ModificationTracker.getInstance().addModificationListener(securityListener);
      }

      // switch to new maps, "invalid" flag cleared below!
      classMap = newClassMap;
      pdoMap = newPdoMap;
      pdoClassMap = newPdoClassMap;
      granteeMap.clear();

      invalid = false;

      LOGGER.fine("security manager initialized");
    }
  }

  /**
   * Creates the modification listener.
   *
   * @return the listener
   */
  protected ModificationListener createModificationListener() {
    return new PdoListener(Security.class) {

      @Override
      public void dataChanged(ModificationEvent ev) {
        invalidate();
      }
    };
  }

  /**
   * Creates a security instance.
   *
   * @param context the domain context of the security instance
   * @return the security instance
   */
  protected Security createSecurityInstance(DomainContext context) {
    return Pdo.create(Security.class, context);
  }

}
