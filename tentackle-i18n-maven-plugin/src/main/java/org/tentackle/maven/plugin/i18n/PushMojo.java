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

package org.tentackle.maven.plugin.i18n;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.tentackle.i18n.pdo.StoredBundle;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;


/**
 * Pushes resource bundles to the database backend.<br>
 * Existing translations are left unchanged.
 */
@Mojo(name = "push",
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true)
public class PushMojo extends AbstractI18nMojo {

  /**
   * Override database values.<br>
   * Default is to leave existing translations unchanged and add only new ones.
   */
  @Parameter
  protected boolean override;

  @Override
  public void processBundle(ClassLoader classLoader, String bundleName, String locale, String resourceName) {
    bundleCount++;
    try {
      Properties props = loadProperties(classLoader, resourceName);
      if (props != null) {
        push(bundleName, locale, props);
      }
      else {
        getLogger().warn("no such resource: {}", resourceName);
        warnings++;
      }
    }
    catch (IOException ex) {
      getLogger().error("could not load {}", resourceName, ex);
      errors++;
    }
  }


  /**
   * Pushes to the backend.
   *
   * @param bundleName the bundle class name
   * @param props the properties
   */
  private void push(String bundleName, String locale, Properties props) {
    StoredBundle bundle = on(StoredBundle.class).findByNameAndLocale(bundleName, locale);
    if (bundle == null) {
      // create a new one
      bundle = on(StoredBundle.class);
      bundle.setName(bundleName);
      bundle.setLocale(locale);
    }
    for (String key: props.stringPropertyNames()) {
      String value = props.getProperty(key);
      String oldValue = bundle.getTranslation(key);
      if (oldValue == null || override && !Objects.equals(value, oldValue)) {
        bundle.setTranslation(key, value);
        updates++;
      }
    }
    if (bundle.isModified()) {
      bundle.save();
    }
  }

}
