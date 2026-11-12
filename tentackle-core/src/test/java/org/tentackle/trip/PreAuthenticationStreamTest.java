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
import java.util.List;
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
  void testAdmissions() throws InterruptedException {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(0);
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

}
