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

package org.tentackle.fx.component.auto;

import org.tentackle.misc.SubString;

import javafx.scene.Group;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Callback;
import java.util.List;

/**
 * List cell factory for auto-completion.<br>
 * Displays the matched substrings in bold.
 */
public class AutoCompletionCellFactory implements Callback<ListView<List<SubString>>, ListCell<List<SubString>>> {

  private final Font font;
  private final Font boldFont;

  /**
   * Creates the list cell factory.
   *
   * @param font the font of the text input control
   */
  public AutoCompletionCellFactory(Font font) {
    this.font = font;
    boldFont = Font.font(font.getFamily(), FontWeight.BOLD, font.getSize());
  }

  /**
   * Gets the font used for the non-matching substrings.
   *
   * @return the standard font
   */
  public Font getFont() {
    return font;
  }

  /**
   * Gets the font for the matching substrings.
   *
   * @return the matching font
   */
  public Font getBoldFont() {
    return boldFont;
  }

  @Override
  public ListCell<List<SubString>> call(ListView<List<SubString>> param) {
    return new ListCell<>() {

      @Override
      protected void updateItem(List<SubString> item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
          setGraphic(null);
        }
        else {
          TextFlow textFlow = new TextFlow();
          String name = item.getFirst().getString();
          int length = 0;
          for (SubString subString : item) {
            if (length < subString.getBeginIndex()) {
              addText(textFlow, name.substring(length, subString.getBeginIndex()), getFont());
            }
            addText(textFlow, subString.getSegment(), getBoldFont());
            length = subString.getEndIndex();
          }
          if (length < name.length()) {
            addText(textFlow, name.substring(length), getFont());
          }
          Group group = new Group(textFlow);
          setGraphic(group);
        }
      }
    };
  }

  private void addText(TextFlow textFlow, String str, Font font) {
    Text text = new Text(str);
    text.setFont(font);
    textFlow.getChildren().add(text);
  }

}
