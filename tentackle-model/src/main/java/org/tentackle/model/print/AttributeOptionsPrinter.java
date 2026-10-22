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

package org.tentackle.model.print;

import org.tentackle.common.Constants;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeOptions;
import org.tentackle.model.EntityOptions;
import org.tentackle.model.ModelException;
import org.tentackle.model.impl.AttributeOptionsImpl;
import org.tentackle.model.impl.CommonOptionsImpl;
import org.tentackle.sql.DataType;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import static org.tentackle.common.Constants.BIND_MAXCOLS;
import static org.tentackle.common.Constants.BIND_SCALE;

/**
 * Pretty printer for attribute options.
 *
 * @author harald
 */
public class AttributeOptionsPrinter {

  private final Attribute attribute;
  private final PrintConfiguration configuration;


  /**
   * Creates a printer.
   *
   * @param attribute the attribute
   * @param configuration the printing configuration
   */
  public AttributeOptionsPrinter(Attribute attribute, PrintConfiguration configuration) {
    this.attribute = attribute;
    this.configuration = configuration;
  }

  /**
   * Prints the options string (without brackets).
   *
   * @return the option string
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public String print() {
    StringBuilder buf = new StringBuilder();
    EntityOptions entityOptions = attribute.getEntity().getOptions();
    AttributeOptions options = attribute.getOptions();

    buf.append(printBindOptions());

    if (attribute.getDataType().isNumeric() || attribute.getDataType().isDateOrTime()) {
      printBool(buf, Constants.BIND_AUTOSELECT, options.isAutoSelect(), entityOptions.isAutoSelect());
    }
    printBool(buf, CommonOptionsImpl.OPTION_BIND, options.isBindable(), entityOptions.isBindable());
    printBool(buf, CommonOptionsImpl.OPTION_NODECLARE, options.isNoDeclare(), entityOptions.isNoDeclare());
    printBool(buf, CommonOptionsImpl.OPTION_NOMETHOD, options.isNoMethod(), entityOptions.isNoMethod());
    printBool(buf, CommonOptionsImpl.OPTION_NOCONSTANT, options.isNoConstant(), entityOptions.isNoConstant());
    printBool(buf, CommonOptionsImpl.OPTION_MUTE, options.isMute(), entityOptions.isMute());
    printBool(buf, CommonOptionsImpl.OPTION_SUPER, options.isFromSuper(), entityOptions.isFromSuper());
    printBool(buf, CommonOptionsImpl.OPTION_READONLY, options.isReadOnly(), entityOptions.isReadOnly());
    printBool(buf, CommonOptionsImpl.OPTION_WRITEONLY, options.isWriteOnly(), entityOptions.isWriteOnly());
    printBool(buf, CommonOptionsImpl.OPTION_MAPNULL, options.isMapNull(), entityOptions.isMapNull());

    if (options.isTrimRead() == options.isTrimWrite() &&
        entityOptions.isTrimRead() == entityOptions.isTrimWrite()) {
      printBool(buf, CommonOptionsImpl.OPTION_TRIM, options.isTrimRead(), entityOptions.isTrimRead());
    }
    else {
      printBool(buf, CommonOptionsImpl.OPTION_TRIM_READ, options.isTrimRead(), entityOptions.isTrimRead());
      printBool(buf, CommonOptionsImpl.OPTION_TRIM_WRITE, options.isTrimWrite(), entityOptions.isTrimWrite());
    }

    if (options.getAccessScope() != entityOptions.getAccessScope()) {
      buf.append(options.getAccessScope()).append(',');
    }

    printBool(buf, AttributeOptionsImpl.OPTION_CONTEXT, options.isContextId());
    printBool(buf, AttributeOptionsImpl.OPTION_DOMAINKEY, options.isDomainKey());
    printBool(buf, AttributeOptionsImpl.OPTION_TZ, options.isWithTimezone());
    printBool(buf, AttributeOptionsImpl.OPTION_HIDDEN, options.isHidden());
    printBool(buf, AttributeOptionsImpl.OPTION_SHALLOW, options.isShallow());
    printBool(buf, AttributeOptionsImpl.OPTION_NORMTEXT, options.isPartOfNormText());

    if (options.getDefaultValue() != null) {
      buf.append(AttributeOptionsImpl.OPTION_DEFAULT).append(' ');
      try {
        buf.append(((DataType) attribute.getEffectiveDataType()).toString(options.getDefaultValue()));
      }
      catch (ModelException mx) {
        buf.append("?").append(mx.getMessage()).append("?");
      }
      buf.append(',');
    }

    if (options.getInitialValue() != null) {
      buf.append(AttributeOptionsImpl.OPTION_INIT).append(' ').append(options.getInitialValue()).append(',');
    }

    if (options.getNewValue() != null) {
      buf.append(AttributeOptionsImpl.OPTION_NEW).append(' ').append(options.getNewValue()).append(',');
    }

    for (String anno: options.getAnnotations()) {
      if (configuration.isOptionAnnotation(anno)) {
        buf.append(new AnnotationPrinter(anno).print()).append(',');
      }
    }
    for (String stereotype: options.getStereotypes()) {
      buf.append('#').append(stereotype).append(',');
    }

    if (!buf.isEmpty() && buf.charAt(buf.length() - 1) == ',') {
      buf.setLength(buf.length() - 1);
    }

    return buf.toString();
  }


  /**
   * Prints the bind options.<br>
   * Options already defined for the entity are omitted, as well as MAXCOLS and SCALE
   * if they correspond to the attribute's size and scale.
   *
   * @return the bind option string
   */
  private String printBindOptions() {
    Set<String> entityOptionSet = new HashSet<>();
    Set<String> attributeOptionSet = new HashSet<>();
    String entityOptions = attribute.getEntity().getOptions().getBindOptions();
    if (entityOptions != null && !entityOptions.isEmpty()) {
      StringTokenizer stok = new StringTokenizer(entityOptions, ",");
      while (stok.hasMoreTokens()) {
        entityOptionSet.add(stok.nextToken());
      }
    }
    String attributeOptions = attribute.getOptions().getBindOptions();
    if (attributeOptions != null && !attributeOptions.isEmpty()) {
      StringTokenizer stok = new StringTokenizer(attributeOptions, ",");
      while (stok.hasMoreTokens()) {
        String option = stok.nextToken();
        if (option.startsWith(BIND_MAXCOLS)) {
          int maxCol = Integer.parseInt(option.substring(BIND_MAXCOLS.length() + 1));
          if (attribute.getSize() != null && attribute.getSize() == maxCol) {
            continue;
          }
        }
        if (option.startsWith(BIND_SCALE)) {
          int scale = Integer.parseInt(option.substring(BIND_SCALE.length() + 1));
          if (attribute.getScale() != null && attribute.getScale() == scale) {
            continue;
          }
        }
        attributeOptionSet.add(option);
      }
    }
    StringBuilder buf = new StringBuilder();
    for (String option: attributeOptionSet) {
      if (!entityOptionSet.contains(option)) {
        buf.append(option).append(',');
      }
    }
    return buf.toString();
  }


  /**
   * Prints a boolean option according to the entity global option.
   *
   * @param buf the builder
   * @param name the option's name
   * @param optionValue the attribute option's value
   * @param entityValue the entity option's value
   */
  private void printBool(StringBuilder buf, String name, boolean optionValue, boolean entityValue) {
    if (optionValue != entityValue) {
      if (!optionValue) {
        buf.append('!');
      }
      buf.append(name).append(',');
    }
  }

  /**
   * Prints a boolean option that has no entity-level counterpart.
   *
   * @param buf the builder
   * @param name the option's name
   * @param optionValue the attribute option's value
   */
  private void printBool(StringBuilder buf, String name, boolean optionValue) {
    printBool(buf, name, optionValue, false);
  }

}
