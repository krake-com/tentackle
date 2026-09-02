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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.tentackle.common.Settings;
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;
import org.tentackle.maven.PackageInfo;
import org.tentackle.reflect.ReflectionHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Properties;


/**
 * Pulls resource bundles from the database backend.<br>
 * If the database contents differ from the resource bundle property files, the property files will be updated.
 */
@Mojo(name = "pull",
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class PullMojo extends AbstractI18nMojo {

  private Map<String, PackageInfo> packageMap;

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      packageMap = createPackageMap(true);
      return true;
    }
    return false;
  }

  @Override
  public void processBundle(ClassLoader classLoader, String bundleName, String locale, String resourceName) throws MojoExecutionException {
    StoredBundle bundle = on(StoredBundle.class).findByNameAndLocale(bundleName, locale);
    String packageName = ReflectionHelper.getPackageName(bundleName);
    PackageInfo info = packageMap.get(packageName);
    if (info != null) {   // if part of the project
      bundleCount++;
      if (bundle == null) {
        // never pushed, or not translated for that locale: there is nothing to pull
        getLog().warn("missing stored bundle: " + resourceName);
        warnings++;
        return;
      }
      int ndx = resourceName.lastIndexOf('/');
      String fileName = ndx >= 0 ? resourceName.substring(ndx + 1) : resourceName;
      File file = new File(info.getPath(), fileName);
      boolean differs = false;
      Properties properties;
      try {
        properties = loadProperties(file);
        if (properties == null) {
          getLog().warn("created resource: " + resourceName);
          warnings++;
          properties = new OrderedProperties();
        }
        for (StoredBundleKey bundleKey : bundle.getKeys()) {
          String propValue = properties.getProperty(bundleKey.getKey());
          if (!bundleKey.getValue().equals(propValue)) {
            properties.setProperty(bundleKey.getKey(), bundleKey.getValue());
            getLog().info(resourceName + ": '" + bundleKey.getKey() + "' " + (propValue == null ? "added" : "updated"));
            updates++;
            differs = true;
          }
        }
      }
      catch (IOException iox) {
        getLog().error("cannot load resource bundle properties file " + file.getAbsolutePath(), iox);
        errors++;
        return;
      }

      if (differs) {
        try (Writer writer = new FileWriter(file, Settings.getEncodingCharset())) {
          String comment = "pulled from " + url + " by " + System.getProperty("user.name") + "@" + getHostName();
          properties.store(writer, comment);
        }
        catch (IOException iox) {
          getLog().error("cannot update resource bundle properties file " + file.getAbsolutePath(), iox);
          errors++;
        }
      }
    }
  }

}
