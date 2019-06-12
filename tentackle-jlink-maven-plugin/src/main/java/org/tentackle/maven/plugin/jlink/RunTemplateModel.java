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

import org.tentackle.common.StringHelper;

import java.util.HashMap;

/**
 * Holds the template model for the run script generator.
 */
public class RunTemplateModel extends HashMap<String, String> {

  private final AbstractJlinkMojo mojo;
  private final JlinkResolver.Result result;

  /**
   * Creates the model.
   *
   * @param mojo the jlink mojo
   * @param result the resolver results
   * @throws MojoExecutionException if module path could not generated
   */
  public RunTemplateModel(AbstractJlinkMojo mojo, JlinkResolver.Result result) throws MojoExecutionException {
    this.mojo = mojo;
    this.result = result;

    putValue("mainModule", mojo.getMainModule());
    putValue("mainClass", mojo.getMainClass());
    putValue("modulePath", result.generateModulePath());
    putValue("classPath", result.generateClassPath());

    // all system properties in camelCase, e.g. "os.name" converts to "osName"
    for (Entry<Object, Object> entry : System.getProperties().entrySet()) {
      putValue(StringHelper.toCamelCase(entry.getKey().toString()), entry.getValue().toString());
    }

    // dto. for all maven properties
    for (Entry<Object, Object> entry : mojo.getMavenProject().getProperties().entrySet()) {
      putValue(StringHelper.toCamelCase(entry.getKey().toString()), entry.getValue().toString());
    }
  }

  /**
   * Adds a key-value pair to the model.
   *
   * @param key the key
   * @param value the value
   */
  public void putValue(String key, Object value) {
    String val = value == null ? "" : value.toString();
    put(key, val);
    mojo.getLog().debug("script variable " + key + " = \"" + val + "\"");
  }

}
