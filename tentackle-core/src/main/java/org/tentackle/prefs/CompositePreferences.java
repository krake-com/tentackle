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

import java.util.Objects;
import java.util.prefs.BackingStoreException;

/**
 * User- and System-Preferences combined.
 * <p>
 * Implements preferences in such a way that
 * user space overrides system space, which is the default
 * for most applications. Setting a user value equal to the system value removes the user value.
 * <p>
 * This class is abstract because it should be extended by
 * the application to get its own namespace: the nodes are those of the subclass's package.
 *
 * @author harald
 */
public abstract class CompositePreferences {


  private final boolean systemOnly;               // true if refer to system-preferences only
  private PersistedPreferences userPrefs;         // preferences for current user
  private PersistedPreferences systemPrefs;       // system default prefs


  /**
   * Creates composite preferences.
   *
   * @param systemOnly true if refer to system-preferences only, false = user space overrides system space
   */
  public CompositePreferences(boolean systemOnly) {
    this.systemOnly = systemOnly;
  }


  /**
   * Returns whether preferences are system-only or user space overrides system space.
   *
   * @return true if system-only, false user first with system default
   */
  public boolean isSystemOnly() {
    return systemOnly;
  }

  /**
   * Gets the user preferences.
   *
   * @return the user-scope preferences, null if systemOnly
   */
  public PersistedPreferences getUserPrefs()  {
    if (!systemOnly && userPrefs == null) {
      userPrefs = PersistedPreferencesFactory.getInstance().userNodeForPackage(this.getClass());
    }
    return userPrefs;
  }

  /**
   * Gets the system preferences.
   *
   * @return the system-scope preferences
   */
  public PersistedPreferences getSystemPrefs()  {
    if (systemPrefs == null) {
      systemPrefs = PersistedPreferencesFactory.getInstance().systemNodeForPackage(this.getClass());
    }
    return systemPrefs;
  }


  /**
   * Flushes the preferences (system and user).
   *
   * @throws BackingStoreException if failed
   */
  public void flush() throws BackingStoreException {
    if (!systemOnly) {
      getUserPrefs().flush();
    }
    getSystemPrefs().flush();
  }


  /**
   * Syncs the preferences (system and user).
   *
   * @throws BackingStoreException if failed
   */
  public void sync() throws BackingStoreException {
    if (!systemOnly) {
      getUserPrefs().sync();
    }
    getSystemPrefs().sync();
  }


  /**
   * Gets the value for a key.<br>
   * User space overrides system space.
   *
   * @param key the preference key
   * @return the value string or null if no such value
   */
  public String getString(String key) {
    String val = null;
    if (!systemOnly) {
      val = getUserPrefs().get(key, null);
    }
    if (val == null) {
      val = getSystemPrefs().get(key, null);
    }
    return val;
  }

  /**
   * Gets the value for a key.<br>
   * User space overrides system space.
   *
   * @param key the preference key
   * @param def the default value
   * @return the value string
   */
  public String getString(String key, String def) {
    String val = getString(key);
    return val == null ? def : val;
  }

  /**
   * Sets the value for a key.<br>
   * If the value is the same as in system space, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preference key
   * @param val the value
   */
  public void setString(String key, String val) {
    if (!systemOnly)  {
      if (val == null || Objects.equals(val, getSystemPrefs().get(key, null))) {
        getUserPrefs().remove(key);
      }
      else  {
        getUserPrefs().put(key, val);
      }
    }
    else  {
      if (val == null)  {
        getSystemPrefs().remove(key);
      }
      else  {
        getSystemPrefs().put(key, val);
      }
    }
  }


  /**
   * Gets an Integer.
   *
   * @param key the preference key
   * @return the integer or null if no such value
   * @throws NumberFormatException if the value is not an integer
   */
  public Integer getInteger(String key) {
    String val = getString(key);
    return val == null ? null : Integer.valueOf(val);
  }

  /**
   * Gets an int value.
   *
   * @param key the preference key
   * @param def the default value
   * @return the integer
   */
  public int getInt(String key, int def) {
    Integer val = getInteger(key);
    return val == null ? def : val;
  }

  /**
   * Sets an integer.<br>
   * If the value is the same as in system space, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preference key
   * @param val the value
   */
  public void setInteger(String key, Integer val) {
    setString(key, val == null ? null : val.toString());
  }


  /**
   * Gets a {@code Long}.
   *
   * @param key the preference key
   * @return the long or null if no such value
   */
  public Long getALong(String key) {
    String val = getString(key);
    return val == null ? null : Long.valueOf(val);
  }

  /**
   * Gets a long value.
   *
   * @param key the preference key
   * @param def the default value
   * @return the long value
   */
  public long getLong(String key, long def) {
    Long val = getALong(key);
    return val == null ? def : val;
  }

  /**
   * Sets a {@code Long}.<br>
   * If the value is the same as in system space, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preference key
   * @param val the value
   */
  public void setLong(String key, Long val) {
    setString(key, val == null ? null : val.toString());
  }



  /**
   * Gets a Float.
   *
   * @param key the preference key
   * @return the float or null if no such value
   */
  public Float getAFloat(String key) {
    String val = getString(key);
    return val == null ? null : Float.valueOf(val);
  }

  /**
   * Gets a float value.
   *
   * @param key the preference key
   * @param def the default value
   * @return the float value
   */
  public float getFloat(String key, float def) {
    Float val = getAFloat(key);
    return val == null ? def : val;
  }

  /**
   * Sets a Float.<br>
   * If the value is the same as in system space, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preference key
   * @param val the value
   */
  public void setFloat(String key, Float val) {
    setString(key, val == null ? null : val.toString());
  }



  /**
   * Gets a Double.
   *
   * @param key the preference key
   * @return the double or null if no such value
   */
  public Double getADouble(String key) {
    String val = getString(key);
    return val == null ? null : Double.valueOf(val);
  }

  /**
   * Gets a double value.
   *
   * @param key the preference key
   * @param def the default value
   * @return the double value
   */
  public double getDouble(String key, double def) {
    Double val = getADouble(key);
    return val == null ? def : val;
  }

  /**
   * Sets a Double.<br>
   * If the value is the same as in system space, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preference key
   * @param val the value
   */
  public void setDouble(String key, Double val) {
    setString(key, val == null ? null : val.toString());
  }



  /**
   * Gets a Boolean.
   *
   * @param key the preference key
   * @return the boolean or null if no such value
   */
  public Boolean getABoolean(String key) {
    String val = getString(key);
    return val == null ? null : Boolean.valueOf(val);
  }

  /**
   * Gets a boolean value.
   *
   * @param key the preference key
   * @param def the default value
   * @return the boolean value
   */
  public boolean getBoolean(String key, boolean def) {
    Boolean val = getABoolean(key);
    return val == null ? def : val;
  }

  /**
   * Sets a Boolean.<br>
   * If the value is the same as in system space, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preference key
   * @param val the value
   */
  public void setBoolean(String key, Boolean val) {
    setString(key, val == null ? null : val.toString());
  }


  /**
   * Gets a byte array from the preferences.
   *
   * @param key the preference key
   * @return the byte array or null if no such value
   */
  public byte[] getByteArray(String key) {
    byte[] val = null;
    if (!systemOnly) {
      val = getUserPrefs().getByteArray(key, null);
    }
    if (val == null) {
      val = getSystemPrefs().getByteArray(key, null);
    }
    return val;
  }

  /**
   * Gets a byte array from the preferences.
   *
   * @param key the preference key
   * @param def the default values
   * @return the byte array
   */
  public byte[] getByteArray(String key, byte[] def) {
    byte[] val = getByteArray(key);
    return val == null ? def : val;
  }


  /**
   * Sets a byte array.<br>
   * If the value is the same as in system space, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preference key
   * @param val the values
   */
  public void setByteArray(String key, byte[] val) {
    if (!systemOnly)  {
      if (val == null || areByteArraysEqual(val, getSystemPrefs().getByteArray(key, null))) {
        getUserPrefs().remove(key);
      }
      else  {
        getUserPrefs().putByteArray(key, val);
      }
    }
    else  {
      if (val == null)  {
        getSystemPrefs().remove(key);
      }
      else  {
        getSystemPrefs().putByteArray(key, val);
      }
    }
  }


  /**
   * Checks whether two byte arrays are equal.
   */
  private boolean areByteArraysEqual(byte[] b1, byte[] b2)  {
    if (b1 == null) {
      return b2 == null;
    }
    else if (b2 == null) {
      return false;
    }
    else  {
      if (b1.length == b2.length) {
        for (int i=0; i < b1.length; i++) {
          if (b1[i] != b2[i]) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }

}
