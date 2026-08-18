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

package org.tentackle.fx.table.filter;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;

/**
 * One selectable value of a column filter's value list.<p>
 * Besides the column's distinct values, the list holds the pseudo values
 * {@link KIND#SELECT_ALL} and {@link KIND#BLANK}.
 *
 * @author harald
 * @see TableColumnFilter
 */
public class TableFilterValue {

  /**
   * The kind of value.
   */
  public enum KIND {

    /** a distinct value of the column. */
    VALUE,

    /** the pseudo value selecting or deselecting all values. */
    SELECT_ALL,

    /** the pseudo value for all blank values. */
    BLANK
  }


  private final KIND kind;
  private final String text;
  private final String displayedText;
  private final Node graphic;
  private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", true);


  /**
   * Creates a value.
   *
   * @param kind the kind of value
   * @param text the value's displayed text, null for the pseudo values
   * @param displayedText the text shown in the value list, never null
   * @param graphic the graphic shown in the value list, null if none
   */
  public TableFilterValue(KIND kind, String text, String displayedText, Node graphic) {
    this.kind = kind;
    this.text = text;
    this.displayedText = displayedText;
    this.graphic = graphic;
  }


  @Override
  public String toString() {
    return displayedText;
  }

  /**
   * Gets the kind of value.
   *
   * @return the kind, never null
   */
  public KIND getKind() {
    return kind;
  }

  /**
   * Returns whether this is one of the pseudo values.
   *
   * @return true if not a distinct value of the column
   */
  public boolean isPseudo() {
    return kind != KIND.VALUE;
  }

  /**
   * Gets the value's displayed text as rendered in the table's cells.
   *
   * @return the text, null for the pseudo values
   */
  public String getText() {
    return text;
  }

  /**
   * Gets the text shown in the value list.
   *
   * @return the text, never null
   */
  public String getDisplayedText() {
    return displayedText;
  }

  /**
   * Gets the graphic shown in the value list.
   *
   * @return the graphic, null if none
   */
  public Node getGraphic() {
    return graphic;
  }

  /**
   * Returns whether this value is selected.
   *
   * @return true if selected
   */
  public boolean isSelected() {
    return selected.get();
  }

  /**
   * Sets whether this value is selected.
   *
   * @param selected true if selected
   */
  public void setSelected(boolean selected) {
    this.selected.set(selected);
  }

  /**
   * The selected property.
   *
   * @return the property
   */
  public BooleanProperty selectedProperty() {
    return selected;
  }

}
