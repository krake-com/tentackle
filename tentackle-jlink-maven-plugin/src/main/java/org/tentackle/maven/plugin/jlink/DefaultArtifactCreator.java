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
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Default artifact creator.
 */
//@Service(ArtifactCreator.class)  no @Service anno since it's part of the plugin itself
public class DefaultArtifactCreator implements ArtifactCreator {

  private static final String[] INSTALLER_EXTENSIONS = {
      ".deb", ".rpm", ".exe", ".msi", ".pkg", ".dmg"
  };


  @Override
  public void createAndAttachArtifact(JLinkMojo mojo) throws MojoExecutionException {
    mojo.getProjectHelper().attachArtifact(mojo.getMavenProject(),
                                           AbstractJLinkMojo.ZIP_EXTENSION,
                                           mojo.getClassifier(),
                                           createZipFile(mojo.getImageDirectory(),
                                                         mojo.getFinalName() + "-" + mojo.getImageDirectory().getName(),
                                                         mojo.getZipDirectory(), mojo.getZipArchiver()));
  }

  @Override
  public void processApplicationImage(JPackageMojo mojo, File imageDir) throws MojoExecutionException {
    // the default implementation does nothing
  }

  @Override
  public void attachInstallers(JPackageMojo mojo, long minTime) throws MojoExecutionException {
    File targetDir = new File(mojo.getMavenProject().getBuild().getDirectory());
    String[] names = targetDir.list((_, name) -> {
      String fileName = name.toLowerCase(Locale.ROOT);
      for (String extension : INSTALLER_EXTENSIONS) {
        if (fileName.endsWith(extension)) {
          // notice: the real name, not the lowercased one, which does not exist on a
          // case-sensitive filesystem
          File file = new File(targetDir, name);
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
                                                 // the artifact handlers are registered for the
                                                 // lowercase extensions (see components.xml)
                                                 name.substring(ndx + 1).toLowerCase(Locale.ROOT),
                                                 mojo.getClassifier(),
                                                 new File(targetDir, name));
        }
      }
    }
  }

  @Override
  public void createAndAttachArtifact(JPackageMojo mojo, File appImageDir) throws MojoExecutionException {
    mojo.getProjectHelper().attachArtifact(mojo.getMavenProject(),
                                           AbstractJLinkMojo.ZIP_EXTENSION,
                                           mojo.getClassifier() + "-pkg",
                                           createZipFile(appImageDir,
                                                         mojo.getFinalName() + "-" + mojo.getPackageDirectory().getName(),
                                                         mojo.getZipDirectory(), mojo.getZipArchiver()));
  }


  /**
   * Creates the ZIP file.
   *
   * @param dir the directory to create a ZIP file from
   * @param name the ZIP filename without extension
   * @param zipDirectory the directory where to store the ZIP file
   * @param zipArchiver the ZIP archiver to use
   * @return the created ZIP file
   * @throws MojoExecutionException if some archiver or IO errors occurred
   */
  protected File createZipFile(File dir, String name, File zipDirectory, ZipArchiver zipArchiver) throws MojoExecutionException {
    File zipFile = new File(zipDirectory, name + "." + AbstractJLinkMojo.ZIP_EXTENSION);
    zipArchiver.addFileSet(DefaultFileSet.fileSet(dir).prefixed("")
                                         .includeExclude(null, null)
                                         .includeEmptyDirs(zipArchiver.getIncludeEmptyDirs()));
    zipArchiver.setDestFile(zipFile);
    try {
      zipArchiver.createArchive();
    }
    catch (ArchiverException | IOException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    return zipFile;
  }

}
