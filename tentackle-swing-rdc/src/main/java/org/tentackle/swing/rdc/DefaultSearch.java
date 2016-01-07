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

package org.tentackle.swing.rdc;

import java.text.MessageFormat;
import java.util.List;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.misc.StringHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormInfo;

/**
 * A default implementation of a PDO search.
 *
 * @author harald
 */
public class DefaultSearch<T extends PersistentDomainObject<T>> extends AbstractPdoSearch<T> {

  private String pattern;                   // normtext pattern
  private int minPatternLength;             // minimum normtext pattern length, 0 = default
  private DefaultSearchPanel searchPanel;   // the search panel


  /**
   * Creates a default PDO search.
   *
   * @param pdoClass the pdo class
   * @param context the domain context
   * @param fetchSize the fetchsize
   * @param warnRowCount number of rows displayable without warning, 0 = unlimited
   * @param maxRowCount maximum number of rows displayable, 0 = unlimited
   */
  public DefaultSearch(Class<T> pdoClass, DomainContext context, int fetchSize, int warnRowCount, int maxRowCount) {
    super(pdoClass, context, fetchSize, warnRowCount, maxRowCount);
  }

  /**
   * Creates a default PDO search.<br>
   * With {@link #defaultFetchSize}, {@link #defaultWarnRowCount} and {@link #defaultMaxRowCount}.
   *
   * @param pdoClass the pdo class
   * @param context the domain context
   */
  public DefaultSearch(Class<T> pdoClass, DomainContext context) {
    super(pdoClass, context);
  }

  /**
   * Creates a default PDO search.<br>
   * With {@link #defaultFetchSize}, {@link #defaultWarnRowCount} and {@link #defaultMaxRowCount}.
   *
   * @param pdo the pdo
   */
  public DefaultSearch(T pdo) {
    super(pdo.getEffectiveClass(), pdo.getDomainContext());
    presetSearchCriteria(pdo);
  }


  /**
   * Gets the normtext search pattern.
   *
   * @return the pattern
   */
  public String getPattern() {
    return pattern;
  }

  /**
   * Sets the normtext search pattern.
   *
   * @param pattern the normtext
   */
  public void setPattern(String pattern) {
    this.pattern = pattern;
    getSearchPanel().setPattern(pattern);
  }

  /**
   * Gets the minimum normtext pattern length.
   *
   * @return the minimum length, 0 = default
   */
  public int getMinPatternLength() {
    return minPatternLength;
  }

  /**
   * Sets the minimum normtext pattern length.
   *
   * @param minPatternLength the minimum length
   */
  public void setMinPatternLength(int minPatternLength) {
    this.minPatternLength = minPatternLength;
  }


  @Override
  public DefaultSearchPanel getSearchPanel() {
    if (searchPanel == null) {
      searchPanel = new DefaultSearchPanel();
    }
    return searchPanel;
  }

  @Override
  public String getSearchCriteriaAsString() {
    return MessageFormat.format(RdcSwingRdcBundle.getString("PATTERN={0}"), pattern == null || pattern.isEmpty() ? RdcSwingRdcBundle.getString("<ALL>") : pattern);
  }

  @Override
  public boolean isSearchCriteriaValid() {
    return validatePattern();
  }

  @Override
  public boolean isSearchRetrievingAll() {
    return pattern == null || pattern.isEmpty();
  }

  @Override
  public boolean isResultValid(List<T> list) {
    return true;
  }

  @Override
  public ScrollableResource<T> findByCriteria(T proxy) {
    // default goes by normtext, if provided
    return proxy.selectByNormTextAsCursor(StringHelper.toLikeString(StringHelper.normalize(pattern)));
  }

  @Override
  public void resetSearchCriteria() {
    pattern = null;
  }

  @Override
  public void presetSearchCriteria(T template) {
    // default does nothing
  }

  /**
   * Validates the normtext pattern.
   *
   * @return true if ok
   */
  public boolean validatePattern() {
    pattern = getSearchPanel().getPattern();
    if (minPatternLength > 0 && (pattern == null || pattern.length() < minPatternLength)) {
      FormInfo.show(MessageFormat.format(RdcSwingRdcBundle.getString("SEARCH PATTERN MUST CONTAIN AT LEAST {0} CHARACTERS"), minPatternLength));
      return false;
    }
    return true;
  }

}
