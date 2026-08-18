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

package org.tentackle.update;

/**
 * The minimal {@link AbstractUpdateServiceImpl} subclass an application has to provide:
 * the artifact name and the version it offers.
 * <p>
 * Must be public with a public {@code String} constructor, because
 * {@code ServerUpdateUtilities.createUpdateServiceImpl} instantiates it reflectively.
 */
public class TestUpdateServiceImpl extends AbstractUpdateServiceImpl {

  /** The artifact name used throughout the tests. */
  public static final String ARTIFACT_NAME = "myapp-jlink-client";

  /** The version the test server offers. */
  public static final String VERSION = "2.1.0";

  /**
   * Creates the service.
   *
   * @param updateURL the download base URL
   */
  public TestUpdateServiceImpl(String updateURL) {
    super(updateURL);
  }

  @Override
  public String getArtifactName() {
    return ARTIFACT_NAME;
  }

  @Override
  public String getVersion() {
    return VERSION;
  }

}
