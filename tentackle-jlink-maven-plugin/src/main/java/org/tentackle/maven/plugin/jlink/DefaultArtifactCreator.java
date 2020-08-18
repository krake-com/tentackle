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

import java.io.File;

/**
 * Default artifact creator.
 */
//@Service(ArtifactCreator.class)  no @Service anno since it's part of the plugin itself
public class DefaultArtifactCreator implements ArtifactCreator {

  private static final String[] INSTALLER_EXTENSIONS = {
      ".deb", ".rpm", ".exe", ".msi", ".pkg", ".dmg"
  };


  @Override
  public void createAndAttachArtifact(JlinkMojo mojo) throws MojoExecutionException {
    mojo.getProjectHelper().attachArtifact(mojo.getMavenProject(),
                                           JlinkMojo.ZIP_EXTENSION,
                                           mojo.getClassifier(),
                                           mojo.createZipFile(mojo.getFinalName()));
  }

  @Override
  public void processApplicationImage(JPackageMojo mojo, File imageDir) throws MojoExecutionException {
    // the default implementation does nothing
  }

  @Override
  public void attachInstallers(JPackageMojo mojo, long minTime) throws MojoExecutionException {
    File targetDir = new File(mojo.getMavenProject().getBuild().getDirectory());
    String[] names = targetDir.list((dir, name) -> {
      String fileName = name.toLowerCase();
      for (String extension : INSTALLER_EXTENSIONS) {
        if (fileName.endsWith(extension)) {
          File file = new File(targetDir, fileName);
          if (file.isFile() && file.lastModified() > minTime) {
            return true;
          }
        }
      }
      return false;
    });

    if (names != null) {
      for (String name : names) {
        int ndx = name.lastIndexOf('.');   // locate extension
        if (ndx >= 0) {
          mojo.getProjectHelper().attachArtifact(mojo.getMavenProject(),
                                                 name.substring(ndx + 1),
                                                 mojo.getClassifier(),
                                                 new File(targetDir, name));
        }
      }
    }
  }

}
