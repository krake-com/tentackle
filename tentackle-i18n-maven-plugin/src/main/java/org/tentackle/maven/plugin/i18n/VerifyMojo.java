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
import org.apache.maven.project.MavenProject;

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;
import org.tentackle.session.Session;

import java.util.List;
import java.util.Map;

/**
 * Verifies the bundles stored in the backend against the property resource bundles of the application.<br>
 * The mojo fails if missing translations in the database would raise a {@link java.util.MissingResourceException}.<br>
 * Notice: the application's resource bundles are verified via the <code>tentackle-check-maven-plugin</code> during the build process.
 */
@Mojo(name = "verify", aggregator = true, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class VerifyMojo extends AbstractVerifyMojo {


  @Override
  protected void processBundles(Session session, List<MavenProject> projects) throws MojoExecutionException {

    super.processBundles(session, projects);

    if (!differingTranslations.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, differingTranslations.size(), " differing translation");
      StoredBundle bundle = null;
      for (Translation translation : differingTranslations) {
        if (!translation.bundle.equals(bundle)) {
          // next bundle
          bundle = translation.bundle;
          buf.append('\n').append(bundle).append(':');
        }
        buf.append(" '").append(translation.key).append("'");
      }
      updates += differingTranslations.size();
      getLogger().info(buf.toString());
    }


    if (!obsoleteBundles.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, obsoleteBundles.size(), " obsolete bundle");
      for (StoredBundle bundle : sortBundles(obsoleteBundles)) {
        buf.append('\n').append(bundle);
      }
      warnings += obsoleteBundles.size();
      getLogger().warn(buf.toString());
    }

    if (!obsoleteKeys.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, obsoleteKeys.size(), " obsolete bundle key");
      StoredBundle bundle = null;
      for (StoredBundleKey bundleKey : sortBundleKeys(obsoleteKeys)) {
        if (!bundleKey.getBundle().equals(bundle)) {
          // next bundle
          bundle = bundleKey.getBundle();
          buf.append('\n').append(bundle).append(':');
        }
        buf.append(" '").append(bundleKey.getKey()).append("'");
      }
      warnings += obsoleteKeys.size();
      getLogger().warn(buf.toString());
    }

    List<String> resourceNames = resourceBundleMap.entrySet().stream().filter(entry -> entry.getValue().isNew()).map(Map.Entry::getKey).toList();
    if (!resourceNames.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, resourceNames.size(), " missing stored bundle");
      for (String resourceName : resourceNames) {
        buf.append(' ').append(resourceName);
      }
      warnings += resourceNames.size();
      getLogger().warn(buf.toString());
    }

    List<Translation> missingTrans = missingTranslations.stream().filter(translation -> translation.bundle.getLocale() != null).toList();
    if (!missingTrans.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, missingTrans.size(), " missing translation");
      StoredBundle bundle = null;
      for (Translation translation : missingTrans) {
        if (!translation.bundle.equals(bundle)) {
          // next bundle
          bundle = translation.bundle;
          buf.append('\n').append(bundle).append(':');
        }
        buf.append(" '").append(translation.key).append("'");
      }
      warnings += missingTrans.size();
      getLogger().warn(buf.toString());
    }

    missingTrans = missingTranslations.stream().filter(translation -> translation.bundle.getLocale() == null).toList();
    if (!missingTrans.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, missingTrans.size(), " missing default translation");
      StoredBundle bundle = null;
      for (Translation translation : missingTrans) {
        if (!translation.bundle.equals(bundle)) {
          // next bundle
          bundle = translation.bundle;
          buf.append('\n').append(bundle).append(':');
        }
        buf.append(" '").append(translation.key).append("'");
      }
      errors += missingTrans.size();
      getLogger().error(buf.toString());
    }
  }

  private void appendCount(StringBuilder buf, int count, String text) {
    buf.append(count).append(text);
    if (count > 1) {
      buf.append('s');
    }
    buf.append(':');
  }

}
