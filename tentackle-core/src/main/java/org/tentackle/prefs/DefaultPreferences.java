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

import java.io.IOException;
import java.io.OutputStream;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

/**
 * Wraps a java util preferences node into a {@link PersistedPreferences} node.
 *
 * @see DefaultPreferencesFactory
 */
public class DefaultPreferences extends Preferences implements PersistedPreferences {

  private final Preferences preferences;

  /**
   * Creates a preferences node.
   *
   * @param preferences the java util preferences node to wrap
   */
  public DefaultPreferences(Preferences preferences) {
    this.preferences = preferences;
  }

  @Override
  public void put(String key, String value) {
    preferences.put(key, value);
  }

  @Override
  public String get(String key, String def) {
    return preferences.get(key, def);
  }

  @Override
  public void remove(String key) {
    preferences.remove(key);
  }

  @Override
  public void clear() throws BackingStoreException {
    preferences.clear();
  }

  @Override
  public void putInt(String key, int value) {
    preferences.putInt(key, value);
  }

  @Override
  public int getInt(String key, int def) {
    return preferences.getInt(key, def);
  }

  @Override
  public void putLong(String key, long value) {
    preferences.putLong(key, value);
  }

  @Override
  public long getLong(String key, long def) {
    return preferences.getLong(key, def);
  }

  @Override
  public void putBoolean(String key, boolean value) {
    preferences.putBoolean(key, value);
  }

  @Override
  public boolean getBoolean(String key, boolean def) {
    return preferences.getBoolean(key, def);
  }

  @Override
  public void putFloat(String key, float value) {
    preferences.putFloat(key, value);
  }

  @Override
  public float getFloat(String key, float def) {
    return preferences.getFloat(key, def);
  }

  @Override
  public void putDouble(String key, double value) {
    preferences.putDouble(key, value);
  }

  @Override
  public double getDouble(String key, double def) {
    return preferences.getDouble(key, def);
  }

  @Override
  public void putByteArray(String key, byte[] value) {
    preferences.putByteArray(key, value);
  }

  @Override
  public byte[] getByteArray(String key, byte[] def) {
    return preferences.getByteArray(key, def);
  }

  @Override
  public String[] keys() throws BackingStoreException {
    return preferences.keys();
  }

  @Override
  public String[] childrenNames() throws BackingStoreException {
    return preferences.childrenNames();
  }

  @Override
  public DefaultPreferences parent() {
    return new DefaultPreferences(preferences.parent());
  }

  @Override
  public DefaultPreferences node(String pathName) {
    return new DefaultPreferences(preferences.node(convertPathName(pathName)));
  }

  @Override
  public boolean nodeExists(String pathName) throws BackingStoreException {
    return preferences.nodeExists(convertPathName(pathName));
  }

  @Override
  public void removeNode() throws BackingStoreException {
    preferences.removeNode();
  }

  @Override
  public String name() {
    return preferences.name();
  }

  @Override
  public String absolutePath() {
    return preferences.absolutePath();
  }

  @Override
  public boolean isUserNode() {
    return preferences.isUserNode();
  }

  @Override
  public String toString() {
    return preferences.toString();
  }

  @Override
  public void flush() throws BackingStoreException {
    preferences.flush();
  }

  @Override
  public void sync() throws BackingStoreException {
    preferences.sync();
  }

  @Override
  public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
    preferences.addPreferenceChangeListener(pcl);
  }

  @Override
  public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
    preferences.removePreferenceChangeListener(pcl);
  }

  @Override
  public void addNodeChangeListener(NodeChangeListener ncl) {
    preferences.addNodeChangeListener(ncl);
  }

  @Override
  public void removeNodeChangeListener(NodeChangeListener ncl) {
    preferences.removeNodeChangeListener(ncl);
  }

  @Override
  public void exportNode(OutputStream os) throws IOException, BackingStoreException {
    preferences.exportNode(os);
  }

  @Override
  public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
    preferences.exportSubtree(os);
  }


  /**
   * Converts the given pathname if absolute.<br>
   * For pathname starting with a slash the optional name of the root node will be prepended.
   * By default, the name of the root node is the empty string, but applications may override
   * {@link DefaultPreferencesFactory#userRoot()} and/or {@link DefaultPreferencesFactory#systemRoot()}
   * to create an extra storage subfolder for the application.
   *
   * @param pathName the original pathname
   * @return the possibly converted pathname
   */
  protected String convertPathName(String pathName) {
    if (pathName.startsWith("/")) {   // absolute path
      String rootName = (isUserNode() ?
                         PersistedPreferencesFactory.getInstance().getUserRoot() :
                         PersistedPreferencesFactory.getInstance().getSystemRoot()).name();
      if (!rootName.isEmpty()) {
        pathName = "/" + rootName + pathName;
      }
    }
    return pathName;
  }

}
