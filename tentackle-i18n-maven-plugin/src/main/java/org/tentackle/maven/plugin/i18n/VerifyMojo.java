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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Verifies the bundles stored in the backend against the property resource bundles of the application.<br>
 * The mojo fails if missing translations in the database could raise a {@link java.util.MissingResourceException}.<br>
 * Notice: the application's resource bundles are already verified via the <code>tentackle-check-maven-plugin</code>.
 *
 * @author harald
 */
@Mojo(name = "verify", aggregator = true, inheritByDefault = false,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class VerifyMojo extends AbstractI18nMojo {

  private class Translation implements Comparable<Translation> {
    private final StoredBundle bundle;
    private final String key;

    private Translation(StoredBundle bundle, String key) {
      this.bundle = bundle;
      this.key = key;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Translation that = (Translation) o;
      if (!bundle.equals(that.bundle)) {
        return false;
      }
      if (!key.equals(that.key)) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      int result = bundle.hashCode();
      result = 31 * result + key.hashCode();
      return result;
    }

    @Override
    public int compareTo(Translation o) {
      int rv = bundle.getName().compareTo(o.bundle.getName());
      if (rv == 0) {
        rv = nullSafe(bundle.getLocale()).compareTo(nullSafe(o.bundle.getLocale()));
        if (rv == 0) {
          rv = key.compareTo(o.key);
        }
      }
      return rv;
    }
  }


  private Set<StoredBundle> obsoleteBundles;                // obsolete bundles
  private Set<StoredBundleKey> obsoleteKeys;                // obsolete bundle keys
  private Set<Translation> missingTranslations;             // missing bundle keys
  private Set<Translation> differingTranslations;           // differing translations
  private Map<String, StoredBundle> resourceBundleMap;      // resource name -> stored bundle
  private Map<String, Properties> propertiesMap;            // bundlename -> default properties for the null-locale


  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      missingTranslations = new TreeSet<>();
      differingTranslations = new TreeSet<>();
      obsoleteKeys = new HashSet<>();
      resourceBundleMap = new TreeMap<>();
      propertiesMap = new HashMap<>();
      return true;
    }
    return false;
  }

  @Override
  protected void processBundles(Session session, List<MavenProject> projects) throws MojoExecutionException {
    obsoleteBundles = new HashSet<>(on(StoredBundle.class).selectAll());    // check all languages!

    super.processBundles(session, projects);

    // filter obsolete bundle names
    for (Iterator<StoredBundle> iterator=obsoleteBundles.iterator(); iterator.hasNext(); ) {
      StoredBundle bundle = iterator.next();
      Properties props = propertiesMap.get(bundle.getName());
      if (props != null) {
        iterator.remove();    // not obsolete, just for another language
        findObsoleteKeys(props, bundle);    // we're checking only for obsolete keys, not missing translations
      }
      else {
        // the whole bundle is obsolete: remove all obsolete keys for that bundle
        for (Iterator<StoredBundleKey> keyIterator=obsoleteKeys.iterator(); keyIterator.hasNext(); ) {
          StoredBundleKey bundleKey = keyIterator.next();
          if (bundleKey.getBundleId() == bundle.getId()) {
            keyIterator.remove();
          }
        }
      }
    }

    if (!obsoleteBundles.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, obsoleteBundles.size(), " obsolete bundle");
      for (StoredBundle bundle : sortBundles(obsoleteBundles)) {
        buf.append('\n').append(bundle);
      }
      warnings += obsoleteBundles.size();
      getLog().warn(buf);
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
      getLog().warn(buf);
    }

    List<String> resourceNames = resourceBundleMap.entrySet().stream().filter(
        entry -> entry.getValue().isNew()).map(entry -> entry.getKey()).collect(Collectors.toList());
    if (!resourceNames.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, resourceNames.size(), " missing stored bundle");
      for (String resourceName : resourceNames) {
        buf.append(' ').append(resourceName);
      }
      warnings += resourceNames.size();
      getLog().warn(buf);
    }

    if (!differingTranslations.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, differingTranslations.size(), " differing translation");
      StoredBundle bundle = null;
      for (Translation translation : differingTranslations) {
        if (!translation.equals(bundle)) {
          // next bundle
          bundle = translation.bundle;
          buf.append('\n').append(bundle).append(':');
        }
        buf.append(" '").append(translation.key).append("'");
      }
      warnings += differingTranslations.size();
      getLog().warn(buf);
    }

    List<Translation> missingTranslations = this.missingTranslations.stream().filter(
        translation -> translation.bundle.getLocale() != null).collect(Collectors.toList());
    if (!missingTranslations.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, missingTranslations.size(), " missing translation");
      StoredBundle bundle = null;
      for (Translation translation : missingTranslations) {
        if (!translation.equals(bundle)) {
          // next bundle
          bundle = translation.bundle;
          buf.append('\n').append(bundle).append(':');
        }
        buf.append(" '").append(translation.key).append("'");
      }
      warnings += missingTranslations.size();
      getLog().warn(buf);
    }

    missingTranslations = this.missingTranslations.stream().filter(
        translation -> translation.bundle.getLocale() == null).collect(Collectors.toList());
    if (!missingTranslations.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      appendCount(buf, missingTranslations.size(), " missing default translation");
      StoredBundle bundle = null;
      for (Translation translation : missingTranslations) {
        if (!translation.equals(bundle)) {
          // next bundle
          bundle = translation.bundle;
          buf.append('\n').append(bundle).append(':');
        }
        buf.append(" '").append(translation.key).append("'");
      }
      errors += missingTranslations.size();
      getLog().error(buf);
    }

  }

  @Override
  public void processBundle(ClassLoader classLoader, String bundleName, String locale, String resourceName) {
    resourceBundleMap.computeIfAbsent(resourceName, resName -> {
      bundleCount++;
      StoredBundle bundle = null;
      try {
        Properties props = loadProperties(classLoader, resName);
        if (props != null) {
          bundle = verify(bundleName, locale, props);
        }
        else {
          getLog().warn("no such resource: " + resName);
          warnings++;
        }
      }
      catch (IOException ex) {
        getLog().error("could not load " + resName, ex);
        errors++;
      }
      if (bundle == null) {
        bundle = on(StoredBundle.class);    // new if missing
      }
      return bundle;
    });
  }


  /**
   * Verifies a bundle properties against the backend.
   *
   * @param bundleName the bundle class name
   * @param locale the locale, null if default
   * @param props the properties
   * @return the bundle, null if missing
   */
  private StoredBundle verify(String bundleName, String locale, Properties props) {
    StoredBundle bundle = on(StoredBundle.class).findByNameAndLocale(bundleName, locale);
    if (bundle != null) {
      obsoleteBundles.remove(bundle);
      if (locale == null) {
        propertiesMap.put(bundle.getName(), props);
      }
      findDivergingTranslations(props, bundle);
      findObsoleteKeys(props, bundle);
    }
    return bundle;
  }

  private void findDivergingTranslations(Properties props, StoredBundle bundle) {
    for (String key : props.stringPropertyNames()) {
      String propValue = props.getProperty(key);
      String dbValue = bundle.getTranslation(key);
      if (dbValue == null) {
        missingTranslations.add(new Translation(bundle, key));
      }
      else if (!Objects.equals(propValue, dbValue)) {
        differingTranslations.add(new Translation(bundle, key));
      }
    }
  }

  private void findObsoleteKeys(Properties props, StoredBundle bundle) {
    for (StoredBundleKey bundleKey : bundle.getKeys()) {
      String propValue = props.getProperty(bundleKey.getKey());
      if (propValue == null) {
        obsoleteKeys.add(bundleKey);
      }
    }
  }

  private Collection<StoredBundle> sortBundles(Collection<StoredBundle> bundles) {
    List<StoredBundle> list = new ArrayList<>(bundles);
    Collections.sort(list, (o1, o2) -> {
      int rv = o1.getName().compareTo(o2.getName());
      if (rv == 0) {
        rv = nullSafe(o1.getLocale()).compareTo(nullSafe(o2.getLocale()));
      }
      return rv;
    });
    return list;
  }

  private Collection<StoredBundleKey> sortBundleKeys(Collection<StoredBundleKey> bundlesKeys) {
    List<StoredBundleKey> list = new ArrayList<>(bundlesKeys);
    Collections.sort(list, (o1, o2) -> {
      int rv = o1.getBundle().getName().compareTo(o2.getBundle().getName());
      if (rv == 0) {
        rv = nullSafe(o1.getBundle().getLocale()).compareTo(nullSafe(o2.getBundle().getLocale()));
        if (rv == 0) {
          rv = o1.getKey().compareTo(o2.getKey());
        }
      }
      return rv;
    });
    return list;
  }

  private String nullSafe(String s) {
    return s == null ? "" : s;
  }

  private void appendCount(StringBuilder buf, int count, String text) {
    buf.append(count).append(text);
    if (count > 1) {
      buf.append('s');
    }
    buf.append(':');
  }

}
