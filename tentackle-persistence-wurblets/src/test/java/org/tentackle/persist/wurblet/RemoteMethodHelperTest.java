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

package org.tentackle.persist.wurblet;

import org.testng.annotations.Test;

import org.tentackle.buildsupport.RemoteMethodInfo;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link RemoteMethodHelper}, which turns the method info collected by the annotation
 * processor into the parameter lists the {@code RemoteMethod} wurblet writes into the TRIP
 * delegate and its implementation.
 * <p>
 * The info objects are built from the {@code .remote} info-file format, i.e. exactly what the
 * {@code AnalyzeProcessor} writes and the wurblets read back at build time.
 */
@SuppressWarnings("missing-explicit-ctor")
public class RemoteMethodHelperTest {

  private static final String PDO_CLASS = "Invoice";

  /**
   * Builds a method info from the {@code .remote} info-file format.
   *
   * @param lines the info-file lines
   * @return the info
   * @throws IOException if the format is broken
   */
  private static RemoteMethodInfo info(String... lines) throws IOException {
    return RemoteMethodInfo.readInfo(new LineNumberReader(new StringReader(String.join("\n", lines))));
  }

  /**
   * Builds the info of a method with the given parameters.
   * <p>
   * A parameter line is {@code "<name> <type> <isSession> <isContext> <isDbObject> <isPdo>"}.
   *
   * @param returns the {@code returns:} line without its keyword
   * @param parameters the parameter descriptors
   * @return the info
   * @throws IOException if the format is broken
   */
  private static RemoteMethodInfo method(String returns, String... parameters) throws IOException {
    String[] lines = new String[5 + parameters.length];
    lines[0] = "method: findIt";
    lines[1] = "class: org.example.InvoicePersistenceImpl";
    lines[2] = "returns: " + returns;
    lines[3] = "modifiers: PUBLIC";
    lines[4] = "varargs: false";
    for (int i = 0; i < parameters.length; i++) {
      lines[5 + i] = "arg[" + i + "]: " + parameters[i];
    }
    return info(lines);
  }

  /** A plain, non-persistent parameter. */
  private static String plain(String name, String type) {
    return name + ' ' + type + " false false false false";
  }

  /** The session parameter every persistence method carries. */
  private static final String SESSION = "session org.tentackle.session.Session true false false false";

  /** A PDO parameter. */
  private static String pdo(String name, String type) {
    return name + ' ' + type + " false false true true";
  }

  /** A return descriptor for a plain value. */
  private static final String RETURNS_VOID = "void . false false false false false false";
  private static final String RETURNS_LONG = "long . false false false false false false";


  @Test
  public void readsTheMethodSignatureFromTheInfoFile() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(
      method("java.util.List <Invoice> true true true true false false", SESSION), null, false);

    assertEquals(helper.getMethodName(), "findIt");
    assertEquals(helper.getReturnType(), "List");        // the package is stripped for readability
    assertEquals(helper.getGenericReturnType(), "<Invoice>");
    assertFalse(helper.isReturningVoid());
    assertTrue(helper.isReturningCollection());
  }

  /**
   * An empty generic type is written as {@code "."} in the info file and must not leak into the
   * generated code as {@code "<>"}.
   */
  @Test
  public void reportsAnAbsentGenericReturnTypeAsEmpty() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(method(RETURNS_LONG, SESSION), null, false);
    assertEquals(helper.getGenericReturnType(), "");
    assertEquals(helper.getReturnType(), "long");
  }

  @Test
  public void classifiesTheReturnValue() throws Exception {
    assertTrue(new RemoteMethodHelper(method(RETURNS_VOID), null, false).isReturningVoid());

    RemoteMethodHelper object = new RemoteMethodHelper(
      method("Invoice . true false true false false false", SESSION), PDO_CLASS, false);
    assertTrue(object.isReturningObject());
    assertFalse(object.isReturningCollection());
    assertFalse(object.isReturningCursor());

    RemoteMethodHelper cursor = new RemoteMethodHelper(
      method("org.tentackle.pdo.PdoCursor <Invoice> false false false true true true", SESSION), PDO_CLASS, false);
    assertTrue(cursor.isReturningCursor());
  }

  @Test
  public void recognizesAStaticMethod() throws Exception {
    RemoteMethodInfo staticInfo = info("method: findIt",
                                       "class: org.example.InvoicePersistenceImpl",
                                       "returns: " + RETURNS_LONG,
                                       "modifiers: PUBLIC STATIC",
                                       "varargs: false",
                                       "arg[0]: " + SESSION);
    assertTrue(new RemoteMethodHelper(staticInfo, null, false).isStaticMethod());
    assertFalse(new RemoteMethodHelper(method(RETURNS_LONG, SESSION), null, false).isStaticMethod());
  }

  @Test
  public void reportsTheFirstParameter() throws Exception {
    RemoteMethodHelper withSession = new RemoteMethodHelper(
      method(RETURNS_LONG, SESSION, plain("name", "java.lang.String")), null, false);
    assertEquals(withSession.getFirstName(), "session");
    assertTrue(withSession.isFirstInstanceOfDb());
    assertFalse(withSession.isFirstInstanceOfDomainContext());
    assertEquals(withSession.getParamCount(), 2);

    RemoteMethodHelper withContext = new RemoteMethodHelper(
      method(RETURNS_LONG, "context org.tentackle.pdo.DomainContext false true false false"), null, false);
    assertTrue(withContext.isFirstInstanceOfDomainContext());
    assertFalse(withContext.isFirstInstanceOfDb());
  }

  @Test
  public void copesWithAMethodWithoutParameters() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(method(RETURNS_LONG), null, false);
    assertEquals(helper.getParamCount(), 0);
    assertNull(helper.getFirstName());
    assertEquals(helper.getInvocationParameterString(), "");
    assertEquals(helper.getDeclarationParameterString(), "");
    assertEquals(helper.getRemoteInvocationParameterString(), "");
    assertTrue(helper.getUpdateDbInParametersStatements().isEmpty());
  }


  /**
   * The session is implicit on the server side - the delegate already runs on it - so it is
   * dropped from the local invocation and from the declaration.
   */
  @Test
  public void dropsTheSessionFromTheInvocationAndDeclaration() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(
      method(RETURNS_LONG, SESSION, plain("name", "java.lang.String"), plain("max", "int")), null, false);

    assertEquals(helper.getInvocationParameterString(), "name, max");
    assertEquals(helper.getDeclarationParameterString(), "String name, int max");
  }

  /**
   * The remote call, on the other hand, has to carry the session across the wire.
   */
  @Test
  public void keepsTheSessionInTheRemoteInvocation() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(
      method(RETURNS_LONG, SESSION, plain("name", "java.lang.String")), null, false);
    assertEquals(helper.getRemoteInvocationParameterString(), "session, name");
  }

  /**
   * {@code addThis} turns an instance method into a delegate method: the receiver becomes an
   * explicit trailing parameter, declared as the PDO interface and named {@code obj}.
   */
  @Test
  public void addsTheReceiverAsAnExplicitParameter() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(
      method(RETURNS_LONG, SESSION, plain("name", "java.lang.String")), PDO_CLASS, true);

    assertEquals(helper.getParamCount(), 3);
    assertEquals(helper.getDeclarationParameterString(), "String name, Invoice obj");
    assertEquals(helper.getInvocationParameterString(), "name, me()");    // the PDO's own proxy
    assertEquals(helper.getRemoteInvocationParameterString(), "session, name");   // never sent as "this"
    assertTrue(helper.isPdo());
  }

  /**
   * For a plain persistence object (no PDO) the receiver keeps its class name and is passed on
   * verbatim instead of through {@code me()}.
   */
  @Test
  public void passesTheReceiverVerbatimWithoutAPdo() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(method(RETURNS_LONG, SESSION), null, true);

    assertFalse(helper.isPdo());
    assertEquals(helper.getDeclarationParameterString(), "InvoicePersistenceImpl obj");
    assertEquals(helper.getInvocationParameterString(), "this");
    assertEquals(helper.getRemoteInvocationParameterString(), "session");
  }

  @Test
  public void declaresAVarArgsParameter() throws Exception {
    RemoteMethodInfo varargs = info("method: findIt",
                                    "class: org.example.InvoicePersistenceImpl",
                                    "returns: " + RETURNS_LONG,
                                    "modifiers: PUBLIC",
                                    "varargs: true",
                                    "arg[0]: " + SESSION,
                                    "arg[1]: names java.lang.String[] false false false false");
    RemoteMethodHelper helper = new RemoteMethodHelper(varargs, null, false);
    assertEquals(helper.getDeclarationParameterString(), "String... names");
    assertEquals(helper.getInvocationParameterString(), "names");
  }

  /**
   * Persistent parameters arrive from the client detached from any session, so the delegate has
   * to bind them to its own before invoking the method.
   */
  @Test
  public void bindsPersistentParametersToTheServersSession() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(
      method(RETURNS_LONG, SESSION, plain("name", "java.lang.String"),
             pdo("invoice", "org.example.Invoice"), pdo("customer", "org.example.Customer")),
      PDO_CLASS, false);

    assertEquals(helper.getUpdateDbInParametersStatements(),
                 List.of("getSession().applyTo(invoice)", "getSession().applyTo(customer)"));
  }

  @Test
  public void leavesPlainParametersUntouched() throws Exception {
    RemoteMethodHelper helper = new RemoteMethodHelper(
      method(RETURNS_LONG, SESSION, plain("name", "java.lang.String")), null, false);
    assertTrue(helper.getUpdateDbInParametersStatements().isEmpty());
  }

}
