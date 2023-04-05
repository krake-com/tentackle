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
import java.util.TreeMap;

/**
 * Command line arguments and options.
 * <p>
 * Scans an array of argument strings and parses options.
 * Options start with two dashes, for example {@code --enabled}
 * and can have a value like {@code --timeout=30}.
 * All other strings are considered as standard args.
 *
 * @author harald
 */
public class CommandLine {

  /** the original startup arguments. */
  private String[] args;

  /** non-option args. */
  private final List<String> arguments = new ArrayList<>();

  /** option args (--opt or --opt=val, each opt can be given only once, last one overrides). */
  private final Map<String,String> options = new TreeMap<>();


  /**
   * Creates a command line from console args.
   *
   * @param args the command line args (usually from main-method)
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
   * Gets the arguments.
   *
   * @return the value from setArgs
   */
  public String[] getArgs() {
    return args;
  }

  /**
   * Sets the arguments.
   *
   * @param args the arguments (usually from commandline)
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
   * @return the value or null if no such option or not a value option
   */
  public String getOptionValue(String option) {
    return options.get(option);
  }


  /**
   * Gets the list of non-option arguments
   *
   * @return the arguments. never null
   */
  public List<String> getArguments() {
    return arguments;
  }


  /**
   * Gets the options.
   *
   * @return the options map, never null
   */
  public Map<String,String> getOptions() {
    return options;
  }


  /**
   * Gets the options as properties.
   *
   * @return the options, never null
   */
  public EncryptedProperties getOptionsAsProperties() {
    EncryptedProperties props = new EncryptedProperties();
    for (Map.Entry<String,String> entry: options.entrySet()) {
      String value = entry.getValue();
      if (value == null)  {
        value = "";
      }
      props.setProperty(entry.getKey(), value);
    }
    return props;
  }

}
