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

package org.tentackle.fx;

import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Callback;

import org.tentackle.misc.ShortLongText;

import java.util.List;

/**
 * Cell factory to display items implementing {@link ShortLongText}.
 */
public class ShortLongTextCellFactory <T extends ShortLongText> implements Callback<ListView<T>, ListCell<T>> {

  private static final double GRID_GAP = 20.0;  // visual gap between short and long text columns

  private boolean listenerPending = true;       // true if add listener to check if popup closed
  private boolean widthPending;                 // true if (re-)compute widths
  private double shortWidth;                    // width for the short text column
  private double longWidth;                     // witdh for the long text column


  @Override
  public ListCell<T> call(ListView<T> listView) {

    if (listenerPending) {
      listView.focusedProperty().addListener((observable, oldValue, newValue) -> {
        if (oldValue) {   // was focused and is now unfocused (popup hidden)
          widthPending = true;
        }
      });
      listenerPending = false;
      widthPending = true;
    }

    return new ListCell<>() {

      private GridPane gridPane;
      private final Label shortLabel = new Label();
      private final Label longLabel = new Label();

      private double lastShortWidth;
      private double lastLongWidth;

      @Override
      protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
          setGraphic(null);
        }
        else {
          if (widthPending) {
            computeWidths(getFont(), listView.getItems());
            widthPending = false;
          }
          if (gridPane == null) {
            gridPane = new GridPane();
            gridPane.getColumnConstraints().addAll(
                new ColumnConstraints(shortWidth, shortWidth, shortWidth),
                new ColumnConstraints(longWidth, longWidth, longWidth)
            );
            gridPane.add(shortLabel, 0, 0);
            gridPane.add(longLabel, 1, 0);
            gridPane.setHgap(GRID_GAP);
          }
          else {
            if (lastShortWidth != shortWidth) {
              updateConstraints(gridPane.getColumnConstraints().get(0), shortWidth);
              lastShortWidth = shortWidth;
            }
            if (lastLongWidth != longWidth) {
              updateConstraints(gridPane.getColumnConstraints().get(1), longWidth);
              lastLongWidth = longWidth;
            }
          }
          shortLabel.setText(item.getShortText());
          longLabel.setText(item.getLongText());
          setGraphic(gridPane);
        }
      }

      private void updateConstraints(ColumnConstraints constraints, double width) {
        constraints.setMinWidth(width);
        constraints.setPrefWidth(width);
        constraints.setMaxWidth(width);
      }

    };
  }

  private void computeWidths(Font font, List<T> items) {
    Text text = new Text();
    text.setFont(font);
    shortWidth = 0.0;
    longWidth = 0.0;
    for (T item : items) {
      text.setText(item.getShortText());
      shortWidth = Double.max(shortWidth, text.getLayoutBounds().getWidth());
      text.setText(item.getLongText());
      longWidth = Double.max(longWidth, text.getLayoutBounds().getWidth());
    }
  }

}
