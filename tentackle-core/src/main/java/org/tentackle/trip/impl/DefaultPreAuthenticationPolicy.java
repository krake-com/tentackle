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

package org.tentackle.trip.impl;

import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.Registry;
import org.tentackle.trip.RejectionLog;
import org.tentackle.trip.TripPolicyViolationException;
import org.tentackle.trip.TripPreAuth;
import org.tentackle.trip.remote.IdentifiedRemoteCall;
import org.tentackle.trip.remote.InitialRemoteCall;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Default implementation of a {@link PreAuthenticationPolicy}.
 * <p>
 * The allowlist holds the TRIP remote calls, the boxed primitives, {@link String}, {@link UUID},
 * {@link Locale}, {@link TimeZone}, {@link org.tentackle.common.EncryptedProperties}, the plain JDK
 * lists, sets and maps, and every type annotated with {@link TripPreAuth}. More classes can be added with {@link #allow(String...)}.
 * <p>
 * The policy also keeps track of the unauthenticated connections of the transports it is installed
 * on. When a limit is reached, the oldest unauthenticated connection is closed to admit the new one.
 * This favors clients that log in quickly over connections kept open without logging in.
 * A connection that sends nothing within {@link #setFirstMessageTimeout(long) firstMessageTimeout}
 * after it was opened, or within {@link #setIdleTimeout(long) idleTimeout} after its last call,
 * is closed as well. A host opening more new connections than {@link #setConnectionRate(int) connectionRate}
 * per second, beyond a {@link #setConnectionBurst(int) burst}, is refused before anything is read from them.
 * <p>
 * In {@link Mode#AUDIT} mode, each distinct violation is logged once and nothing is refused.
 */
public class DefaultPreAuthenticationPolicy implements PreAuthenticationPolicy {

  private static final Logger LOGGER = Logger.get(DefaultPreAuthenticationPolicy.class);

  /** Default maximum size of a message in bytes. */
  public static final int DEFAULT_MAX_MESSAGE_SIZE = 256 * 1024;

  /** Default maximum number of elements of an array, collection or map. */
  public static final int DEFAULT_MAX_ELEMENTS = 4096;

  /** Default maximum nesting depth of objects, arrays, collections and maps. */
  public static final int DEFAULT_MAX_DEPTH = 32;

  /** Default maximum number of messages per connection before authentication. */
  public static final int DEFAULT_MAX_MESSAGES = 32;

  /** Maximum length of a violation message: it may contain names chosen by the client. */
  private static final int MAX_VIOLATION_LENGTH = 200;

  /** Default wait time for dictionary entries in milliseconds. */
  public static final long DEFAULT_DICTIONARY_WAIT_TIME = 5000;

  /** Default maximum number of unauthenticated connections. */
  public static final int DEFAULT_MAX_CONNECTIONS = 1024;

  /** Default maximum number of unauthenticated connections per host. */
  public static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 64;

  /** Default timeout until the first message is received in milliseconds. */
  public static final long DEFAULT_FIRST_MESSAGE_TIMEOUT = 30000;

  /** Default timeout until the next message is received after a call in milliseconds. */
  public static final long DEFAULT_IDLE_TIMEOUT = 30000;

  /** Default length of the network prefix IPv6 clients are grouped by. */
  public static final int DEFAULT_IPV6_PREFIX_LENGTH = 64;

  /** Default number of new connections per second a host may open in the long run. */
  public static final int DEFAULT_CONNECTION_RATE = 20;

  /** Default number of new connections a host may open at once. */
  public static final int DEFAULT_CONNECTION_BURST = 64;

  /** Number of hosts whose connection rate is tracked before the idle ones are forgotten. */
  private static final int RATE_PRUNE_THRESHOLD = 1024;

  /** Maximum number of distinct violations logged in audit mode. */
  private static final int MAX_AUDITED = 1000;

  /** The name of a primitive array's element, which is always allowed. */
  private static final String PRIMITIVE = "";


  /**
   * Holds the scheduler for the message deadlines.<br>
   * Shared by all policies, created on first use.
   */
  private static final class SchedulerHolder {
    private static final ScheduledThreadPoolExecutor SCHEDULER = createScheduler();

    private static ScheduledThreadPoolExecutor createScheduler() {
      ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
        1, Thread.ofPlatform().daemon().name("TRIP pre-authentication deadline").factory());
      scheduler.setRemoveOnCancelPolicy(true);
      return scheduler;
    }
  }


  /**
   * The admission of an unauthenticated connection.
   */
  private final class AdmissionImpl implements Admission {

    private final String host;
    private final String hostKey;
    private final Runnable closer;
    private final BooleanSupplier authenticated;
    private volatile ScheduledFuture<?> deadline;
    private volatile boolean busy;
    private boolean released;

    private AdmissionImpl(String host, Runnable closer, BooleanSupplier authenticated) {
      this.host = host;
      this.hostKey = hostKey(host);
      this.closer = closer;
      this.authenticated = authenticated;
    }

    @Override
    public void messageReceived() {
      busy = true;
      cancelDeadline();
    }

    @Override
    public void callCompleted() {
      busy = false;
      long timeout = idleTimeout;
      if (timeout > 0) {
        // a client that stops sending after a call must not keep the connection forever
        startDeadline(timeout, "idle for " + timeout + " ms");
      }
    }

    @Override
    public void release() {
      cancelDeadline();
      synchronized (admissions) {
        remove(this);
      }
    }

    /**
     * Starts the deadline for the next message.
     *
     * @param timeout the timeout in milliseconds
     * @param reason the reason logged when the connection is closed
     */
    private void startDeadline(long timeout, String reason) {
      cancelDeadline();
      deadline = SchedulerHolder.SCHEDULER.schedule(() -> deadlineReached(reason), timeout, TimeUnit.MILLISECONDS);
    }

    private void cancelDeadline() {
      ScheduledFuture<?> future = deadline;
      if (future != null) {
        deadline = null;
        future.cancel(false);
      }
    }

    private void deadlineReached(String reason) {
      deadline = null;
      synchronized (admissions) {
        if (released) {
          return;
        }
      }
      if (!busy && !authenticated.getAsBoolean()) {   // busy: a message arrived just in time
        // Closing may block, for example, a TLS socket in the middle of its handshake:
        // the scheduler is shared by all connections and must not wait for it.
        Thread.ofVirtual().name("TRIP pre-authentication closer")
              .start(() -> close(this, reason));
      }
    }

    @Override
    public String toString() {
      return "unauthenticated connection from " + host;
    }
  }


  private final Mode mode;
  private final Set<String> allowedClassNames;
  private final Set<String> audited;
  private final LinkedHashSet<AdmissionImpl> admissions;    // oldest first
  private final Map<String, Integer> admissionsPerHost;
  private final Map<String, RateBucket> rateBuckets;        // guarded by admissions
  private long lastRatePrune;                               // guarded by admissions

  private volatile int maxMessageSize;
  private volatile int maxElements;
  private volatile int maxDepth;
  private volatile int maxMessages;
  private volatile long dictionaryWaitTime;
  private volatile int maxConnections;
  private volatile int maxConnectionsPerHost;
  private volatile long firstMessageTimeout;
  private volatile long idleTimeout;
  private volatile int ipv6PrefixLength;
  private volatile int connectionRate;
  private volatile int connectionBurst;


  /**
   * Creates a policy.
   *
   * @param mode the mode
   */
  public DefaultPreAuthenticationPolicy(Mode mode) {
    this.mode = Objects.requireNonNull(mode);
    allowedClassNames = ConcurrentHashMap.newKeySet();
    audited = ConcurrentHashMap.newKeySet();
    admissions = new LinkedHashSet<>();
    admissionsPerHost = new HashMap<>();
    rateBuckets = new HashMap<>();
    maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
    maxElements = DEFAULT_MAX_ELEMENTS;
    maxDepth = DEFAULT_MAX_DEPTH;
    maxMessages = DEFAULT_MAX_MESSAGES;
    dictionaryWaitTime = DEFAULT_DICTIONARY_WAIT_TIME;
    maxConnections = DEFAULT_MAX_CONNECTIONS;
    maxConnectionsPerHost = DEFAULT_MAX_CONNECTIONS_PER_HOST;
    firstMessageTimeout = DEFAULT_FIRST_MESSAGE_TIMEOUT;
    idleTimeout = DEFAULT_IDLE_TIMEOUT;
    ipv6PrefixLength = DEFAULT_IPV6_PREFIX_LENGTH;
    connectionRate = DEFAULT_CONNECTION_RATE;
    connectionBurst = DEFAULT_CONNECTION_BURST;
    allowBuiltins();
    allowAnnotated();
  }

  /**
   * Creates an enforcing policy.
   */
  public DefaultPreAuthenticationPolicy() {
    this(Mode.ENFORCE);
  }


  /**
   * Adds classes to the allowlist.
   *
   * @param classNames the fully qualified class names (binary or canonical names)
   */
  public void allow(String... classNames) {
    for (String className : classNames) {
      allowedClassNames.add(normalize(className));
    }
  }

  /**
   * Adds classes to the allowlist.
   *
   * @param classes the classes
   */
  public void allow(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      allowedClassNames.add(normalize(clazz.getName()));
    }
  }

  /**
   * Gets the allowlist.
   *
   * @return the normalized class names, unmodifiable
   */
  public Set<String> getAllowedClassNames() {
    return Collections.unmodifiableSet(allowedClassNames);
  }

  /**
   * Sets the maximum size of a message.
   *
   * @param maxMessageSize the maximum size in bytes, 0 if unlimited
   */
  public void setMaxMessageSize(int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  /**
   * Sets the maximum number of elements of an array, collection or map.
   *
   * @param maxElements the maximum number of elements, 0 if unlimited
   */
  public void setMaxElements(int maxElements) {
    this.maxElements = maxElements;
  }

  /**
   * Sets the maximum nesting depth of objects, arrays, collections and maps.
   *
   * @param maxDepth the maximum depth, 0 if unlimited
   */
  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  /**
   * Sets the maximum number of messages a connection may send before the client is authenticated.<br>
   * Each login attempt and each update check is a message.
   *
   * @param maxMessages the maximum number of messages, 0 if unlimited
   */
  public void setMaxMessages(int maxMessages) {
    this.maxMessages = maxMessages;
  }

  /**
   * Sets the time to wait for a missing dictionary entry.
   *
   * @param dictionaryWaitTime the wait time in milliseconds
   */
  public void setDictionaryWaitTime(long dictionaryWaitTime) {
    this.dictionaryWaitTime = dictionaryWaitTime;
  }

  /**
   * Gets the maximum number of unauthenticated connections.
   *
   * @return the maximum number, 0 if unlimited
   */
  public int getMaxConnections() {
    return maxConnections;
  }

  /**
   * Sets the maximum number of unauthenticated connections.
   *
   * @param maxConnections the maximum number, 0 if unlimited
   */
  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  /**
   * Gets the maximum number of unauthenticated connections per host.
   *
   * @return the maximum number, 0 if unlimited
   */
  public int getMaxConnectionsPerHost() {
    return maxConnectionsPerHost;
  }

  /**
   * Sets the maximum number of unauthenticated connections per host.
   *
   * @param maxConnectionsPerHost the maximum number, 0 if unlimited
   */
  public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
    this.maxConnectionsPerHost = maxConnectionsPerHost;
  }

  /**
   * Gets the time until the first message of a connection must be received.
   *
   * @return the timeout in milliseconds, 0 if none
   */
  public long getFirstMessageTimeout() {
    return firstMessageTimeout;
  }

  /**
   * Sets the time until the first message of a connection must be received.<br>
   * Covers the TLS handshake as well.
   *
   * @param firstMessageTimeout the timeout in milliseconds, 0 if none
   */
  public void setFirstMessageTimeout(long firstMessageTimeout) {
    this.firstMessageTimeout = firstMessageTimeout;
  }

  /**
   * Gets the time an unauthenticated connection may stay idle after a call.
   *
   * @return the timeout in milliseconds, 0 if none
   */
  public long getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Sets the time an unauthenticated connection may stay idle after a call.<br>
   * If the next message is not received within this time, the connection is closed.
   * Without it, a client could send a single message and then keep the connection open
   * without ever logging in.
   *
   * @param idleTimeout the timeout in milliseconds, 0 if none
   */
  public void setIdleTimeout(long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  /**
   * Gets the length of the network prefix IPv6 clients are grouped by.
   *
   * @return the prefix length in bits, 0 or 128 to group by the full address
   */
  public int getIpv6PrefixLength() {
    return ipv6PrefixLength;
  }

  /**
   * Sets the length of the network prefix IPv6 clients are grouped by for the connection limits.<br>
   * A client usually owns a whole /64 network and can connect from any address in it.
   *
   * @param ipv6PrefixLength the prefix length in bits, 0 or 128 to group by the full address
   */
  public void setIpv6PrefixLength(int ipv6PrefixLength) {
    this.ipv6PrefixLength = ipv6PrefixLength;
  }

  /**
   * Gets the number of new connections per second a host may open in the long run.
   *
   * @return the rate, 0 if unlimited
   */
  public int getConnectionRate() {
    return connectionRate;
  }

  /**
   * Sets the number of new connections per second a host may open in the long run.<br>
   * A connection above the rate is refused before anything is read from it, not even the TLS handshake.
   * Without the limit, a host could open connections as fast as the network allows, and each new
   * connection would evict an older one: the server would spend its CPU on TLS handshakes, and the
   * connections of the other clients sharing the host's address could not complete their handshake
   * in time. Counts all new connections, including the ones of clients that are already
   * authenticated, since that is not known before the handshake.
   * <p>
   * The rate is enforced as a token bucket, which holds at most {@link #setConnectionBurst(int) connectionBurst}
   * tokens. Each connection takes one token, and the bucket refills at the given rate.
   *
   * @param connectionRate the rate, 0 if unlimited
   */
  public void setConnectionRate(int connectionRate) {
    this.connectionRate = connectionRate;
  }

  /**
   * Gets the number of new connections a host may open at once.
   *
   * @return the burst, 0 if unlimited
   */
  public int getConnectionBurst() {
    return connectionBurst;
  }

  /**
   * Sets the number of new connections a host may open at once.<br>
   * See {@link #setConnectionRate(int)}.
   *
   * @param connectionBurst the burst, 0 if unlimited
   */
  public void setConnectionBurst(int connectionBurst) {
    this.connectionBurst = connectionBurst;
  }

  /**
   * Gets the number of currently admitted unauthenticated connections.
   *
   * @return the number of connections
   */
  public int getAdmittedConnections() {
    synchronized (admissions) {
      return admissions.size();
    }
  }

  @Override
  public Mode getMode() {
    return mode;
  }

  @Override
  public boolean isEnforcing() {
    return mode == Mode.ENFORCE;
  }

  @Override
  public void violation(String message) {
    message = sanitize(message);
    if (mode == Mode.ENFORCE) {
      throw new TripPolicyViolationException("not allowed before authentication: " + message);
    }
    if (audited.size() < MAX_AUDITED && audited.add(message)) {
      LOGGER.warning("pre-authentication policy violation (audit only): {0}", message);
    }
  }

  @Override
  public boolean isClassAllowed(String className) {
    String name = normalize(className);
    return name != null && (name.equals(PRIMITIVE) || allowedClassNames.contains(name));
  }

  @Override
  public boolean isDelegateAllowed(Registry registry, UUID remoteId) {
    return Registry.REGISTRY_ID.equals(remoteId) || registry.lookupName(remoteId) != null;
  }

  @Override
  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  @Override
  public int getMaxElements() {
    return maxElements;
  }

  @Override
  public int getMaxDepth() {
    return maxDepth;
  }

  @Override
  public int getMaxMessages() {
    return maxMessages;
  }

  @Override
  public long getDictionaryWaitTime() {
    return dictionaryWaitTime;
  }

  @Override
  public Admission admit(String host, Runnable closer, BooleanSupplier authenticated) {
    AdmissionImpl admission = new AdmissionImpl(host, Objects.requireNonNull(closer), Objects.requireNonNull(authenticated));
    List<AdmissionImpl> evicted = new ArrayList<>();
    synchronized (admissions) {
      if (!takeConnectionToken(admission.hostKey)) {
        if (mode == Mode.ENFORCE) {
          admission.released = true;
          return null;
        }
        violation("more than " + connectionRate + " connections per second");
      }

      // connections whose client logged in on another connection don't count anymore
      List<AdmissionImpl> authenticatedAdmissions = new ArrayList<>();
      for (AdmissionImpl other : admissions) {
        if (other.authenticated.getAsBoolean()) {
          authenticatedAdmissions.add(other);
        }
      }
      authenticatedAdmissions.forEach(this::remove);

      int perHost = maxConnectionsPerHost;
      if (perHost > 0 && admissionsPerHost.getOrDefault(admission.hostKey, 0) >= perHost) {
        addVictim(admission.hostKey, evicted);
      }
      int max = maxConnections;
      if (max > 0 && admissions.size() - evicted.size() >= max) {
        // The host holding the most connections loses one: with many addresses at hand,
        // an attacker can open connections faster than real users log in, but these users
        // are spread over many hosts.
        addVictim(getBusiestHostKey(evicted), evicted);
      }
      if (mode == Mode.ENFORCE) {
        evicted.forEach(this::remove);
      }
      admissions.add(admission);
      admissionsPerHost.merge(admission.hostKey, 1, Integer::sum);
    }

    for (AdmissionImpl other : evicted) {
      close(other, "too many unauthenticated connections");
    }

    long timeout = firstMessageTimeout;
    if (timeout > 0) {
      admission.startDeadline(timeout, "no message received within " + timeout + " ms");
    }
    return admission;
  }


  /**
   * Gets the current time for the connection rate.<br>
   * Overridden by tests.
   *
   * @return the time in nanoseconds, see {@link System#nanoTime()}
   */
  protected long nanoTime() {
    return System.nanoTime();
  }


  /**
   * Adds the classes every login needs to the allowlist.
   */
  protected void allowBuiltins() {
    allow(Object.class, Class.class, String.class, Void.class,
          Boolean.class, Byte.class, Character.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
          UUID.class, Locale.class, TimeZone.class,
          InitialRemoteCall.class, IdentifiedRemoteCall.class,
          // plain JDK containers: their elements are checked one by one
          ArrayList.class, HashMap.class, LinkedHashMap.class, HashSet.class, LinkedHashSet.class);
    // tentackle-common cannot use @TripPreAuth
    allow("org.tentackle.common.EncryptedProperties");
  }

  /**
   * Adds the classes annotated with {@link TripPreAuth} to the allowlist.
   */
  protected void allowAnnotated() {
    for (Set<String> classNames :
         ServiceFactory.getServiceFinder().createNameMapToAll(PreAuthenticationPolicy.class.getName()).values()) {
      allow(classNames.toArray(new String[0]));
    }
  }

  /**
   * Normalizes a class name.
   * <ul>
   *   <li>arrays are reduced to their element class,</li>
   *   <li>the encoded names of primitives, {@code Object} and {@code Class} are expanded,</li>
   *   <li>nested classes are named by their canonical name.</li>
   * </ul>
   * The analyzer that collects the {@link TripPreAuth} annotations writes canonical names, while
   * classes are sent by their binary names.
   *
   * @param className the class name
   * @return the normalized name, the empty string for a primitive array element, null if malformed
   */
  protected String normalize(String className) {
    if (className == null) {
      return null;
    }
    String name = className.trim();
    if (name.startsWith("[")) {
      int dimensions = 0;
      while (dimensions < name.length() && name.charAt(dimensions) == '[') {
        dimensions++;
      }
      name = name.substring(dimensions);
      if (name.length() == 1) {
        return "ZBCSIJFD".contains(name) ? PRIMITIVE : null;
      }
      if (name.length() < 3 || name.charAt(0) != 'L' || name.charAt(name.length() - 1) != ';') {
        return null;
      }
      name = name.substring(1, name.length() - 1);
    }
    if (name.equals(ReflectionHelper.getSerializableClassName(Object.class))) {
      return Object.class.getName();
    }
    if (name.equals(ReflectionHelper.getSerializableClassName(Class.class))) {
      return Class.class.getName();
    }
    int ndx = name.indexOf('#');
    if (ndx >= 0) {
      name = name.substring(0, ndx);    // primitive: named by its wrapper class
    }
    return name.isEmpty() ? null : name.replace('$', '.');
  }


  /**
   * Sanitizes a violation message before it is thrown or logged.<br>
   * The message may contain names chosen by the client, such as class names: control characters
   * are replaced, so that the client cannot forge log lines, and the length is limited.
   *
   * @param message the message
   * @return the sanitized message
   */
  protected String sanitize(String message) {
    if (message == null) {
      return null;
    }
    boolean truncated = message.length() > MAX_VIOLATION_LENGTH;
    StringBuilder buf = new StringBuilder(truncated ? message.substring(0, MAX_VIOLATION_LENGTH) : message);
    for (int i = 0; i < buf.length(); i++) {
      if (Character.isISOControl(buf.charAt(i))) {
        buf.setCharAt(i, '?');
      }
    }
    if (truncated) {
      buf.append("...");
    }
    return buf.toString();
  }

  /**
   * Determines the key a host's connections are grouped by for the limits.<br>
   * An IPv6 address is reduced to its network prefix (see {@link #setIpv6PrefixLength(int)}),
   * since a single client usually owns a whole /64 network and can pick any address from it.
   *
   * @param host the host, usually an IP address literal, null if unknown
   * @return the key
   */
  protected String hostKey(String host) {
    int prefixLength = ipv6PrefixLength;
    if (host != null && host.indexOf(':') >= 0 && prefixLength > 0 && prefixLength < 128) {
      String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
      try {
        if (InetAddress.ofLiteral(literal) instanceof Inet6Address address) {
          byte[] bytes = address.getAddress();
          for (int bit = prefixLength; bit < 128; bit++) {
            bytes[bit / 8] &= (byte) ~(0x80 >>> (bit % 8));
          }
          return InetAddress.getByAddress(bytes).getHostAddress() + "/" + prefixLength;
        }
      }
      catch (IllegalArgumentException | UnknownHostException e) {
        // not an IPv6 literal: take the host as is
      }
    }
    return host;
  }

  /**
   * Takes a token from the host's bucket for a new connection.<br>
   * Must be invoked while holding the monitor of {@link #admissions}.
   *
   * @param hostKey the host key
   * @return true if the host stays within its rate, false if the connection must be refused
   */
  private boolean takeConnectionToken(String hostKey) {
    int rate = connectionRate;
    int burst = connectionBurst;
    if (rate <= 0 || burst <= 0) {
      return true;
    }
    long now = nanoTime();
    if (rateBuckets.size() >= RATE_PRUNE_THRESHOLD && now - lastRatePrune >= 1_000_000_000L) {
      // forget the hosts whose buckets refilled: they would start with a full bucket anyway
      lastRatePrune = now;
      rateBuckets.values().removeIf(bucket -> bucket.tokens(now, rate, burst) >= burst);
    }
    RateBucket bucket = rateBuckets.get(hostKey);
    if (bucket == null) {
      bucket = new RateBucket(burst, now);
      rateBuckets.put(hostKey, bucket);
    }
    return bucket.take(now, rate, burst);
  }

  /**
   * The token bucket limiting the connection rate of a host.<br>
   * Accessed only while holding the monitor of {@link #admissions}.
   */
  private static final class RateBucket {

    private double tokens;
    private long updated;

    private RateBucket(double tokens, long now) {
      this.tokens = tokens;
      this.updated = now;
    }

    private double tokens(long now, int rate, int burst) {
      long elapsed = now - updated;
      if (elapsed > 0) {
        tokens = Math.min(burst, tokens + elapsed * (rate / 1e9));
        updated = now;
      }
      return tokens;
    }

    private boolean take(long now, int rate, int burst) {
      if (tokens(now, rate, burst) >= 1.0) {
        tokens -= 1.0;
        return true;
      }
      return false;
    }
  }

  /**
   * Finds the host holding the most admitted connections.<br>
   * Must be invoked while holding the monitor of {@link #admissions}.
   *
   * @param evicted the connections already chosen to be evicted
   * @return the host key, null if no admissions
   */
  private String getBusiestHostKey(List<AdmissionImpl> evicted) {
    String busiest = null;
    int maxCount = 0;
    for (Map.Entry<String, Integer> entry : admissionsPerHost.entrySet()) {
      int count = entry.getValue();
      for (AdmissionImpl other : evicted) {
        if (Objects.equals(other.hostKey, entry.getKey())) {
          count--;
        }
      }
      if (count > maxCount) {
        maxCount = count;
        busiest = entry.getKey();
      }
    }
    return busiest;
  }

  /**
   * Chooses the connection of a host to be evicted.<br>
   * The oldest connection without a call in progress, the oldest connection otherwise.
   * Must be invoked while holding the monitor of {@link #admissions}.
   *
   * @param hostKey the host key
   * @param evicted the connections chosen to be evicted, the victim is added
   */
  private void addVictim(String hostKey, List<AdmissionImpl> evicted) {
    AdmissionImpl victim = null;
    for (AdmissionImpl other : admissions) {
      if (Objects.equals(other.hostKey, hostKey) && !evicted.contains(other)) {
        if (!other.busy) {
          victim = other;
          break;
        }
        if (victim == null) {
          victim = other;
        }
      }
    }
    if (victim != null) {
      evicted.add(victim);
    }
  }

  /**
   * Closes an unauthenticated connection, or only logs it in audit mode.
   *
   * @param admission the connection's admission
   * @param reason the reason
   */
  private void close(AdmissionImpl admission, String reason) {
    if (mode == Mode.ENFORCE) {
      if (RejectionLog.isLoggable()) {
        LOGGER.warning("closing {0}: {1}", admission, reason);
      }
      try {
        admission.closer.run();
      }
      catch (RuntimeException ex) {
        LOGGER.warning("closing " + admission + " failed", ex);
      }
    }
    else {
      violation(reason);
    }
  }

  /**
   * Removes an admission.<br>
   * Must be invoked while holding the monitor of {@link #admissions}.
   *
   * @param admission the admission
   */
  private void remove(AdmissionImpl admission) {
    if (!admission.released) {
      admission.released = true;
      admissions.remove(admission);
      admissionsPerHost.computeIfPresent(admission.hostKey, (_, count) -> count > 1 ? count - 1 : null);
    }
  }

}
