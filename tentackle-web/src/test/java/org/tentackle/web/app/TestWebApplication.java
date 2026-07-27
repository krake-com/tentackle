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

package org.tentackle.web.app;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.Db;
import org.tentackle.misc.Identifiable;
import org.tentackle.pdo.DomainContext;
import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.SessionPool;

/**
 * A {@link WebApplication} whose backend login is stubbed out.
 * <p>
 * The real startup sequence of an {@code AbstractServerApplication} is
 * {@code register() - initialize() - login() - configure() - finishStartup()}. Everything but the
 * registration talks to a backend, reads preferences and starts the modification tracker, none of
 * which says anything about the web bridge. The steps are therefore overridden away, while
 * {@code startup()}, {@code finishStartup()} and {@code cleanup()} - the three methods
 * {@code WebApplication} actually contributes - run for real, so the key cache is created, started
 * and terminated by production code.
 * <p>
 * The session and the two pools are injected instead of being built by a login, which is what
 * lets a single test switch between the two deployment shapes: DB-connected (a {@link SessionPool})
 * and proxy (a {@link MultiUserSessionPool}).
 */
public class TestWebApplication extends WebApplication<String> {

  private final Db session;
  private final SessionPool sessionPool;
  private final MultiUserSessionPool remoteSessionPool;
  private final EncryptedProperties properties;

  /**
   * Creates the application.
   *
   * @param session the session {@code getSession()} reports
   * @param sessionPool the local session pool, null for a proxy node
   * @param remoteSessionPool the remote session pool, null for a DB-connected node
   */
  public TestWebApplication(Db session, SessionPool sessionPool, MultiUserSessionPool remoteSessionPool) {
    super("web-test", "1.0.0");
    this.session = session;
    this.sessionPool = sessionPool;
    this.remoteSessionPool = remoteSessionPool;
    properties = new EncryptedProperties();
    // the modification tracker is a JVM-wide singleton with a polling thread - keep it out of the tests
    properties.setProperty(DISABLE_MODIFICATION_TRACKER, "");
  }

  @Override
  public Db getSession() {
    return session;
  }

  @Override
  public <U extends Identifiable> U getUser(DomainContext context, long userId) {
    return null;    // the tests never resolve a user PDO
  }

  @Override
  public SessionPool getSessionPool() {
    return sessionPool;
  }

  @Override
  public MultiUserSessionPool getRemoteSessionPool() {
    return remoteSessionPool;
  }

  @Override
  public SessionPool createSessionPool() {
    return sessionPool;
  }

  @Override
  public MultiUserSessionPool createRemoteSessionPool() {
    return remoteSessionPool;
  }

  @Override
  protected ConnectionManager createConnectionManager() {
    return null;
  }

  @Override
  protected EncryptedProperties getProperties() {
    return properties;
  }

  @Override
  protected void initialize() {
    // no scripting, no module hook scan
  }

  @Override
  protected void login() {
    // the session is injected
  }

  @Override
  protected void configure() {
    // no preferences, no security manager, no modification tracker
  }

}
