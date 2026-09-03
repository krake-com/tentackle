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

package org.tentackle.trip.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A PDO-shaped object for the TRIP benchmark.
 * <p>
 * The field mix mirrors a typical persistent entity: a numeric id and serial, a handful of
 * primitive columns, a few strings, a money amount, dates, an enum and a reference to another
 * entity. The primitive fields are what makes the boxing in the component path visible.
 */
public class BenchmarkEntity {

  /** the state of an entity, so that the enum path is covered. */
  public enum State {
    /** newly created. */
    NEW,
    /** currently being worked on. */
    ACTIVE,
    /** no longer in use. */
    RETIRED
  }

  public long id;
  public long serial;
  public int number;
  public int quantity;
  public int position;
  public short priority;
  public byte flags;
  public char separator;
  public boolean active;
  public boolean deleted;
  public double weight;
  public double factor;
  public float ratio;
  public long created;
  public long modified;

  public String name;
  public String description;
  public String reference;

  public BigDecimal amount;
  public LocalDate validFrom;
  public LocalDateTime timestamp;
  public State state;
  public BenchmarkEntity parent;

  public BenchmarkEntity() {
  }

  /**
   * Creates an entity filled with values derived from the given index.
   *
   * @param index the index within the generated batch
   * @param parent the parent entity, null if none
   */
  public BenchmarkEntity(int index, BenchmarkEntity parent) {
    id = 1_000_000L + index;
    serial = index % 97;
    number = index * 7;
    quantity = index % 13;
    position = index;
    priority = (short) (index % 5);
    flags = (byte) (index % 3);
    separator = index % 2 == 0 ? ';' : ',';
    active = index % 2 == 0;
    deleted = false;
    weight = index * 1.5d;
    factor = 0.75d;
    ratio = index * 0.25f;
    created = 1_700_000_000_000L + index;
    modified = 1_700_000_100_000L + index;
    name = "entity-name-" + index;
    description = "a reasonably long description for the entity number " + index;
    reference = "REF-0000-" + (index % 50);   // repeats, so the string deduplication kicks in
    amount = BigDecimal.valueOf(index * 1234L, 2);
    validFrom = LocalDate.of(2020 + index % 5, 1 + index % 12, 1 + index % 28);
    timestamp = LocalDateTime.of(2024, 6, 1, index % 24, index % 60, index % 60);
    state = State.values()[index % State.values().length];
    this.parent = parent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BenchmarkEntity that = (BenchmarkEntity) o;
    return id == that.id && serial == that.serial && number == that.number &&
           quantity == that.quantity && position == that.position && priority == that.priority &&
           flags == that.flags && separator == that.separator && active == that.active &&
           deleted == that.deleted && Double.compare(weight, that.weight) == 0 &&
           Double.compare(factor, that.factor) == 0 && Float.compare(ratio, that.ratio) == 0 &&
           created == that.created && modified == that.modified &&
           Objects.equals(name, that.name) && Objects.equals(description, that.description) &&
           Objects.equals(reference, that.reference) && Objects.equals(amount, that.amount) &&
           Objects.equals(validFrom, that.validFrom) && Objects.equals(timestamp, that.timestamp) &&
           state == that.state && Objects.equals(parent, that.parent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, number, name, amount, state);
  }

  @Override
  public String toString() {
    return "BenchmarkEntity[" + id + ", " + name + "]";
  }

}
