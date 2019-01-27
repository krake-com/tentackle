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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

import java.io.File;
import java.io.IOException;

/**
 * Mojo to create an application ZIP with jlink.
 */
@Mojo(name = "jlink",
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      defaultPhase = LifecyclePhase.PACKAGE,
      requiresProject = true)

public class JlinkMojo extends AbstractJlinkMojo {

  /**
   * The archiver to zip the image.
   */
  @Component(role = Archiver.class, hint = "zip")
  private ZipArchiver zipArchiver;

  /**
   * The directory where to store the ZIP file.
   */
  @Parameter(defaultValue = "${project.build.directory}", required = true)
  private File zipDirectory;

  /**
   * The generated ZIP file name.
   */
  @Parameter(defaultValue = "${project.build.finalName}", required = true)
  private String finalName;


  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    super.execute();
    File zipFile = createZipFile();
    getMavenProject().getArtifact().setFile(zipFile);
  }

  private File createZipFile() throws MojoExecutionException {
    File zipFile = new File(zipDirectory, finalName + ".zip");
    zipArchiver.addDirectory(getImageDirectory());
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
