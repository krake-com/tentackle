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

package org.tentackle.maven;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.Settings;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests the guard of the JVM-wide encoding settings in parallel builds.
 */
public class EncodingTest {

  private Charset savedCharset;
  private Charset savedWurbelCharset;

  @BeforeMethod
  public void saveSettings() {
    savedCharset = Settings.getEncodingCharset();
    savedWurbelCharset = org.wurbelizer.misc.Settings.getEncodingCharset();
  }

  @AfterMethod
  public void restoreSettings() {
    Settings.setEncodingCharset(savedCharset);
    org.wurbelizer.misc.Settings.setEncodingCharset(savedWurbelCharset);
  }


  @Test
  public void sameCharsetInParallelBuild() throws MojoExecutionException {
    MavenSession session = createSession(true);
    createMojo(session, "UTF-8").determineEncoding();
    createMojo(session, "UTF-8").determineEncoding();
    assertEquals(Settings.getEncodingCharset(), StandardCharsets.UTF_8);
    assertEquals(org.wurbelizer.misc.Settings.getEncodingCharset(), StandardCharsets.UTF_8);
  }

  @Test
  public void conflictingCharsetsInParallelBuild() throws MojoExecutionException {
    MavenSession session = createSession(true);
    createMojo(session, "UTF-8").determineEncoding();
    AbstractTentackleMojo mojo = createMojo(session, "ISO-8859-1");
    assertThrows(MojoExecutionException.class, mojo::determineEncoding);
    assertEquals(Settings.getEncodingCharset(), StandardCharsets.UTF_8);
  }

  @Test
  public void conflictingCharsetsInSequentialBuild() throws MojoExecutionException {
    MavenSession session = createSession(false);
    createMojo(session, "UTF-8").determineEncoding();
    createMojo(session, "ISO-8859-1").determineEncoding();
    assertEquals(Settings.getEncodingCharset(), StandardCharsets.ISO_8859_1);
  }

  @Test
  public void conflictingCharsetsInDifferentSessions() throws MojoExecutionException {
    createMojo(createSession(true), "UTF-8").determineEncoding();
    createMojo(createSession(true), "ISO-8859-1").determineEncoding();
    assertEquals(Settings.getEncodingCharset(), StandardCharsets.ISO_8859_1);
  }


  @SuppressWarnings("deprecation")
  private MavenSession createSession(boolean parallel) {
    MavenSession session = new MavenSession(null, null, new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult());
    session.setParallel(parallel);
    return session;
  }

  private AbstractTentackleMojo createMojo(MavenSession session, String charset) {
    AbstractTentackleMojo mojo = new AbstractTentackleMojo() {
      @Override
      public void executeImpl() {}
    };
    mojo.charset = charset;
    try {
      Field field = AbstractTentackleMojo.class.getDeclaredField("mavenSession");
      field.setAccessible(true);
      field.set(mojo, session);
    }
    catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return mojo;
  }
}
