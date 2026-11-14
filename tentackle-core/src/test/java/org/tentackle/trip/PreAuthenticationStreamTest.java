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

package org.tentackle.trip;

import org.testng.annotations.Test;

import org.tentackle.log.Logger;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.DefaultPreAuthenticationPolicy;
import org.tentackle.trip.impl.EchoTransceiver;
import org.tentackle.trip.impl.TestClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * What a server reads from a client that is not authenticated yet.
 *
 * @see PreAuthenticationPolicy
 */
@SuppressWarnings("missing-explicit-ctor")
public class PreAuthenticationStreamTest {

  /** Set by the static initializer of {@link Gadget}. */
  static volatile boolean gadgetInitialized;

  /** Set by the static initializer of {@link ComponentGadget}. */
  static volatile boolean componentGadgetInitialized;

  /** Set by the static initializer of {@link ArrayGadget}. */
  static volatile boolean arrayGadgetInitialized;

  /** Set by the static initializer of {@link AuditedGadget}. */
  static volatile boolean auditedGadgetInitialized;

  /**
   * A class whose initialization has a side effect.<br>
   * Must never be referenced, except by name.
   */
  public static class Gadget {
    static {
      gadgetInitialized = true;
    }
    public String value;
  }

  /**
   * Same as {@link Gadget}, but sent as the type of a component.
   */
  public static class ComponentGadget {
    static {
      componentGadgetInitialized = true;
    }
    public String value;
  }

  /**
   * Same as {@link Gadget}, but sent as the component name of an allowed array class.
   */
  public static class ArrayGadget {
    static {
      arrayGadgetInitialized = true;
    }
    public String value;
  }

  /**
   * Same as {@link Gadget}, but received by a policy that only audits.
   */
  public static class AuditedGadget {
    static {
      auditedGadgetInitialized = true;
    }
    public String value;
  }

  /**
   * A harmless class to be put on the allowlist.
   */
  public static class Harmless {
    public String name;
    public long number;
  }


  /**
   * Creates a transceiver that writes like a client and reads like a server.
   *
   * @return the transceiver
   */
  private EchoTransceiver createClientToServer() {
    return new EchoTransceiver(TripFactory.getInstance().createDictionary(UUID.randomUUID(), false),
                               TripFactory.getInstance().createDictionary(UUID.randomUUID(), true),
                               false, null);
  }

  private DefaultPreAuthenticationPolicy createPolicy(PreAuthenticationPolicy.Mode mode) {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy(mode);
    policy.allow(Harmless.class);
    return policy;
  }

  /**
   * Writes a dictionary entry exactly like {@code DictionaryEntryTripType} does, but with forged names.
   */
  private void writeEntry(TripStream stream, int ordinal, String className, boolean isProxy,
                          String[] componentNames, String[] componentTypes) {
    TripFactory factory = TripFactory.getInstance();
    stream.getSerializer().serializeOpCode(DefaultOpCode.DICTIONARY);
    stream.writeComponentOrdinal(ordinal);
    factory.getType(String.class).write(className, stream);
    factory.getType(Boolean.TYPE).write(isProxy, stream);
    factory.getType(String[].class).write(componentNames, stream);
    factory.getType(String[].class).write(componentTypes, stream);
  }

  /**
   * Sends an object and reads it back with the given policy.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private <T> T echo(T object, PreAuthenticationPolicy policy) {
    EchoTransceiver transceiver = createClientToServer();
    TripStream out = transceiver.getStream();
    TripType<T> type = out.getType((Class<T>) object.getClass());
    type.write(object, out);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(policy);
    OpCode opCode = in.getDeserializer().deserializeOpCode();
    if (type instanceof PredefinedArray arrayType) {
      return (T) arrayType.readWithType(in, opCode);
    }
    return type.read(in, opCode);
  }


  @Test
  void testForgedClassIsNeverInitialized() {
    EchoTransceiver transceiver = createClientToServer();
    writeEntry(transceiver.getStream(), -1, "org.tentackle.trip.PreAuthenticationStreamTest$Gadget", false,
               new String[] { "value" }, new String[] { String.class.getName() });
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));

    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class, in::receiveDictionary);
    assertTrue(ex.getMessage().contains("Gadget"), ex.getMessage());
    assertFalse(gadgetInitialized, "static initializer of a refused class was run");
  }

  @Test
  void testForgedComponentTypeIsRefused() {
    EchoTransceiver transceiver = createClientToServer();
    writeEntry(transceiver.getStream(), -1, Harmless.class.getName(), false,
               new String[] { "name", "number" },
               new String[] { "org.tentackle.trip.PreAuthenticationStreamTest$ComponentGadget",
                              Long.class.getName() + "#TYPE" });
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));

    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class, in::receiveDictionary);
    assertTrue(ex.getMessage().contains("ComponentGadget"), ex.getMessage());
    assertFalse(componentGadgetInitialized, "static initializer of a refused component type was run");
  }

  @Test
  void testForgedArrayComponentIsNeverInitialized() {
    // the component of an array entry names the element class, which is loaded to create the array
    EchoTransceiver transceiver = createClientToServer();
    TripStream out = transceiver.getStream();
    out.getSerializer().serializeOpCode(DefaultOpCode.ARRAY);
    writeEntry(out, -1, Harmless[].class.getName(), false,
               new String[] { "org.tentackle.trip.PreAuthenticationStreamTest$ArrayGadget" },
               new String[] { Harmless.class.getName() });
    TripFactory.getInstance().getType(Integer.class).write(1, out);   // length
    out.getSerializer().serializeOpCode(DefaultOpCode.END);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));

    TripType<Harmless[]> type = in.getType(Harmless[].class);
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class,
                                                   () -> type.read(in, in.getDeserializer().deserializeOpCode()));
    assertTrue(ex.getMessage().contains("ArrayGadget"), ex.getMessage());
    assertFalse(arrayGadgetInitialized, "static initializer of a refused array component was run");
  }

  @Test
  void testUnknownComponentNamesAreRefused() {
    // otherwise, an entry can carry thousands of names the server keeps as long as the connection lives
    String[] names = new String[1000];
    String[] types = new String[names.length];
    for (int i = 0; i < names.length; i++) {
      names[i] = "c" + i;
      types[i] = String.class.getName();
    }
    EchoTransceiver transceiver = createClientToServer();
    writeEntry(transceiver.getStream(), -1, Harmless.class.getName(), false, names, types);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));

    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class, in::receiveDictionary);
    assertTrue(ex.getMessage().contains("component c0"), ex.getMessage());
  }

  @Test
  void testViolationMessageIsSanitized() {
    DefaultPreAuthenticationPolicy policy = createPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class,
                                                   () -> policy.violation("class Foo\n2026-11-12 SEVERE forged" + "x".repeat(1000)));
    assertFalse(ex.getMessage().contains("\n"), ex.getMessage());
    assertTrue(ex.getMessage().length() < 300, ex.getMessage());
  }

  @Test
  void testProxyIsRefused() {
    EchoTransceiver transceiver = createClientToServer();
    writeEntry(transceiver.getStream(), -1, "jdk.proxy1.$Proxy42", true,
               new String[] { Runnable.class.getName() }, new String[] { Class.class.getName() });
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));

    expectThrows(TripPolicyViolationException.class, in::receiveDictionary);
  }

  @Test
  void testServerEntryReferencedByClient() {
    // entries the server created for classes it sent to the client may be referenced by the client
    Dictionary serverDictionary = TripFactory.getInstance().createDictionary(UUID.randomUUID(), true);
    DictionaryEntry notAllowed = serverDictionary.createEntry(TestClass.class);
    serverDictionary.addEntry(notAllowed);
    DictionaryEntry allowed = serverDictionary.createEntry(Harmless.class);
    serverDictionary.addEntry(allowed);

    EchoTransceiver transceiver = new EchoTransceiver(TripFactory.getInstance().createDictionary(UUID.randomUUID(), false),
                                                      serverDictionary, false, null);
    transceiver.getStream().writeDictionaryOrdinal(allowed.getOrdinal());
    transceiver.getStream().writeDictionaryOrdinal(notAllowed.getOrdinal());
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));

    assertEquals(in.receiveDictionary(), allowed);
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class, in::receiveDictionary);
    assertTrue(ex.getMessage().contains(TestClass.class.getName()), ex.getMessage());
  }

  @Test
  void testPermanentEntryRefusedByServer() {
    // no policy at all: a client must never send an entry with a permanent ordinal
    EchoTransceiver transceiver = createClientToServer();
    writeEntry(transceiver.getStream(), 7, Harmless.class.getName(), false,
               new String[] { "name", "number" }, new String[] { String.class.getName(), Long.class.getName() + "#TYPE" });
    transceiver.echo();
    TripStream in = transceiver.getStream();

    TripRuntimeException ex = expectThrows(TripRuntimeException.class, in::receiveDictionary);
    assertTrue(ex.getMessage().contains("permanent"), ex.getMessage());
  }

  @Test
  void testAllowedClass() {
    Harmless harmless = new Harmless();
    harmless.name = "harmless";
    harmless.number = 4711;
    Harmless echo = echo(harmless, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));
    assertEquals(echo.name, harmless.name);
    assertEquals(echo.number, harmless.number);

    // containers of allowed elements, arrays of primitives and annotated types
    List<Object> list = new ArrayList<>(List.of("a", 1L, harmless, Logger.Level.INFO));
    List<Object> listEcho = echo(list, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));
    assertEquals(listEcho.size(), 4);
    assertEquals(listEcho.get(3), Logger.Level.INFO);
    assertEquals(echo(new long[] { 1, 2, 3 }, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE)).length, 3);
  }

  @Test
  void testClassNotAllowed() {
    TestClass testObject = new TestClass();
    testObject.name = "not on the allowlist";
    expectThrows(TripPolicyViolationException.class, () -> echo(testObject, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE)));
    expectThrows(TripPolicyViolationException.class,
                 () -> echo(new TestClass[] { testObject }, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE)));

    // no restrictions once authenticated...
    assertEquals(echo(testObject, null).name, testObject.name);
    // ...and only logged when auditing
    assertEquals(echo(testObject, createPolicy(PreAuthenticationPolicy.Mode.AUDIT)).name, testObject.name);
  }

  @Test
  void testAuditedClassIsLoaded() {
    // the counterpart of testForgedClassIsNeverInitialized: proves that the probe works
    EchoTransceiver transceiver = createClientToServer();
    writeEntry(transceiver.getStream(), -1, "org.tentackle.trip.PreAuthenticationStreamTest$AuditedGadget", false,
               new String[] { "value" }, new String[] { String.class.getName() });
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(createPolicy(PreAuthenticationPolicy.Mode.AUDIT));

    assertNotNull(in.receiveDictionary());
    assertTrue(auditedGadgetInitialized);
  }

  @Test
  void testTooManyElements() {
    DefaultPreAuthenticationPolicy policy = createPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setMaxElements(100);
    // sparse: a few bytes on the wire, but 8 MB when allocated
    expectThrows(TripPolicyViolationException.class, () -> echo(new long[1_000_000], policy));
    List<String> list = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      list.add("x");
    }
    expectThrows(TripPolicyViolationException.class, () -> echo(list, policy));
    assertEquals(echo(list.subList(0, 100).toArray(new String[0]), policy).length, 100);
  }

  @Test
  void testMessageTooLarge() {
    DefaultPreAuthenticationPolicy policy = createPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setMaxMessageSize(1000);
    String small = "x".repeat(500);
    assertEquals(echo(small, policy), small);
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class, () -> echo("x".repeat(20_000), policy));
    assertTrue(ex.getMessage().contains("maximum size"), ex.getMessage());
  }

  @Test
  void testUnknownOrdinalFailsFast() {
    DefaultPreAuthenticationPolicy policy = createPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setDictionaryWaitTime(200);
    EchoTransceiver transceiver = createClientToServer();
    transceiver.getStream().writeDictionaryOrdinal(-42);    // never sent
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setReadPolicy(policy);

    long start = System.currentTimeMillis();
    expectThrows(TripRuntimeException.class, in::receiveDictionary);
    long elapsed = System.currentTimeMillis() - start;
    assertTrue(elapsed < 3000, "waited " + elapsed + " ms");
  }

  /**
   * Creates a hash chain: two sets per level, each referencing both sets of the next level.<br>
   * A few bytes per level, but computing the hash code of the root takes 2^depth steps.
   */
  private Set<Object> createHashChain(int depth) {
    Set<Object> root = new HashSet<>();
    Set<Object> s1 = root;
    Set<Object> s2 = new HashSet<>();
    for (int i = 0; i < depth; i++) {
      Set<Object> t1 = new HashSet<>();
      Set<Object> t2 = new HashSet<>();
      t1.add("x");    // the two sets of a level must differ
      s1.add(t1);
      s1.add(t2);
      s2.add(t1);
      s2.add(t2);
      s1 = t1;
      s2 = t2;
    }
    return root;
  }

  @Test(timeOut = 10000)
  void testHashChainIsRefused() {
    Set<Object> chain = createHashChain(30);    // below the maximum depth: minutes to deserialize without the policy
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class,
                                                   () -> echo(chain, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE)));
    assertTrue(ex.getMessage().contains("back-reference"), ex.getMessage());
    // no restrictions once authenticated
    assertEquals(echo(createHashChain(4), null).size(), 2);
  }

  @Test
  void testSharedValuesAllowed() {
    // immutable values may be shared, containers may not
    String shared = "shared";
    List<Object> list = new ArrayList<>(List.of(shared, shared, Logger.Level.INFO, Logger.Level.INFO));
    List<Object> echo = echo(list, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE));
    assertEquals(echo, list);

    List<Object> inner = new ArrayList<>(List.of("a"));
    List<Object> outer = new ArrayList<>(List.of(inner, inner));
    expectThrows(TripPolicyViolationException.class, () -> echo(outer, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE)));

    List<Object> self = new ArrayList<>();
    self.add(self);
    expectThrows(TripPolicyViolationException.class, () -> echo(self, createPolicy(PreAuthenticationPolicy.Mode.ENFORCE)));
  }

  @Test
  void testTooDeep() {
    DefaultPreAuthenticationPolicy policy = createPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setMaxDepth(8);
    List<Object> root = new ArrayList<>();
    List<Object> list = root;
    for (int i = 1; i < 8; i++) {
      List<Object> next = new ArrayList<>();
      list.add(next);
      list = next;
    }
    list.add("leaf");
    assertEquals(echo(root, policy), root);   // 8 levels: just fits

    list.set(0, new ArrayList<>(List.of("leaf")));
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class, () -> echo(root, policy));
    assertTrue(ex.getMessage().contains("depth"), ex.getMessage());

    // objects count as well
    Harmless harmless = new Harmless();
    harmless.name = "deep";
    list.set(0, harmless);
    expectThrows(TripPolicyViolationException.class, () -> echo(root, policy));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testDeepNestingDoesNotOverflow() throws InterruptedException {
    // without a limit, a few hundred KB of nested lists overflow the stack of the reader
    List<Object> root = new ArrayList<>();
    List<Object> list = root;
    for (int i = 0; i < 100_000; i++) {
      List<Object> next = new ArrayList<>();
      list.add(next);
      list = next;
    }
    EchoTransceiver transceiver = createClientToServer();
    TripType<List<Object>> type = transceiver.getStream().getType((Class) ArrayList.class);
    // the sender recurses as well: give it a stack large enough
    Thread writer = new Thread(null, () -> type.write(root, transceiver.getStream()), "deep writer", 1L << 30);
    writer.start();
    writer.join();
    transceiver.echo();

    DefaultPreAuthenticationPolicy policy = createPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setMaxMessageSize(0);
    TripStream in = transceiver.getStream();
    in.setReadPolicy(policy);
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class,
                                                   () -> type.read(in, in.getDeserializer().deserializeOpCode()));
    assertTrue(ex.getMessage().contains("depth"), ex.getMessage());
  }

  @Test
  void testClassNames() {
    DefaultPreAuthenticationPolicy policy = createPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    assertTrue(policy.isClassAllowed(String.class.getName()));
    assertTrue(policy.isClassAllowed("@O"));
    assertTrue(policy.isClassAllowed("@C"));
    assertTrue(policy.isClassAllowed("java.lang.Long#TYPE"));
    assertTrue(policy.isClassAllowed("[J"));
    assertTrue(policy.isClassAllowed("[[I"));
    assertTrue(policy.isClassAllowed("[Ljava.lang.String;"));
    assertTrue(policy.isClassAllowed("[Ljava.lang.Object;"));
    assertTrue(policy.isClassAllowed(Harmless.class.getName()));
    // annotated with @TripPreAuth, nested: collected by its canonical name
    assertTrue(policy.isClassAllowed(Logger.Level.class.getName()));
    assertTrue(policy.isClassAllowed("org.tentackle.misc.Secret"));

    assertFalse(policy.isClassAllowed("java.lang.Runtime"));
    assertFalse(policy.isClassAllowed("[Ljava.lang.Runtime;"));
    assertFalse(policy.isClassAllowed("[X"));
    assertFalse(policy.isClassAllowed("[L;"));
    assertFalse(policy.isClassAllowed(""));
    assertFalse(policy.isClassAllowed(null));
  }

  @Test
  void testBusiestHostIsEvicted() {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(0);
    policy.setIdleTimeout(0);
    policy.setMaxConnectionsPerHost(0);
    policy.setMaxConnections(4);

    AtomicInteger closedUser = new AtomicInteger();
    AtomicInteger closedAttacker = new AtomicInteger();
    policy.admit("10.0.0.1", closedUser::incrementAndGet, () -> false);     // the oldest
    for (int i = 0; i < 10; i++) {
      policy.admit("10.6.6.6", closedAttacker::incrementAndGet, () -> false);
    }
    assertEquals(closedUser.get(), 0, "user evicted instead of the attacker");
    assertEquals(closedAttacker.get(), 7);
    assertEquals(policy.getAdmittedConnections(), 4);
  }

  @Test
  void testIpv6NetworkCountsAsOneHost() {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(0);
    policy.setIdleTimeout(0);
    policy.setMaxConnectionsPerHost(2);

    AtomicInteger closed = new AtomicInteger();
    policy.admit("[2001:db8:0:1::1]", closed::incrementAndGet, () -> false);
    policy.admit("[2001:db8:0:1::2]", closed::incrementAndGet, () -> false);
    policy.admit("[2001:db8:0:2::1]", closed::incrementAndGet, () -> false);   // another network
    assertEquals(closed.get(), 0);
    policy.admit("[2001:db8:0:1:ffff::3]", closed::incrementAndGet, () -> false);   // same /64
    assertEquals(closed.get(), 1);

    policy.setIpv6PrefixLength(128);    // full address
    policy.admit("[2001:db8:0:1::4]", closed::incrementAndGet, () -> false);
    policy.admit("[2001:db8:0:1::5]", closed::incrementAndGet, () -> false);
    assertEquals(closed.get(), 1);
  }

  @Test
  void testBusyConnectionIsEvictedLast() {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(0);
    policy.setIdleTimeout(0);
    policy.setMaxConnectionsPerHost(2);

    AtomicInteger closedBusy = new AtomicInteger();
    AtomicInteger closedIdle = new AtomicInteger();
    PreAuthenticationPolicy.Admission busy = policy.admit("a", closedBusy::incrementAndGet, () -> false);
    busy.messageReceived();   // a login in progress
    policy.admit("a", closedIdle::incrementAndGet, () -> false);
    policy.admit("a", () -> { }, () -> false);
    assertEquals(closedBusy.get(), 0);
    assertEquals(closedIdle.get(), 1);

    busy.callCompleted();
    policy.admit("a", () -> { }, () -> false);
    assertEquals(closedBusy.get(), 1);
  }

  @Test
  void testAdmissions() throws InterruptedException {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(0);
    policy.setIdleTimeout(0);
    policy.setMaxConnectionsPerHost(2);
    policy.setMaxConnections(3);

    AtomicInteger closedA1 = new AtomicInteger();
    AtomicInteger closedA2 = new AtomicInteger();
    AtomicInteger closedA3 = new AtomicInteger();
    AtomicInteger closedB1 = new AtomicInteger();
    AtomicInteger closedB2 = new AtomicInteger();
    boolean[] authenticated = new boolean[1];

    PreAuthenticationPolicy.Admission a1 = policy.admit("a", closedA1::incrementAndGet, () -> false);
    PreAuthenticationPolicy.Admission a2 = policy.admit("a", closedA2::incrementAndGet, () -> false);
    assertEquals(policy.getAdmittedConnections(), 2);
    // third from host a: evicts the oldest of host a
    policy.admit("a", closedA3::incrementAndGet, () -> false);
    assertEquals(closedA1.get(), 1);
    assertEquals(closedA2.get(), 0);
    assertEquals(policy.getAdmittedConnections(), 2);
    a1.release();   // the evicted connection terminates
    assertEquals(policy.getAdmittedConnections(), 2);

    // host b: the global limit evicts the oldest of all
    policy.admit("b", closedB1::incrementAndGet, () -> authenticated[0]);
    assertEquals(policy.getAdmittedConnections(), 3);
    policy.admit("b", closedB2::incrementAndGet, () -> false);
    assertEquals(closedA2.get(), 1);
    assertEquals(policy.getAdmittedConnections(), 3);
    a2.release();

    // an authenticated connection doesn't count anymore
    authenticated[0] = true;
    policy.admit("c", () -> { }, () -> false);
    assertEquals(closedB1.get(), 0);
    assertEquals(closedA3.get(), 0);
    assertEquals(policy.getAdmittedConnections(), 3);   // a3, b2, c

    // first message deadline
    policy.setFirstMessageTimeout(100);
    AtomicInteger closedLate = new AtomicInteger();
    AtomicInteger closedInTime = new AtomicInteger();
    policy.setMaxConnections(0);
    policy.admit("d", closedLate::incrementAndGet, () -> false);
    policy.admit("e", closedInTime::incrementAndGet, () -> false).messageReceived();
    Thread.sleep(500);
    assertEquals(closedLate.get(), 1);
    assertEquals(closedInTime.get(), 0);
  }

  @Test
  void testIdleDeadline() throws InterruptedException {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(0);
    policy.setIdleTimeout(100);

    // idle after a call
    AtomicInteger closedIdle = new AtomicInteger();
    PreAuthenticationPolicy.Admission idle = policy.admit("a", closedIdle::incrementAndGet, () -> false);
    idle.messageReceived();
    idle.callCompleted();

    // call still in progress, e.g. a login
    AtomicInteger closedBusy = new AtomicInteger();
    policy.admit("b", closedBusy::incrementAndGet, () -> false).messageReceived();

    // next message received in time
    AtomicInteger closedActive = new AtomicInteger();
    PreAuthenticationPolicy.Admission active = policy.admit("c", closedActive::incrementAndGet, () -> false);
    active.messageReceived();
    active.callCompleted();
    active.messageReceived();

    // logged in meanwhile
    AtomicInteger closedAuthenticated = new AtomicInteger();
    PreAuthenticationPolicy.Admission authenticated = policy.admit("d", closedAuthenticated::incrementAndGet, () -> true);
    authenticated.messageReceived();
    authenticated.callCompleted();

    // released
    AtomicInteger closedReleased = new AtomicInteger();
    PreAuthenticationPolicy.Admission released = policy.admit("e", closedReleased::incrementAndGet, () -> false);
    released.messageReceived();
    released.callCompleted();
    released.release();

    Thread.sleep(500);
    assertEquals(closedIdle.get(), 1);
    assertEquals(closedBusy.get(), 0);
    assertEquals(closedActive.get(), 0);
    assertEquals(closedAuthenticated.get(), 0);
    assertEquals(closedReleased.get(), 0);
  }

}
