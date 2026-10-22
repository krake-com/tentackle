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

import org.tentackle.common.ServiceFactory;

import java.util.prefs.PreferencesFactory;


interface PersistedPreferencesFactoryHolder {
  PersistedPreferencesFactory INSTANCE = ServiceFactory.createService(PersistedPreferencesFactory.class);
}


/**
 * Factory for {@link PersistedPreferences}.
 * <p>
 * Tentackle does not use the {@link java.util.prefs.Preferences}.
 * Instead, it provides an implementation that stores the preferences in the database (see tentackle-database).
 * By default, {@link PersistedPreferences} and {@link java.util.prefs.Preferences} co-exist independently.
 * However, the {@link PersistedPreferences} can be used as a drop-in replacement for the {@link java.util.prefs.Preferences},
 * so that 3rd-party software using the preferences-API may use the database backing store as well.
 * <p>
 * The implementation is located via &#64;{@link org.tentackle.common.Service}. There is no default in tentackle-core:
 * either the database implementation or an application-specific one, such as an extension of the
 * {@link DefaultPreferencesFactory}, must be provided.
 */
public interface PersistedPreferencesFactory extends PreferencesFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static PersistedPreferencesFactory getInstance() {
    return PersistedPreferencesFactoryHolder.INSTANCE;
  }

  /**
   * Invalidates all loaded and/or cached nodes.<br>
   * The listeners remain registered.<br>
   * Any modifications not flushed so far will be discarded as well.
   */
  void invalidate();

  /**
   * Returns whether the preferences are automatically synchronized
   * with the database backend.<p>
   * The default depends on the implementation.
   *
   * @return true if auto sync, false if {@link PersistedPreferences#sync()} must be invoked explicitly
   */
  boolean isAutoSync();

  /**
   * Sets the auto-sync feature.
   *
   * @param autoSync true if auto-sync
   */
  void setAutoSync(boolean autoSync);

  /**
   * Returns whether preferences are readonly.
   * <p>
   * If readonly all modifications to the preferences will be silently ignored.
   * The default is read-write.
   *
   * @return true if readonly
   */
  boolean isReadOnly();

  /**
   * Sets the readonly feature.
   *
   * @param readOnly true if readonly
   */
  void setReadOnly(boolean readOnly);

  /**
   * Returns whether retrieving and storing preferences refers to the system scope only.
   * <p>
   * This feature is nice to configure a system for all users.
   * The default is user and system scope.
   *
   * @return true if system-only
   */
  boolean isSystemOnly();

  /**
   * Sets the system-only feature.
   *
   * @param systemOnly true if system scope
   */
  void setSystemOnly(boolean systemOnly);

  /**
   * Returns the system root preference node.<br>
   * Multiple calls on this method will return the same object reference.
   *
   * @return the system root preference node
   */
  PersistedPreferences getSystemRoot();

  /**
   * Returns the user root preference node corresponding to the calling user.
   *
   * @return the user root preference node corresponding to the calling user, system root if isSystemOnly
   * @see #isSystemOnly()
   */
  PersistedPreferences getUserRoot();

  /**
   * Returns the preference node from the calling user's preference tree that is associated (by convention) with the
   * specified class's package.
   *
   * @param c the class for whose package a user preference node is desired.
   *
   * @return the user preference node associated with the package of which
   * <code>c</code> is a member.
   *
   * @see java.util.prefs.Preferences#userNodeForPackage(java.lang.Class)
   */
  PersistedPreferences userNodeForPackage(Class<?> c);

  /**
   * Returns the preference node from the calling system's preference tree that is associated (by convention) with the
   * specified class's package.
   *
   * @param c the class for whose package a system preference node is desired.
   *
   * @return the system preference node associated with the package of which
   * <code>c</code> is a member.
   *
   * @see java.util.prefs.Preferences#systemNodeForPackage(java.lang.Class)
   */
  PersistedPreferences systemNodeForPackage(Class<?> c);

}
