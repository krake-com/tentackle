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

package org.tentackle.misc;

import org.tentackle.common.EncryptedProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Command line arguments and options.
 * <p>
 * Scans an array of argument strings and parses options.
 * Options start with two dashes, for example {@code --enabled},
 * and can have a value like {@code --timeout=30}.
 * If an option is given more than once, the last one wins.
 * All other strings are considered as standard args. Null strings are ignored.
 *
 * @author harald
 */
public class CommandLine {

  /** the original startup arguments. */
  private String[] args;

  /** non-option args. */
  private final List<String> arguments = new ArrayList<>();

  /** option args (--opt or --opt=val, if given more than once, the last one wins). */
  private final Map<String,String> options = new TreeMap<>();


  /**
   * Creates a command line from console args.
   *
   * @param args the command line args (usually from the main method), null if none
   */
  public CommandLine(String[] args) {
    setArgs(args);
  }

  /**
   * Creates an empty command line.
   */
  public CommandLine() {
    this(null);
  }

  /**
   * Gets the original arguments.
   *
   * @return the value from {@link #setArgs(String[])}
   */
  public String[] getArgs() {
    return args;
  }

  /**
   * Sets the arguments.
   *
   * @param args the arguments (usually from the command line), null if none
   */
  public void setArgs(String[] args) {
    this.args = args;
    arguments.clear();
    options.clear();
    if (args != null) {
      for (String arg: args) {
        if (arg != null) {
          if (arg.startsWith("--")) {   // NOI18N
            // Option
            String option = arg.substring(2);
            String value  = null;
            int ndx = option.indexOf('=');
            if (ndx > 0) {
              value  = option.substring(ndx + 1);
              option = option.substring(0, ndx);
            }
            options.put(option, value);
          }
          else  {
            arguments.add(arg);
          }
        }
      }
    }
  }


  /**
   * Checks if an option has been set or not.
   *
   * @param option the option name (without the leading --)
   * @return true if option set
   */
  public boolean isOptionSet(String option) {
    return options.containsKey(option);
  }


  /**
   * Gets the value for an option.
   *
   * @param option the option name (without the leading --)
   * @return the value, null if no such option or not a value option
   */
  public String getOptionValue(String option) {
    return options.get(option);
  }


  /**
   * Gets the list of non-option arguments.
   *
   * @return the arguments, never null
   */
  public List<String> getArguments() {
    return arguments;
  }


  /**
   * Gets the options.
   *
   * @return the options map sorted by option name, never null. Options without a value map to null.
   */
  public Map<String,String> getOptions() {
    return options;
  }


  /**
   * Gets the options as properties.<br>
   * Options without a value map to the empty string.
   *
   * @return the options, never null
   */
  public EncryptedProperties getOptionsAsProperties() {
    Set<Map.Entry<String, String>> entries = options.entrySet();
    EncryptedProperties props = PropertiesUtilities.getInstance().create(entries.size() + 1);
    for (Map.Entry<String,String> entry: entries) {
      String value = entry.getValue();
      if (value == null)  {
        value = "";
      }
      props.setProperty(entry.getKey(), value);
    }
    return props;
  }

}
