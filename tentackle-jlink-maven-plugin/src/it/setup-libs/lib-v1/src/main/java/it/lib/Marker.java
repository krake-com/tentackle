package it.lib;

public class Marker {

  // deliberately a method, not a "static final String" field: such a field would be a compile-time
  // constant, inlined at every call site by javac, which would defeat this whole fixture's purpose
  public static String version() {
    return "v1";
  }

}
