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

package org.tentackle.model.migrate;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

interface MigrationUtilitiesHolder {
  MigrationUtilities INSTANCE = ServiceFactory.createService(MigrationUtilities.class, MigrationUtilities.class);
}

/**
 * Utility methods to match columns for migration.<br>
 * May be replaced by project specific migration rules via <code>@Service</code>.
 */
@Service(MigrationUtilities.class)    // defaults to self
public class MigrationUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static MigrationUtilities getInstance() {
    return MigrationUtilitiesHolder.INSTANCE;
  }



  private final Map<String, List<String>> syllableMap = new HashMap<>();


  /**
   * Returns whether the character should be treated as a vowel.
   *
   * @param c the character
   * @return true if vowel
   */
  public boolean isVowel(char c) {
    return c=='a' || c=='A' ||
           c=='e' || c=='E' ||
           c=='i' || c=='I' ||
           c=='o' || c=='O' ||
           c=='u' || c=='U' ||
           c=='y' || c=='Y';
  }

  /**
   * Returns whether the given string contains at least one vowel.
   *
   * @param s the string
   * @return true if vowels found
   */
  public boolean containsVowel(String s) {
    for (int i=0; i < s.length(); i++) {
      if (isVowel(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extracts the syllables of a name.<br>
   * The default implementation is very simple, but works good enough for the purpose of column migration.
   *
   * @param name the column name
   * @return the syllables
   */
  public List<String> extractSyllables(String name) {
    return syllableMap.computeIfAbsent(name, n -> {
      List<String> syllables = new ArrayList<>();
      StringBuilder syllable = new StringBuilder();
      for (String s : n.split("_")) {
        syllable.setLength(0);
        char lastChar = 0;
        boolean containsVowel = false;
        int len = s.length();
        for (int i = 0; i < len; i++) {
          char c = s.charAt(i);
          if (Character.isDigit(c)) {
            // numbers build a syllable on their own
            if (syllable.length() > 0 && !Character.isDigit(lastChar)) {
              syllables.add(syllable.toString());
              syllable.setLength(0);
              containsVowel = false;
            }
            syllable.append(c);
            lastChar = c;
          }
          else if (Character.isLetter(c)) {
            c = Character.toLowerCase(c);
            if (syllable.length() > 0 && !Character.isLetter(lastChar)) {
              syllables.add(syllable.toString());
              syllable.setLength(0);
              containsVowel = false;
            }
            if (lastChar != c) {    // ignore duplicates such as tt, ss, pp, etc...
              boolean isVwl = isVowel(c);
              if (syllable.length() == 0) {
                syllable.append(c);
                containsVowel |= isVwl;
                lastChar = c;
              }
              else {
                if (!isVwl && containsVowel && i < len - 2 && containsVowel(s.substring(i + 1))) {
                  // word already contains at least one vowel and this is not a vowel
                  // and not only one char remaining and remainer contains at least one vowel
                  syllable.append(c);
                  syllables.add(syllable.toString());
                  syllable.setLength(0);
                  containsVowel = false;
                }
                else {
                  syllable.append(c);
                  containsVowel |= isVwl;
                }
                lastChar = c;
              }
            }
          }
        }
        if (syllable.length() > 0) {
          syllables.add(syllable.toString());
        }
      }
      return syllables;
    });
  }


  /**
   * Finds the best match for a given model name among existing names.
   *
   * @param modelName the new name according to the model
   * @param existingNames the existing column names in the database table
   * @return the best match, null if existingnames is empty
   */
  public String bestMatch(String modelName, List<String> existingNames) {
    Set<MatchResult> results = new TreeSet<>();
    for (String existingName : existingNames) {
      results.add(new MatchResult(modelName, existingName));
    }
    Iterator<MatchResult> iterator = results.iterator();
    if (iterator.hasNext()) {
      return iterator.next().newName;
    }
    return null;
  }


  /**
   * Holds the distances between all syllables sorted by distance.
   */
  private class MatchResult implements Comparable<MatchResult> {
    private final String newName;
    private final int[] distances;

    private MatchResult(String oldName, String newName) {
      this.newName = newName;
      List<String> oldSyllables = extractSyllables(oldName);
      List<String> newSyllables = extractSyllables(newName);
      distances = new int[oldSyllables.size() * newSyllables.size()];
      int ndx = 0;
      for (String oldSyllable: oldSyllables) {
        for (String newSyllable: newSyllables) {
          distances[ndx++] = StringHelper.levenshteinDistance(oldSyllable, newSyllable);
        }
      }
      Arrays.sort(distances);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MatchResult that = (MatchResult) o;
      if (!newName.equals(that.newName)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return newName.hashCode();
    }

    @Override
    public int compareTo(MatchResult o) {
      int max = Math.max(distances.length, o.distances.length);
      for (int ndx=0; ndx < max; ndx++) {
        if (ndx >= distances.length) {
          return -1;
        }
        if (ndx >= o.distances.length) {
          return  1;
        }
        int rv = distances[ndx] - o.distances[ndx];
        if (rv != 0) {
          return rv;
        }
      }
      return 0;
    }
  }

}
