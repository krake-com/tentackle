/**
 * Tentackle - http://www.tentackle.org
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

// Created on September 12, 2002, 2:38 PM

package org.tentackle.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoListener;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.security.pdo.Security;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationListener;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionUtilities;



/**
 * An ACL-based SecurityManager.
 * <p>
 * The rules themselves are PDOs ({@link Security}) and thus are stored in the database.
 * <p>
 * The list of rules is interpreted as an ACL (access control list).
 * Whenever a permission is checked, the list is processed
 * until a rule fires. If no rule fires the default result is returned,
 * which is ether granted or denied.<br>
 *
 * @author harald
 */
public class DefaultSecurityManager implements SecurityManager {

  /**
   * @todo: use new optimistic StampedLock.
   * ReentrantReadWriteLock doesnt provide any significant advantage over synchronized.
   * Maybe StampedLock does...
   */

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSecurityManager.class);


  // key for a non-pdo class-only rule
  private static class ClassKey implements Comparable<ClassKey> {

    private final int granteeClassId;
    private final long granteeId;
    private final String className;
    private final int priority;

    // construct the entry
    public ClassKey(Security sec)  {
      this.granteeClassId = sec.getGranteeClassId();
      this.granteeId = sec.getGranteeId();
      this.className = sec.getObjectClassName();
      this.priority  = sec.getPriority();
    }

    // to get the first/last key
    public ClassKey(GranteeDescriptor grantee, String className, int priority) {
      this.granteeClassId = grantee.getGranteeClassId();
      this.granteeId = grantee.getGranteeId();
      this.className = className;
      this.priority  = priority;
    }

    @Override
    public int compareTo(ClassKey k) {
      int rv = granteeClassId - k.granteeClassId;
      if (rv == 0) {
        rv = Long.compare(granteeId, k.granteeId);
        if (rv == 0) {
          rv = className.compareTo(k.className);
          if (rv == 0)  {
            rv = priority - k.priority;
          }
        }
      }
      return rv;
    }

    @Override
    public int hashCode() {
      int hash = 3;
      hash = 67 * hash + this.granteeClassId;
      hash = 67 * hash + (int) (this.granteeId ^ (this.granteeId >>> 32));
      hash = 67 * hash + Objects.hashCode(this.className);
      hash = 67 * hash + this.priority;
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
      return Objects.equals(this.className, other.className);
    }

  }

  // key for a pdo rule
  private static class PdoKey implements Comparable<PdoKey> {

    private final int granteeClassId;
    private final long granteeId;
    private final int objectClassId;
    private final long objectId;
    private final int priority;

    // construct the entry
    public PdoKey(Security sec)  {
      this.granteeClassId = sec.getGranteeClassId();
      this.granteeId = sec.getGranteeId();
      this.objectClassId = sec.getObjectClassId();
      this.objectId = sec.getObjectId();
      this.priority = sec.getPriority();
    }

    // to get the first/last key
    public PdoKey(GranteeDescriptor grantee, int objectClassId, long objectId, int priority) {
      this.granteeClassId = grantee.getGranteeClassId();
      this.granteeId = grantee.getGranteeId();
      this.objectClassId = objectClassId;
      this.objectId = objectId;
      this.priority  = priority;
    }

    @Override
    public int compareTo(PdoKey k) {
      int rv = granteeClassId - k.granteeClassId;
      if (rv == 0) {
        rv = Long.compare(granteeId, k.granteeId);
        if (rv == 0) {
          rv = objectClassId - k.objectClassId;
          if (rv == 0) {
            rv = Long.compare(objectId, k.objectId);
            if (rv == 0) {
              rv = priority - k.priority;
            }
          }
        }
      }
      return rv;
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 73 * hash + this.granteeClassId;
      hash = 73 * hash + (int) (this.granteeId ^ (this.granteeId >>> 32));
      hash = 73 * hash + this.objectClassId;
      hash = 73 * hash + (int) (this.objectId ^ (this.objectId >>> 32));
      hash = 73 * hash + this.priority;
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
      return this.priority == other.priority;
    }

  }


  // key for a pdo class-only rule
  private static class PdoClassKey implements Comparable<PdoClassKey> {

    private final int granteeClassId;
    private final long granteeId;
    private final int objectClassId;
    private final int priority;

    // construct the entry
    public PdoClassKey(Security sec)  {
      this.granteeClassId = sec.getGranteeClassId();
      this.granteeId = sec.getGranteeId();
      this.objectClassId = sec.getObjectClassId();
      this.priority = sec.getPriority();
    }

    // to get the first/last key
    public PdoClassKey(GranteeDescriptor grantee, int objectClassId, int priority) {
      this.granteeClassId = grantee.getGranteeClassId();
      this.granteeId = grantee.getGranteeId();
      this.objectClassId = objectClassId;
      this.priority  = priority;
    }

    @Override
    public int compareTo(PdoClassKey k) {
      int rv = granteeClassId - k.granteeClassId;
      if (rv == 0) {
        rv = Long.compare(granteeId, k.granteeId);
        if (rv == 0) {
          rv = objectClassId - k.objectClassId;
          if (rv == 0) {
            rv = priority - k.priority;
          }
        }
      }
      return rv;
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 37 * hash + this.granteeClassId;
      hash = 37 * hash + (int) (this.granteeId ^ (this.granteeId >>> 32));
      hash = 37 * hash + this.objectClassId;
      hash = 37 * hash + this.priority;
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
      return this.priority == other.priority;
    }

  }




  private final TreeMap<ClassKey,Security> classMap;          // non-pdo class rules
  private final TreeMap<PdoKey,Security> pdoMap;              // object rules
  private final TreeMap<PdoClassKey,Security> pdoClassMap;    // pdo class-only rules
  private final Map<GranteeDescriptor, Collection<GranteeDescriptor>> granteeMap;   // maps root grantee to grantee descriptors

  private volatile boolean invalid;                           // true if cache needs initialization
  private boolean enabled;                                    // true if security manager is enabled
  private boolean acceptByDefault;                            // true if isAccepted() by default
  private ModificationListener securityListener;              // the listener to invalidate the security manager


  /**
   * Creates a security manager.<br>
   * The manager is disabled by default and must be enabled when the application is started up.
   */
  public DefaultSecurityManager() {
    invalid = true;
    acceptByDefault = true;
    classMap = new TreeMap<>();
    pdoMap = new TreeMap<>();
    pdoClassMap = new TreeMap<>();
    granteeMap = new HashMap<>();
  }


  /**
   * Invalidates this manager.
   */
  @Override
  public void invalidate() {
    invalid = true;
  }


  /**
   * Determines whether this manager is enabled.
   *
   * @return true if enabled.
   */
  @Override
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Enables/disables this security manager.<br>
   * If it is disabled, all requests return GRANT_DEFAULT, which usually
   * will be treated by the application as GRANT_ACCEPT.
   * If enabled, the real GRANT will be returned.
   * Notice that an enabled manager will return GRANT_DENY if the userId is 0!
   *
   * @param enabled New value of property enabled.
   * @see #setAcceptByDefault(boolean)
   */
  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }


  /**
   * Determines whether this manager accepts by default,
   * i.e. if no matching rule is found for a permission.
   *
   * @return true if accept by default
   */
  @Override
  public boolean isAcceptByDefault() {
    return acceptByDefault;
  }

  /**
   * Sets the default behaviour for "no rules found" for the
   * {@link SecurityResult} to "accepted".
   * This is the default.
   * <p>
   * Notice: setting both acceptByDefault and denyByDefault to false will
   * return "neither accepted nor denied" security results if no rules fire.
   *
   * @param acceptByDefault true if {@link SecurityResult#isAccepted} returns true if no rules found.
   */
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
    return result == null ? createDefaultSecurityResult("no identifiable-rules match") : result;
  }


  @Override
  public SecurityResult evaluate(DomainContext context, Permission permission, Class<?> clazz) {
    SecurityResult result = evaluateImpl(context, permission, 0, 0, clazz);
    return result == null ? createDefaultSecurityResult("no class-rules match") : result;
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
    catch (Exception ex) {
      session.rollback(txVoucher);
      LOGGER.severe("removing rules failed", ex);
    }
  }


  /**
   * Creates a default security result.<br>
   * This result is returned, if no matching rules were found.
   *
   * @param message the message
   * @return the result
   */
  protected SecurityResult createDefaultSecurityResult(String message) {
    if (isAcceptByDefault()) {
      return createdAcceptedSecurityResult(message, true);
    }
    return createDeniedSecurityResult(message, true);
  }

  /**
   * Creates an accepting security result.
   *
   * @param message the message
   * @param byDefault true if accepted by default
   * @return the result
   */
  protected SecurityResult createdAcceptedSecurityResult(String message, boolean byDefault) {
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
   * Determines the session grantee.
   * <p>
   * If the grantee is null (for example a system user) all permissions will be granted.
   *
   * @param context the domain context
   * @return the grantee, null if accept all permissions
   */
  public GranteeDescriptor determineGrantee(DomainContext context) {
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
   *
   * @param context the domain context
   * @param grantee the session's grantee
   * @return the grantee descriptors
   */
  public Collection<GranteeDescriptor> determineGranteesToCheck(DomainContext context, GranteeDescriptor grantee) {
    Collection<GranteeDescriptor> grantees = new ArrayList<>();
    // check rules for grantee
    grantees.add(grantee);
    // check rules for all
    grantees.add(new GranteeDescriptor(0, 0));
    return grantees;
  }


  /**
   * Evaluates a permission for a pdo or regular class.
   *
   * @param context the domain context
   * @param permission the requested permission
   * @param objectClassId the class id, 0 if non-pdo class rule
   * @param objectId the object id, 0 for all objects of given class
   * @param clazz the non-pdo class, null pdo rule
   * @return the SecurityResult, null if no matching rule found
   */
  protected SecurityResult evaluateImpl(DomainContext context, Permission permission,
                                        int objectClassId, long objectId, Class<?> clazz) {

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

    boolean fineLoggable = LOGGER.isFineLoggable();   // little speedup

    if (!isEnabled()) {
      if (fineLoggable) {
        LOGGER.fine("SecurityManager is disabled -> GRANT_DEFAULT");
      }
      return createDefaultSecurityResult("security manager is disabled");
    }

    if (invalid) {
      if (context.getSession().isRemote()) {
        createSecurityInstance(context).assertRemoteSecurityManagerInitialized();
      }
      initialize(context.getSession());
    }

    GranteeDescriptor sessionGrantee = determineGrantee(context);
    if (sessionGrantee == null) {
      return createdAcceptedSecurityResult(null, true);
    }

    synchronized(this) {

      Collection<GranteeDescriptor> granteesToCheck = granteeMap.get(sessionGrantee);
      if (granteesToCheck == null) {
        granteesToCheck = determineGranteesToCheck(context, sessionGrantee);
        granteeMap.put(sessionGrantee, granteesToCheck);
      }

      for (GranteeDescriptor grantee: granteesToCheck) {

        if (fineLoggable) {
          LOGGER.fine("Checking grantee={0}[{1}], context={2}, permission={3}, object={4}[{5}], class={6}",
                      sessionGrantee.getGranteeClassId(), sessionGrantee.getGranteeId(),
                      context, permission, objectClassId, objectId, clazz);
        }

        // the map of applicable security rules
        SortedMap<?, Security> map;

        if (clazz != null) {
          map = classMap.subMap(new ClassKey(grantee, clazz.getName(), 0),
                                new ClassKey(grantee, clazz.getName(), Integer.MAX_VALUE));
        }
        else  {
          if (objectId == 0) {
            map = pdoClassMap.subMap(new PdoClassKey(grantee, objectClassId, 0),
                                     new PdoClassKey(grantee, objectClassId, Integer.MAX_VALUE));
          }
          else {
            map = pdoMap.subMap(new PdoKey(grantee, objectClassId, objectId, 0),
                                new PdoKey(grantee, objectClassId, objectId, Integer.MAX_VALUE));
          }
        }

        // walk through the security settings of the map
        // they are sorted by priority!
        for (Security sec : map.values()) {
          if (fineLoggable) {
            LOGGER.fine("evaluate {0}", sec);
          }
          // check if rule fires
          if (sec.evaluate(context, permission)) {
            // rule applies!
            if (fineLoggable) {
              LOGGER.fine(sec.isAllowed() ? "-> ACCEPT" : "-> DENY");
            }
            if (sec.isAllowed()) {
              return createdAcceptedSecurityResult(sec.getMessage(), false);
            }
            else {
              return createDeniedSecurityResult(sec.getMessage(), false);
            }
          }
        }
      }
    }

    if (fineLoggable) {
      LOGGER.fine("no rule matched");
    }
    return null;
  }


  /**
   * Creates the domain context to load security rules.
   *
   * @param evaluationContext the domain context used in evaluation
   * @return the domain context that can be used to initialize the manager
   */
  protected DomainContext createInitializationContext(DomainContext evaluationContext) {
    return Pdo.createDomainContext(evaluationContext.getSession());
  }


  /**
   * Adds a rule to the ACL cache.
   *
   * @param rule the rule to add
   */
  public void addRule(Security rule) {
    if (rule.getObjectClassId() == 0) {
      classMap.put(new ClassKey(rule), rule);
    }
    else {
      if (rule.getObjectId() == 0) {
        pdoClassMap.put(new PdoClassKey(rule), rule);
      }
      else {
        pdoMap.put(new PdoKey(rule), rule);
      }
    }
  }


  /**
   * Initializes the ACL.
   *
   * @param session the session to load the security rules
   */
  protected synchronized void initialize(Session session) {

    classMap.clear();
    pdoMap.clear();
    pdoClassMap.clear();

    // add all rules
    Collection<? extends Security> rules = createSecurityInstance(Pdo.createDomainContext(session)).selectAllCached();
    for (Security rule : rules) {
      addRule(rule);
    }

    invalid = false;

    if (securityListener == null) {
      // register listener once
      securityListener = createModificationListener();
      ModificationTracker.getInstance().addModificationListener(securityListener);
    }

    LOGGER.fine("security manager initialized");
  }


  /**
   * Creates the modification listener.
   *
   * @return the listener, null if modifications detected somehow differently
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
