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
package org.tentackle.maven.plugin.jlink;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Default artifact creator.<br>
 * Creates a single ZIP-file with platform and architecture classifier.
 */
//@Service(ArtifactCreator.class)  no @Service anno since it's part of the plugin itself
public class DefaultArtifactCreator implements ArtifactCreator {

  @Override
  public void createAndAttachArtifact(JlinkMojo mojo) throws MojoExecutionException {
    mojo.getProjectHelper().attachArtifact(mojo.getMavenProject(), JlinkMojo.ZIP_EXTENSION, mojo.getClassifier(), mojo.createZipFile());
  }

}
