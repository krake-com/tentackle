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

import org.tentackle.buildsupport.codegen.TemplateModel;

import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Default factory for the template model.
 */
//@Service(TemplateModelFactory.class)  no @Service anno since it's part of the plugin itself
public class DefaultTemplateModelFactory implements TemplateModelFactory {

  @Override
  public TemplateModel create(AbstractJLinkMojo mojo, JLinkResolver.Result result) throws MojoExecutionException {

    TemplateModel model = new TemplateModel();

    StringTokenizer stok = new StringTokenizer(mojo.getVariablesPrecedence(), " \t\r\n,");

    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      switch (token.toUpperCase(Locale.ROOT)) {
        case "SYSTEM":
        case "SYS":
          // all system properties in camelCase, e.g. "os.name" converts to "osName"
          model.addProperties(System.getProperties());
          break;

        case "MAVEN":
        case "MVN":
        case "PROPERTIES":
        case "PROPS":
        case "POM":
          // dto. for all maven properties
          model.addProperties(mojo.getMavenProject().getProperties());
          break;

        case "VARIABLES":
        case "VARS":
        case "VAR":
        case "EXTRA":
        case "EXTRAS":
        case "TEMPLATE":
          // the optional template variables
          model.addMap(mojo.getVariables());
          break;

        default:
          throw new MojoExecutionException("unknown variables precedence keyword: " + token);
      }
    }

    // the following fixed properties cannot be overridden:
    model.putValue("phase", mojo.getMojoExecution().getLifecyclePhase());
    model.putValue("goal", mojo.getMojoExecution().getGoal());
    model.putValue("id", mojo.getMojoExecution().getExecutionId());

    model.putValue("mainModule", mojo.getMainModule());
    model.putValue("mainClass", mojo.getMainClass());
    model.putValue("modulePath", result.generateModulePath());
    model.putValue("classPath", result.generateClassPath());

    model.putValue("layered", mojo.isLayered());
    if (mojo.isLayered()) {
      // the launcher runs this instead of mainModule/mainClass directly: it builds the configured
      // ModuleLayer graph at startup, then reflectively hands off to the real main class - see
      // org.tentackle.common.LayerBootstrap and the layers.md documentation
      model.putValue("bootstrapModule", JLinkResolver.BOOTSTRAP_MODULE_NAME);
      model.putValue("bootstrapClass", JLinkResolver.BOOTSTRAP_CLASS_NAME);
      model.putValue("layersConfigPath", AbstractJLinkMojo.DEST_RESOURCES + "/" + LayerResolver.LAYERS_PROPERTIES);
    }

    return model;
  }
}
