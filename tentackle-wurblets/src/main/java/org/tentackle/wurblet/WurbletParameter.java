/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.wurblet;

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Relation;
import org.tentackle.sql.Backend;

/**
 * A wurblet parameter.
 * <p>
 * Holds select, update/set and sorting parameters.
 *
 * @author harald
 */
public class WurbletParameter implements WurbletParameterOperand {

  /** the entity the wurblet is applied to. */
  private final Entity entity;

  /** the position among all parameters. */
  private final int index;

  /** the original text. */
  private final String text;

  /** name of the database attribute. */
  private String name;

  /** true if this is a component key. */
  private final boolean isComponentKey;

  /** true if this is a relation key. */
  private final boolean isRelationKey;

  /** holds the component of the entity (empty == this, null = no component or relation key). */
  private final String componentEntityName;

  /** != null if name describes a relation key. */
  private final String relationName;

  /** != null if component if relation within a relation key. */
  private final String relationComponentEntityName;

  /** name of the method argument. */
  private String arg;

  /** relational operator. */
  private String relop;

  /** predefined value (replaces the ?). */
  private String value;

  /** fixed value (directly put into sql-statement). */
  private boolean fixed;

  /** != (ASC, DESC) null if this is a sorting parameter. */
  private String sortMode;

  /** the model attribute. */
  private Attribute attribute;

  /** the relation if a relation key. */
  private Relation relation;


  /**
   * Constructs a parameter from a wurblet arg.<p>
   *
   * A parameter is of the form:<br>
   * [+-][&lt;attribute-path&gt;]<tt>&lt;attribute-name&gt;[[argname]][:relop[:value]|[#value]]</tt><br>
   * where relop is optional and defaults to '='.<br>
   * The special relop :null will be translated to " IS NULL" and :notnull translated to " IS NOT NULL".
   * "like" will translated to " LIKE ".
   * "notlike" will be translated to " NOT LIKE ".
   * <p>
   * A leading + or - denotes an order by field.
   * + means ascending, - means descending.
   * Such a parameter must not be followed by operators.
   * <p>
   * An optional leading entity name followed by a dot and the attribute denotes a
   * so-called component-key. This is an attribute of a component of the current entity.
   * The current entity must be a root-entity.
   *
   *
   * <pre>
   * Examples:
   *          poolId            -&gt; name=poolId, arg=poolId, relop="="
   *          poolId[from]:&gt;=   -&gt; name=poolId, arg=from, relop="&gt;="
   *          code:=:'Hurrz'    -&gt; name=code, arg=code, relop="=", value="'Hurz'"
   *          code:=#'Hurrz'    -&gt; name=code, arg=code, relop="=", value="'Hurz'", fixed=true
   *
   *          +poolId -kurzname -&gt; ORDER BY poolId ASC, kurzname DESC
   * </pre>
   *
   * The <code>&lt;attribute-path&gt;</code> describes the path to the attribute.<br>
   * The persistence layer is able to reference any component of a PDO by only one join
   * (resp. two if multi-class inheritance), regardless how deep it is located within
   * the component tree. This is possible due to the optional columns rootId and rootClassId.<br>
   * Non-components, i.e. simple relations, can also be used. However, those are limited to
   * the direct relations to the entity, i.e. max. one join.<br>
   * Futhermore, all components of such a non-composite relations can also be referenced.<br>
   * As a result, a join chain may consist of an optional component, followed by an optional
   * non-composite relation, followed by an optional component of that relation.
   *
   * <p>
   * The path is of the following form:
   * <ul>
   * <li><code>&lt;component-entity&gt;.</code><br>
   * The entity name of one of the PDO's components. May be any component in the composite's hierarchy.<br>
   * Parameter example:<br>
   * <code>InvoiceLine.amount</code>
   * </li>
   * <li>
   * <code>[&lt;component-entity&gt;].&lt;relation&gt;.[&lt;component-entity&gt;.]</code><br>
   * The relation to the entity of the attribute. If the relation is defined in a component of the PDO, the component
   * defining the relation needs to be prepended.<br>
   * If the attribute is part of a component of the related entity, it's component needs to be appended to the relation.<br>
   * Parameter examples:<br>
   * <code>InvoiceLine.Product.price</code><br>
   * <code>.User.Address.zipCode</code>
   * </li>
   * <li>
   * <code>.</code><br>
   * For convenience, a leading dot is equivalent to an attribute-name without a dot.<br>
   * Parameter example:<br>
   * <code>.poolId</code>
   * </li>
   * </ul>
   *
   * @param entity the entity the wurblet is applied to
   * @param index the position among all parameters
   * @param text the wurblet arg
   */
  public WurbletParameter(Entity entity, int index, String text) {
    this.entity = entity;
    this.index = index;
    this.text = text;

    int ndx = text.indexOf(':');
    if (ndx > 0) {
      name = text.substring(0, ndx);
      relop = text.substring(ndx + 1);

      ndx = relop.indexOf(':');
      if (ndx > 0) {
        value = relop.substring(ndx + 1);
        relop = relop.substring(0, ndx);
      }
      else {
        ndx = relop.indexOf('#');
        if (ndx > 0) {
          value = relop.substring(ndx + 1);
          relop = relop.substring(0, ndx);
          fixed = true;
        }
      }

      switch (relop.toUpperCase()) {
        case "NULL":
          relop = Backend.SQL_ISNULL;
          value = "";
          fixed = true;
          break;
        case "NOTNULL":
          relop = Backend.SQL_ISNOTNULL;
          value = "";
          fixed = true;
          break;
        case "LIKE":
          relop = Backend.SQL_LIKE;
          break;
        case "NOTLIKE":
          relop = Backend.SQL_NOTLIKE;
          break;
      }
    }
    else {
      name = text;
      switch (name.charAt(0)) {
        case '-':
          sortMode = Backend.SQL_SORTDESC;
          name = name.substring(1);
          break;
        case '+':
          sortMode = Backend.SQL_SORTASC;
          name = name.substring(1);
          break;
        default:
          relop = Backend.SQL_EQUAL;
          break;
      }
    }

    ndx = name.indexOf('.');
    if (ndx >= 0) {
      componentEntityName = name.substring(0, ndx);
      name = name.substring(ndx + 1);
      ndx = name.indexOf('.');
      if (ndx > 0) {
        // multi dot -> relation key
        isComponentKey = false;
        isRelationKey = true;
        relationName = name.substring(0, ndx);
        name = name.substring(ndx + 1);
        ndx = name.indexOf('.');
        if (ndx > 0) {
          relationComponentEntityName = name.substring(0, ndx);
          name = name.substring(ndx + 1);
        }
        else  {
          relationComponentEntityName = null;
        }
      }
      else  {
        // single dot -> component key
        isRelationKey = false;
        relationName = null;
        relationComponentEntityName = null;
        isComponentKey = !componentEntityName.isEmpty(); // .<attribute>  -> ignore obsolete dot
      }
    }
    else  {
      relationName = null;
      componentEntityName = null;
      relationComponentEntityName = null;
      isComponentKey = false;
      isRelationKey = false;
    }

    arg = name; // default
    ndx = name.indexOf('[');
    if (ndx > 0) {
      int ndx2 = name.indexOf(']');
      if (ndx2 > ndx) {
        arg = name.substring(ndx + 1, ndx2);
        name = name.substring(0, ndx);
      }
    }
  }


  /**
   * Gets the entity the wurblet is applied to.
   *
   * @return the entity
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the index within the wurblet anchor.
   *
   * @return the index, lower comes first
   */
  public int getIndex() {
    return index;
  }

  /**
   * Returns whether this is a component key.
   *
   * @return true if component key
   */
  public boolean isComponentKey() {
    return isComponentKey;
  }

  /**
   * Returns whether this is a relation key.
   *
   * @return true if relation key
   */
  public boolean isRelationKey() {
    return isRelationKey;
  }

  /**
   * Gets the entity name of the component key.
   *
   * @return the entity, null if this is not a component key
   */
  public String getComponentEntityName() {
    return componentEntityName;
  }

  /**
   * Gets the component of the related entity pointed to by the relation key.
   *
   * @return the component, null if direct attribute of related entity
   */
  public String getRelationComponentEntityName() {
    return relationComponentEntityName;
  }

  /**
   * Gets the relation of the relation key.
   *
   * @return the relation
   */
  public String getRelationName() {
    return relationName;
  }


  /**
   * Returns true if this is a sorting parameter.
   *
   * @return true if sorting parameter
   */
  public boolean isSortKey() {
    return sortMode != null;
  }

  /**
   * Returns the sortmode if !isKey().
   * @return the sortmode (either {@link Backend#SQL_SORTASC} or {@link Backend#SQL_SORTDESC}) or null if isKey
   */
  public String getSortMode() {
    return sortMode;
  }

  /**
   * Sets the sortmode.
   *
   * @param sortMode either {@link Backend#SQL_SORTASC} or {@link Backend#SQL_SORTDESC}
   */
  public void setSortMode(String sortMode) {
    this.sortMode = sortMode;
  }

  /**
   * @return true if its a method parameter
   */
  public boolean isArg() {
    return value == null;
  }

  /**
   * @return true if we need an sql arg
   */
  public boolean isSqlArg() {
    return !fixed;
  }

  /**
   * @return the sql argument or the value
   */
  public String getSqlArg() {
    String str;
    if (value != null) {
      str = value;
      if (attribute != null && attribute.getDataType().isNumeric()) {
        // check to downcast for (short) or (byte)
        switch (attribute.getDataType()) {
          case BYTE:
            str = "(Byte) " + str;
            break;
          case BYTE_PRIMITIVE:
            str = "(byte) " + str;
            break;
          case SHORT:
            str = "(Short) " + str;
            break;
          case SHORT_PRIMITIVE:
            str = "(short) " + str;
            break;
        }
      }
    }
    else  {
      str = arg;
    }
    return str;
  }

  /**
   * Gets the relop string.<br>
   * Usually "=?" or " IS NULL" or "=value"
   * @return the relop string
   */
  public String getRelop() {

    String str;

    // translate to predefined strings, if possible
    switch(relop) {
      case Backend.SQL_EQUAL:
        if (fixed) {
          str = "Backend.SQL_EQUAL + " + value;
        }
        else  {
          str = "Backend.SQL_EQUAL_PAR";
        }
        break;

      case Backend.SQL_NOTEQUAL:
      case "!=":
        if (fixed) {
          str = "Backend.SQL_NOTEQUAL + " + value;
        }
        else  {
          str = "Backend.SQL_NOTEQUAL_PAR";
        }
        break;

      case Backend.SQL_LESS:
        if (fixed) {
          str = "Backend.SQL_LESS + " + value;
        }
        else  {
          str = "Backend.SQL_LESS_PAR";
        }
        break;

      case Backend.SQL_GREATER:
        if (fixed) {
          str = "Backend.SQL_GREATER + " + value;
        }
        else  {
          str = "Backend.SQL_GREATER_PAR";
        }
        break;

      case Backend.SQL_LESSOREQUAL:
        if (fixed) {
          str = "Backend.SQL_LESSOREQUAL + " + value;
        }
        else  {
          str = "Backend.SQL_LESSOREQUAL_PAR";
        }
        break;

      case Backend.SQL_GREATEROREQUAL:
        if (fixed) {
          str = "Backend.SQL_GREATEROREQUAL + " + value;
        }
        else  {
          str = "Backend.SQL_GREATEROREQUAL_PAR";
        }
        break;

      case Backend.SQL_LIKE:
        if (fixed) {
          str = "Backend.SQL_LIKE + " + value;
        }
        else  {
          str = "Backend.SQL_LIKE_PAR";
        }
        break;

      case Backend.SQL_NOTLIKE:
        if (fixed) {
          str = "Backend.SQL_NOTLIKE + " + value;
        }
        else  {
          str = "Backend.SQL_NOTLIKE_PAR";
        }
        break;

      case Backend.SQL_ISNULL:
        str = "Backend.SQL_ISNULL";
        break;

      case Backend.SQL_ISNOTNULL:
        str = "Backend.SQL_ISNOTNULL";
        break;

      default:
        str = relop + (fixed ? value : "?");
    }

    return str;
  }

  /**
   * Gets the original text.
   *
   * @return the text
   */
  public String getText() {
    return text;
  }

  /**
   * name of the database attribute.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * name of the database attribute.
   *
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * name of the method argument.
   * @return the arg
   */
  public String getArg() {
    return arg;
  }

  /**
   * name of the method argument.
   * @param arg the arg to set
   */
  public void setArg(String arg) {
    this.arg = arg;
  }

  /**
   * relational operator.
   * @param relop the relop to set
   */
  public void setRelop(String relop) {
    this.relop = relop;
  }

  /**
   * predefined value (replaces the ?).
   * @return the value
   */
  public String getValue() {
    return value;
  }

  /**
   * predefined value (replaces the ?).
   * @param value the value to set
   */
  public void setValue(String value) {
    this.value = value;
  }

  /**
   * fixed value (directly put into sql-statement).
   * @return the fixed
   */
  public boolean isFixed() {
    return fixed;
  }

  /**
   * fixed value (directly put into sql-statement).
   * @param fixed the fixed to set
   */
  public void setFixed(boolean fixed) {
    this.fixed = fixed;
  }

  /**
   * the model attribute.
   * @return the attribute
   */
  public Attribute getAttribute() {
    return attribute;
  }

  /**
   * the model attribute.
   * @param attribute the attribute to set
   */
  public void setAttribute(Attribute attribute) {
    this.attribute = attribute;
  }

  /**
   * Gets the relation if this is a relation key.
   *
   * @return the relation
   */
  public Relation getRelation() {
    return relation;
  }

  /**
   * Sets the relation if this is a relation key.
   *
   * @param relation the relation
   */
  public void setRelation(Relation relation) {
    this.relation = relation;
  }


  @Override
  public String toString() {
    return getText();
  }

}
