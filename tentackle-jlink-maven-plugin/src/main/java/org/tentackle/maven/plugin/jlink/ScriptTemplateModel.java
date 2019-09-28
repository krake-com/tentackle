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

import org.tentackle.maven.AbstractTemplateModel;

import java.util.StringTokenizer;

/**
 * Holds the template model for the script generator.
 */
public class ScriptTemplateModel extends AbstractTemplateModel {

  /**
   * Creates the model.
   *
   * @param mojo the jlink mojo
   * @param result the resolver results
   * @throws MojoExecutionException if module path could not generated
   */
  public ScriptTemplateModel(AbstractJlinkMojo mojo, JlinkResolver.Result result) throws MojoExecutionException {
    super(mojo.getLog());

    StringTokenizer stok = new StringTokenizer(mojo.getVariablesPrecedence(), " \t\r\n,");

    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      switch (token.toUpperCase()) {
        case "SYSTEM":
        case "SYS":
          // all system properties in camelCase, e.g. "os.name" converts to "osName"
          addProperties(System.getProperties());
          break;

        case "MAVEN":
        case "MVN":
        case "PROPERTIES":
        case "PROPS":
        case "POM":
          // dto. for all maven properties
          addProperties(mojo.getMavenProject().getProperties());
          break;

        case "VARIABLES":
        case "VARS":
        case "VAR":
        case "EXTRA":
        case "EXTRAS":
        case "TEMPLATE":
          // the optional template variables
          addMap(mojo.getVariables());
          break;

        default:
          throw new MojoExecutionException("unknown variables order keyword: " + token);
      }
    }

    // the following fixed properties cannot be overridden:
    putValue("phase", mojo.getMojoExecution().getLifecyclePhase());
    putValue("goal", mojo.getMojoExecution().getGoal());
    putValue("id", mojo.getMojoExecution().getExecutionId());

    putValue("mainModule", mojo.getMainModule());
    putValue("mainClass", mojo.getMainClass());
    putValue("modulePath", result.generateModulePath());
    putValue("classPath", result.generateClassPath());
  }

}
