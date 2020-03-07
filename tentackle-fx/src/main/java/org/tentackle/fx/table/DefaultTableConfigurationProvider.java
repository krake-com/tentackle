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

package org.tentackle.fx.table;

import org.tentackle.bind.BindableElement;
import org.tentackle.common.BundleFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxBindingFactory;
import org.tentackle.reflect.ReflectionHelper;

import java.util.Collection;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Base implementation of a {@link TableConfigurationProvider}.
 * <p>
 * The default implementation will add all bindable elements of the serviced class.<br>
 * Override {@link #createTableConfiguration()} to customize.<br>
 * If the class contains components and their bindables should be added instead, override
 * {@link #addBindableColumns(TableConfiguration, String...)}. Example:
 * <pre>
 * &#64;TableConfigurationProviderService(value = TokenLockInfoDTO.class, noBundle = true)
 * public class TokenLockInfoTableConfiguration extends DefaultTableConfigurationProvider&lt;TokenLockInfoDTO&gt; {
 *
 *   &#64;Override
 *   protected void addBindableColumns(TableConfiguration&lt;TokenLockInfoDTO&gt; configuration, String... components) {
 *     super.addBindableColumns(configuration, "pdoKey", "editedBy");
 *   }
 * }
 * </pre>
 *
 * @param <T> the table elements type
 * @author harald
 */
public class DefaultTableConfigurationProvider<T> implements TableConfigurationProvider<T> {

  private final Class<T> clazz;                             // the table elements class
  private final boolean bundleProvided;                     // true if resource bundle is provided
  private final TableConfiguration.BINDING bindingType;     // the table binding type

  private ResourceBundle bundle;                            // cached resource bundle


  /**
   * Creates a table configuration provider.
   */
  @SuppressWarnings("unchecked")
  public DefaultTableConfigurationProvider() {
    TableConfigurationProviderService annotation = getClass().getAnnotation(TableConfigurationProviderService.class);
    if (annotation == null) {
      if (getClass() != DefaultTableConfigurationProvider.class) {
        throw new FxRuntimeException(getClass() + " is not annotated with @" + ReflectionHelper.getClassBaseName(TableConfigurationProviderService.class));
      }
      // see DefaultTableConfigurationProviderFactory
      clazz = (Class<T>) Object.class;
      bundleProvided = false;
      bindingType = TableConfiguration.BINDING.NO;
    }
    else {
      clazz = (Class<T>) annotation.value();
      bundleProvided = !annotation.noBundle();
      bindingType = annotation.binding();
    }
  }

  /**
   * Returns whether a resource bundle is provided.
   *
   * @return true if provided, false if no bundle
   */
  public boolean isBundleProvided() {
    return bundleProvided;
  }

  /**
   * Gets the resource bundle for this provider.
   * <p>
   * Throws {@link java.util.MissingResourceException} if no bundle found.
   *
   * @return the resource bundle
   */
  public ResourceBundle getBundle() {
    if (bundle == null) {
      bundle = BundleFactory.getBundle(getClass().getName());
    }
    return bundle;
  }

  /**
   * Creates the table configuration.<br>
   * The default implementation adds all bindable elements and invokes {@link #addBindableColumns(TableConfiguration, String...)}
   * without optional components.
   *
   * @return the table configuration
   */
  @Override
  public TableConfiguration<T> createTableConfiguration() {
    TableConfiguration<T> configuration = FxFactory.getInstance().createTableConfiguration(clazz, null);
    addBindableColumns(configuration);
    if (bindingType != null) {
      configuration.setBindingType(bindingType);
    }
    return configuration;
  }

  /**
   * Adds all bindable columns.<br>
   * Override this method to specify the optional components.
   *
   * @param configuration the table configuration
   * @param components the binding path of components that should in turn be analyzed for bindables
   */
  protected void addBindableColumns(TableConfiguration<T> configuration, String... components) {
    addBindableColumns(configuration, "", "", clazz, components);

    // try to shorten dotted names, if the remaining tail is unique among all columns
    Collection<TableColumnConfiguration<T, ?>> columnConfigurations = configuration.getColumnConfigurations();
    for (TableColumnConfiguration column : columnConfigurations) {
      String displayedName = column.getDisplayedName();
      if (displayedName != null) {
        int ndx = displayedName.lastIndexOf('.');
        if (ndx >= 0) {
          String shortName = displayedName.substring(ndx + 1);
          if (!StringHelper.isAllWhitespace(shortName)) {
            boolean shorten = true;
            for (TableColumnConfiguration otherColumn : columnConfigurations) {
              if (otherColumn != column) {
                String otherName = otherColumn.getDisplayedName();
                if (displayedName.equals(otherName) || shortName.equals(otherName)) {
                  shorten = false;
                  break;
                }
              }
            }
            if (shorten) {
              column.setDisplayedName(shortName);
            }
          }
        }
      }
    }
  }

  /**
   * Recursively adds all bindable columns including components.
   *
   * @param configuration the table configuration
   * @param columnPrefix the prefix for the column names
   * @param displayPrefix the prefix for the display names
   * @param clazz the class to analyze for bindable elements
   * @param components the binding path of components that should in turn be analyzed for bindables
   */
  protected void addBindableColumns(TableConfiguration<T> configuration, String columnPrefix, String displayPrefix,
                                    Class<?> clazz, String... components) {

    if (bindingType != null && bindingType != TableConfiguration.BINDING.NO) {
      for (Map.Entry<String, BindableElement> entry :
           FxBindingFactory.getInstance().getBindableCache().getBindableMap(clazz, bindingType == TableConfiguration.BINDING.YES).entrySet()) {

        String bindName = entry.getKey();
        BindableElement element = entry.getValue();

        String columnName = columnPrefix + StringHelper.firstToLower(bindName);
        String displayName = displayPrefix + (isBundleProvided() ? getBundle().getString(bindName) : bindName);
        boolean isComponent = false;
        if (components != null) {
          for (String component: components) {
            if (columnName.equals(component)) {
              isComponent = true;
              break;
            }
          }
        }
        if (isComponent) {
          addBindableColumns(configuration, columnName + ".", displayName + ".", element.getType());
        }
        else {
          configuration.addColumn(columnName, displayName);
        }
      }
    }
  }

}
