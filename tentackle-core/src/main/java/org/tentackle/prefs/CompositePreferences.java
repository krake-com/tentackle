/*
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

import java.util.Objects;
import java.util.prefs.BackingStoreException;



/**
 * User- and System-Preferences combined.<br>
 *
 * Implements Preferences in such a way that
 * user-space overwrites system-space which is the default
 * for most applications.
 * Furthermore, some helper methods allow exporting and importing
 * with file-dialogs, etc...
 * <p>
 * This class is abstract because it should be extended by
 * the application to get its own namespace (by classname).
 *
 * @author harald
 */
public abstract class CompositePreferences {


  private final PersistedPreferences userPrefs;        // preferences for current user
  private final PersistedPreferences systemPrefs;      // system default prefs


  /**
   * Creates composite preferences.
   *
   * @param systemOnly true if refer to system-preferences only, false = user space overwrites system space
   */
  public CompositePreferences(boolean systemOnly) {
    systemPrefs = PersistedPreferencesFactory.getInstance().systemNodeForPackage(this.getClass());
    userPrefs = systemOnly ? null : PersistedPreferencesFactory.getInstance().userNodeForPackage(this.getClass());
  }


  /**
   * Gets the user peferences.
   *
   * @return the user-scope preferences, null if systemOnly
   */
  public PersistedPreferences getUserPrefs()  {
    return userPrefs;
  }

  /**
   * Gets the system preferences.
   *
   * @return the system-scope preferences
   */
  public PersistedPreferences getSystemPrefs()  {
    return systemPrefs;
  }


  /**
   * Flushes the preferences (system and user).
   *
   * @throws BackingStoreException if failed
   */
  public void flush() throws BackingStoreException {
    if (userPrefs != null) {
      userPrefs.flush();
    }
    systemPrefs.flush();
  }


  /**
   * Syncs the preferences (system and user).
   *
   * @throws BackingStoreException if failed
   */
  public void sync() throws BackingStoreException {
    if (userPrefs != null) {
      userPrefs.sync();
    }
    systemPrefs.sync();
  }


  /**
   * Gets the value for a key.
   * Userspace overwrites systemspace.
   *
   * @param key the preferences key
   * @return the value string or null if no such value
   */
  public String getString(String key) {
    String val = null;
    if (userPrefs != null) {
      val = userPrefs.get(key, null);
    }
    if (val == null) {
      val = systemPrefs.get(key, null);
    }
    return val;
  }

  /**
   * Gets the value for a key.
   * Userspace overwrites systemspace.
   *
   * @param key the preferences key
   * @param def the default value
   * @return the value string
   */
  public String getString(String key, String def) {
    String val = getString(key);
    return val == null ? def : val;
  }

  /**
   * Sets the value for a key.<br>
   * If the value is the same as in systemspace, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preferences key
   * @param val the value
   */
  public void setString(String key, String val) {
    if (userPrefs != null)  {
      if (val == null || Objects.equals(val, systemPrefs.get(key, null))) {
        userPrefs.remove(key);
      }
      else  {
        userPrefs.put(key, val);
      }
    }
    else  {
      if (val == null)  {
        systemPrefs.remove(key);
      }
      else  {
        systemPrefs.put(key, val);
      }
    }
  }


  /**
   * Gets an Integer.
   *
   * @param key the preferences key
   * @return the integer or null if no such value
   */
  public Integer getInteger(String key) {
    String val = getString(key);
    return val == null ? null : Integer.valueOf(val);
  }

  /**
   * Gets an int value.
   *
   * @param key the preferences key
   * @param def the default value
   * @return the integer
   */
  public int getInt(String key, int def) {
    Integer val = getInteger(key);
    return val == null ? def : val;
  }

  /**
   * Sets an integer.<br>
   * If the value is the same as in systemspace, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preferences key
   * @param val the value
   */
  public void setInteger(String key, Integer val) {
    setString(key, val == null ? null : val.toString());
  }


  /**
   * Gets a Long.
   *
   * @param key the preferences key
   * @return the integer or null if no such value
   */
  public Long getALong(String key) {
    String val = getString(key);
    return val == null ? null : Long.valueOf(val);
  }

  /**
   * Gets a long value.
   *
   * @param key the preferences key
   * @param def the default value
   * @return the integer
   */
  public long getLong(String key, long def) {
    Long val = getALong(key);
    return val == null ? def : val;
  }

  /**
   * Sets a Long.<br>
   * If the value is the same as in systemspace, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preferences key
   * @param val the value
   */
  public void setLong(String key, Long val) {
    setString(key, val == null ? null : val.toString());
  }



  /**
   * Gets a Float.
   *
   * @param key the preferences key
   * @return the integer or null if no such value
   */
  public Float getAFloat(String key) {
    String val = getString(key);
    return val == null ? null : Float.valueOf(val);
  }

  /**
   * Gets a float value.
   *
   * @param key the preferences key
   * @param def the default value
   * @return the integer
   */
  public float getFloat(String key, float def) {
    Float val = getAFloat(key);
    return val == null ? def : val;
  }

  /**
   * Sets a Float.<br>
   * If the value is the same as in systemspace, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preferences key
   * @param val the value
   */
  public void setFloat(String key, Float val) {
    setString(key, val == null ? null : val.toString());
  }



  /**
   * Gets a Double.
   *
   * @param key the preferences key
   * @return the integer or null if no such value
   */
  public Double getADouble(String key) {
    String val = getString(key);
    return val == null ? null : Double.valueOf(val);
  }

  /**
   * Gets a double value.
   *
   * @param key the preferences key
   * @param def the default value
   * @return the integer
   */
  public double getDouble(String key, double def) {
    Double val = getADouble(key);
    return val == null ? def : val;
  }

  /**
   * Sets a Double.<br>
   * If the value is the same as in systemspace, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preferences key
   * @param val the value
   */
  public void setDouble(String key, Double val) {
    setString(key, val == null ? null : val.toString());
  }



  /**
   * Gets a Boolean.
   *
   * @param key the preferences key
   * @return the integer or null if no such value
   */
  public Boolean getABoolean(String key) {
    String val = getString(key);
    return val == null ? null : Boolean.valueOf(val);
  }

  /**
   * Gets a boolean value.
   *
   * @param key the preferences key
   * @param def the default value
   * @return the integer
   */
  public boolean getBoolean(String key, boolean def) {
    Boolean val = getABoolean(key);
    return val == null ? def : val;
  }

  /**
   * Sets a Boolean.<br>
   * If the value is the same as in systemspace, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preferences key
   * @param val the value
   */
  public void setBoolean(String key, Boolean val) {
    setString(key, val == null ? null : val.toString());
  }


  /**
   * Gets a bytearray from the preferences.
   *
   * @param key the preferences key
   * @return the byte array or null if no such value
   */
  public byte[] getByteArray(String key) {
    byte[] val = null;
    if (userPrefs != null) {
      val = userPrefs.getByteArray(key, null);
    }
    if (val == null) {
      val = systemPrefs.getByteArray(key, null);
    }
    return val;
  }

  /**
   * Gets a bytearray from the preferences.
   *
   * @param key the preferences key
   * @param def the default values
   * @return the byte array
   */
  public byte[] getByteArray(String key, byte[] def) {
    byte[] val = getByteArray(key);
    return val == null ? def : val;
  }


  /**
   * Sets a bytearray.<br>
   * If the value is the same as in systemspace, the user value is removed.
   * Null-values also perform a remove.
   *
   * @param key the preferences key
   * @param val the values
   */
  public void setByteArray(String key, byte[] val) {
    if (userPrefs != null)  {
      if (val == null || areByteArraysEqual(val, systemPrefs.getByteArray(key, null))) {
        userPrefs.remove(key);
      }
      else  {
        userPrefs.putByteArray(key, val);
      }
    }
    else  {
      if (val == null)  {
        systemPrefs.remove(key);
      }
      else  {
        systemPrefs.putByteArray(key, val);
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
