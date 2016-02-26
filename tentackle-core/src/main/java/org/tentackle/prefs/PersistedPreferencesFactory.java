/**
 * Tentackle - http://www.tentackle.org
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

import java.io.IOException;
import java.io.InputStream;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;
import org.tentackle.common.ServiceFactory;



interface PersistedPreferencesFactory$Singleton {
  PersistedPreferencesFactory INSTANCE = ServiceFactory.createService(PersistedPreferencesFactory.class);
}


/**
 * Factory for PersistedPreferences.
 * <p>
 * Tentackle does not use the java.util.Preferences due to several implementation issues,
 * especially in EE containers.
 * Instead it provides an implementation that stores the preferences in the databases.
 * However, this implementation can be used as a drop-in replacement for the java.util.Preferences
 * so that third-party software using the preferences-API may use the database backing store as well.
 */
public interface PersistedPreferencesFactory extends PreferencesFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PersistedPreferencesFactory getInstance() {
    return PersistedPreferencesFactory$Singleton.INSTANCE;
  }


  /**
   * Returns whether the preferences are automatically sync'd
   * with the database backend.<p>
   * The default is autoSync enabled.
   *
   * @return true if auto sync, false if {@link PersistedPreferences#sync()} must be invoked explicitly
   */
  boolean isAutoSync();

  /**
   * Sets the autosync feature.
   *
   * @param autoSync true if autosync
   */
  void setAutoSync(boolean autoSync);

  /**
   * Returns whether preferences are readonly.
   * <p>
   * If readonly all modifications to the preferences will be silently ignored.
   * Default is read-write.
   *
   * @return true readonly
   */
  boolean isReadOnly();

  /**
   * Sets the reaonly feature.
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
   * @return true if system only
   */
  boolean isSystemOnly();

  /**
   * Sets the system-only feature.
   *
   * @param systemOnly true if system scope
   */
  void setSystemOnly(boolean systemOnly);

  /**
   * Returns the system root preference node. (Multiple calls on this method will return the same object reference.)
   * <p>
   * @return the system root preference node
   */
  PersistedPreferences getSystemRoot();

  /**
   * Returns the user root preference node corresponding to the calling user. In a server, the returned value will
   * typically depend on some implicit client-context.
   * <p>
   * @return the user root preference node corresponding to the calling user
   */
  PersistedPreferences getUserRoot();

  /**
   * Returns the preference node from the calling user's preference tree that is associated (by convention) with the
   * specified class's package.
   * <p>
   * @param c the class for whose package a user preference node is desired.
   * <p>
   * @return the user preference node associated with the package of which
   * <tt>c</tt> is a member.
   *
   * @see Preferences#userNodeForPackage(java.lang.Class)
   */
  PersistedPreferences userNodeForPackage(Class<?> c);

  /**
   * Returns the preference node from the calling system's preference tree that is associated (by convention) with the
   * specified class's package.
   * <p>
   * @param c the class for whose package a system preference node is desired.
   * <p>
   * @return the system preference node associated with the package of which
   * <tt>c</tt> is a member.
   *
   * @see Preferences#systemNodeForPackage(java.lang.Class)
   */
  PersistedPreferences systemNodeForPackage(Class<?> c);

  /**
   * Imports all of the preferences represented by the XML document on the specified input stream. The document may
   * represent user preferences or system preferences. If it represents user preferences, the preferences will be
   * imported into the calling user's preference tree (even if they originally came from a different user's preference
   * tree). If any of the preferences described by the document inhabit preference nodes that do not exist, the nodes
   * will be created.
   *
   * @param is the input stream from which to read the XML document.
   * @throws IOException                       if reading from the specified input stream results in an
   *                                           <tt>IOException</tt>.
   * @throws InvalidPreferencesFormatException Data on input stream does not constitute a valid XML document with the
   *                                           mandated document type.
   * @throws SecurityException                 If a security manager is present and it denies
   *                                           <tt>RuntimePermission("preferences")</tt>.
   * @see RuntimePermission
   */
  void importPreferences(InputStream is) throws IOException, InvalidPreferencesFormatException;

}
