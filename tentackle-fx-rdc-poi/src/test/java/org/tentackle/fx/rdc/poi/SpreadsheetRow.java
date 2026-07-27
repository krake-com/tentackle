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

package org.tentackle.fx.rdc.poi;

import org.tentackle.bind.Bindable;
import org.tentackle.common.BMoney;

import java.util.Calendar;
import java.util.Date;

/**
 * The row object exported by the tests.
 * <p>
 * Provides one bindable attribute per branch of
 * {@link PoiTableUtilities#toSpreadsheet(java.util.Collection, java.io.File, java.util.List)}.
 */
public class SpreadsheetRow {

  /**
   * An attribute type the exporter has no special treatment for.
   */
  public enum Status {
    /** the order is still open. */
    OPEN,
    /** the order is done. */
    CLOSED
  }


  private String name;
  private Boolean paid;
  private Integer count;
  private Double rate;
  private BMoney price;       // scale 2
  private BMoney discount;    // scale 2 as well: must share the cell style with price
  private BMoney tax;         // scale 0: needs its own cell style
  private Date date;
  private Calendar calendar;
  private Status status;

  /**
   * Creates a row.
   */
  public SpreadsheetRow() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  @Bindable
  public String getName() {
    return name;
  }

  /**
   * Sets the name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the paid flag.
   *
   * @return the flag
   */
  @Bindable
  public Boolean getPaid() {
    return paid;
  }

  /**
   * Sets the paid flag.
   *
   * @param paid the flag
   */
  public void setPaid(Boolean paid) {
    this.paid = paid;
  }

  /**
   * Gets the count.
   *
   * @return the count
   */
  @Bindable
  public Integer getCount() {
    return count;
  }

  /**
   * Sets the count.
   *
   * @param count the count
   */
  public void setCount(Integer count) {
    this.count = count;
  }

  /**
   * Gets the rate.
   *
   * @return the rate
   */
  @Bindable
  public Double getRate() {
    return rate;
  }

  /**
   * Sets the rate.
   *
   * @param rate the rate
   */
  public void setRate(Double rate) {
    this.rate = rate;
  }

  /**
   * Gets the price.
   *
   * @return the price
   */
  @Bindable
  public BMoney getPrice() {
    return price;
  }

  /**
   * Sets the price.
   *
   * @param price the price
   */
  public void setPrice(BMoney price) {
    this.price = price;
  }

  /**
   * Gets the discount.
   *
   * @return the discount
   */
  @Bindable
  public BMoney getDiscount() {
    return discount;
  }

  /**
   * Sets the discount.
   *
   * @param discount the discount
   */
  public void setDiscount(BMoney discount) {
    this.discount = discount;
  }

  /**
   * Gets the tax.
   *
   * @return the tax
   */
  @Bindable
  public BMoney getTax() {
    return tax;
  }

  /**
   * Sets the tax.
   *
   * @param tax the tax
   */
  public void setTax(BMoney tax) {
    this.tax = tax;
  }

  /**
   * Gets the date.
   *
   * @return the date
   */
  @Bindable
  public Date getDate() {
    return date;
  }

  /**
   * Sets the date.
   *
   * @param date the date
   */
  public void setDate(Date date) {
    this.date = date;
  }

  /**
   * Gets the calendar.
   *
   * @return the calendar
   */
  @Bindable
  public Calendar getCalendar() {
    return calendar;
  }

  /**
   * Sets the calendar.
   *
   * @param calendar the calendar
   */
  public void setCalendar(Calendar calendar) {
    this.calendar = calendar;
  }

  /**
   * Gets the status.
   *
   * @return the status
   */
  @Bindable
  public Status getStatus() {
    return status;
  }

  /**
   * Sets the status.
   *
   * @param status the status
   */
  public void setStatus(Status status) {
    this.status = status;
  }

}
