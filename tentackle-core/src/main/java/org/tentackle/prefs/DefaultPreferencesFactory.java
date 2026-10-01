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
package org.tentackle.prefs;

import org.tentackle.reflect.ReflectionHelper;

import java.util.prefs.Preferences;

/**
 * Factory for {@link Preferences} wrapped by {@link PersistedPreferences}.<br>
 * Use case is to map invocations of PersistedPreferences by the framework or applications back to the standard java util prefs.
 * The preferences will then no longer be persisted in the database.<br>
 * Auto-sync and read-only are not supported by java util prefs and are just stored.<br>
 * To activate, extend this class and annotate with <code>@Service(PersistedPreferencesFactory.class)</code>.<br>
 * You may also override the methods isSystemOnly, systemRoot, and systemNodeForPackage since system root is usually not writable by normal users.<br>
 * Furthermore, consider overriding userRoot/systemRoot as well to prepend a sub node for non-class preferences such as table settings.
 * <p>
 * Example:
 * <pre>
 *
 * &#64;Service(PersistedPreferencesFactory.class)
 * public class JavaUtilPreferencesFactory extends DefaultPreferencesFactory {
 *
 *   &#64;Override
 *   public boolean isSystemOnly() {
 *     return false;
 *   }
 *
 *   &#64;Override
 *   public DefaultPreferences userRoot() {
 *     return super.userRoot().node("MyApp");
 *   }
 *
 *   &#64;Override
 *   public DefaultPreferences systemRoot() {
 *     return userRoot();
 *   }
 *
 *   &#64;Override
 *   public DefaultPreferences systemNodeForPackage(Class&lt;?&gt; c) {
 *     return userNodeForPackage(c);
 *   }
 * }
 * </pre>
 *
 * CAUTION: don't register this factory as a {@link java.util.prefs.PreferencesFactory} as this would
 * cause an endless loop!
 */
public class DefaultPreferencesFactory implements PersistedPreferencesFactory {

  private boolean autoSync;     // not supported by java.util.prefs, just stored
  private boolean readOnly;     // not supported by java.util.prefs, just stored
  private boolean systemOnly;   // the user root is the system root

  private DefaultPreferences systemRoot;
  private DefaultPreferences userRoot;


  /**
   * Creates the preferences factory.
   */
  public DefaultPreferencesFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void invalidate() {
    // nothing to do
  }

  @Override
  public boolean isAutoSync() {
    return autoSync;
  }

  @Override
  public void setAutoSync(boolean autoSync) {
    this.autoSync = autoSync;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  @Override
  public boolean isSystemOnly() {
    return systemOnly;
  }

  @Override
  public void setSystemOnly(boolean systemOnly) {
    this.systemOnly = systemOnly;
  }

  @Override
  public DefaultPreferences getSystemRoot() {
    DefaultPreferences root = systemRoot;
    if (root == null) {
      synchronized (this) {
        root = systemRoot;
        if (root == null) {
          systemRoot = systemRoot();
        }
      }
    }
    return systemRoot;
  }

  @Override
  public DefaultPreferences getUserRoot() {
    DefaultPreferences root = userRoot;
    if (root == null) {
      synchronized (this) {
        root = userRoot;
        if (root == null) {
          userRoot = isSystemOnly() ? systemRoot() : userRoot();
        }
      }
    }
    return userRoot;
  }

  @Override
  public DefaultPreferences userNodeForPackage(Class<?> c) {
    return getUserRoot().node(nodeName(c));
  }

  @Override
  public DefaultPreferences systemNodeForPackage(Class<?> c) {
    return getSystemRoot().node(nodeName(c));
  }

  @Override
  public DefaultPreferences systemRoot() {
    return new DefaultPreferences(Preferences.systemRoot());
  }

  @Override
  public DefaultPreferences userRoot() {
    return new DefaultPreferences(Preferences.userRoot());
  }

  /**
   * Returns the absolute path name of the node corresponding to the package of the specified class.
   *
   * @param clazz the class
   * @return the path name
   * @throws IllegalArgumentException if the package has no preference node associated with it
   */
  protected String nodeName(Class<?> clazz) {
    if (clazz.isArray()) {
      throw new IllegalArgumentException("Arrays have no associated preferences node");
    }
    String pkgName = ReflectionHelper.getPackagePathName(clazz);
    return "/".equals(pkgName) ?
           "/<unnamed>" :    // conforms to Preferences#nodeName
           pkgName;
  }

}
