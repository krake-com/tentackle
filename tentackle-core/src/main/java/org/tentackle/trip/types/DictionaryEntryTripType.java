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

package org.tentackle.trip.types;

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.DictionaryEntryComponent;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.PredefinedArray;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.util.HashMap;
import java.util.Map;


interface DictionaryEntryTypeHolder {
  TripType<Boolean> BOOLEAN_INSTANCE = TripFactory.getInstance().getType(Boolean.TYPE);
  TripType<String> STRING_INSTANCE = TripFactory.getInstance().getType(String.class);
  TripType<String[]> STRING_ARRAY_INSTANCE = TripFactory.getInstance().getType(String[].class);

  // always set, but null in case the application replaces StringArrayTripType
  // by something else but a PredefinedArray.
  @SuppressWarnings("unchecked")
  PredefinedArray<String[]> PREDEFINED_STRING_ARRAY =
    STRING_ARRAY_INSTANCE instanceof PredefinedArray ?
      (PredefinedArray<String[]>) STRING_ARRAY_INSTANCE : null;
}


/**
 * Trip type for {@link DictionaryEntry}.
 */
@TripTypeService(DictionaryEntry.class)
public final class DictionaryEntryTripType extends AbstractTripType<DictionaryEntry> {

  /**
   * Creates the type.
   */
  public DictionaryEntryTripType() {
    super(DictionaryEntry.class);
  }

  @Override
  public void write(DictionaryEntry value, TripStream stream) {
    stream.getSerializer().serializeOpCode(DefaultOpCode.DICTIONARY);
    stream.writeComponentOrdinal(value.getOrdinal());
    DictionaryEntryTypeHolder.STRING_INSTANCE.write(value.getClassName(), stream);
    DictionaryEntryTypeHolder.BOOLEAN_INSTANCE.write(value.isProxy(), stream);
    String[] componentNames = new String[value.getComponents().length];
    String[] componentTypes = new String[componentNames.length];
    int i = 0;
    for (DictionaryEntryComponent component : value.getComponents()) {
      componentNames[i] = component.getComponentName();
      componentTypes[i++] = ReflectionHelper.getSerializableClassName(component.getType().getObjectClass());
    }
    DictionaryEntryTypeHolder.STRING_ARRAY_INSTANCE.write(componentNames, stream);
    DictionaryEntryTypeHolder.STRING_ARRAY_INSTANCE.write(componentTypes, stream);
  }

  @Override
  public DictionaryEntry read(TripStream stream, OpCode opCode) {
    if (opCode != DefaultOpCode.DICTIONARY) {
      throw new TripRuntimeException("unexpected opCode: " + opCode);
    }
    Dictionary dictionary = stream.getDictionary();
    int ordinal = stream.readComponentOrdinal(stream.receiveOpCode());
    String className = DictionaryEntryTypeHolder.STRING_INSTANCE.read(stream, stream.receiveOpCode());
    boolean isProxy = DictionaryEntryTypeHolder.BOOLEAN_INSTANCE.read(stream, stream.receiveOpCode());
    opCode = stream.receiveOpCode();
    String[] componentNames;
    String[] componentTypes;
    if (DictionaryEntryTypeHolder.PREDEFINED_STRING_ARRAY != null) {
      componentNames = DictionaryEntryTypeHolder.PREDEFINED_STRING_ARRAY.readWithType(stream, opCode);
      opCode = stream.receiveOpCode();
      componentTypes = DictionaryEntryTypeHolder.PREDEFINED_STRING_ARRAY.readWithType(stream, opCode);
    }
    else {
      componentNames = DictionaryEntryTypeHolder.STRING_ARRAY_INSTANCE.read(stream, opCode);
      opCode = stream.receiveOpCode();
      componentTypes = DictionaryEntryTypeHolder.STRING_ARRAY_INSTANCE.read(stream, opCode);
    }
    if (componentTypes.length != componentNames.length) {
      throw new TripRuntimeException("number of component types " + componentTypes.length +
                                     " differs from the number of components " + componentNames.length + " of " + className);
    }
    if (dictionary.isServer() && ordinal >= 0) {
      // only the server assigns permanent ordinals: a client sends temporary entries only
      throw new TripRuntimeException("permanent dictionary entry " + ordinal + " received for " + className);
    }
    PreAuthenticationPolicy policy = stream.getReadPolicy();
    if (policy != null) {
      // before anything is loaded: the static initializer of an arbitrary class must not even run
      checkPreAuthentication(policy, className, isProxy, componentNames, componentTypes);
    }
    DictionaryEntryComponent[] components = new DictionaryEntryComponent[componentNames.length];
    for (int i = 0; i < componentNames.length; i++) {
      components[i] = dictionary.createComponent(componentNames[i],
                                                 TripFactory.getInstance().getType(componentTypes[i]));
    }
    return dictionary.createEntry(ordinal, className, isProxy, components);
  }


  /**
   * Checks a dictionary entry received from a client that is not authenticated yet.
   * <p>
   * The class must be on the allowlist. A component type must be on the allowlist as well, unless
   * it is the type the allowed class declares for that component: the class was vetted together
   * with the types of its components. Since the values of final component types are read without
   * their own dictionary entry, the component types cannot be left unchecked.
   * Dynamic proxies are refused altogether.
   *
   * @param policy the policy
   * @param className the class name
   * @param isProxy true if a dynamic proxy
   * @param componentNames the component names, the interface names for a proxy
   * @param componentTypes the component types
   */
  private void checkPreAuthentication(PreAuthenticationPolicy policy, String className, boolean isProxy,
                                      String[] componentNames, String[] componentTypes) {
    if (isProxy) {
      policy.violation("dynamic proxy for " + String.join(", ", componentNames));
      return;
    }
    boolean classAllowed = policy.isClassAllowed(className);
    if (!classAllowed) {
      policy.violation("class " + className);
    }
    Map<String, String> declaredTypes = null;
    for (int i = 0; i < componentTypes.length; i++) {
      String typeName = componentTypes[i];
      if (!policy.isClassAllowed(typeName)) {
        if (declaredTypes == null) {
          declaredTypes = classAllowed ? getDeclaredComponentTypes(className) : Map.of();
        }
        if (!typeName.equals(declaredTypes.get(componentNames[i]))) {
          policy.violation("type " + typeName + " of component " + componentNames[i] + " in class " + className);
        }
      }
    }
  }

  /**
   * Gets the declared types of the components of an allowed class.
   *
   * @param className the class name
   * @return the serializable type names mapped by component name
   */
  private Map<String, String> getDeclaredComponentTypes(String className) {
    Map<String, String> declaredTypes = new HashMap<>();
    for (TripComponent<?, ?> component : TripFactory.getInstance().getType(className).getComponents()) {
      declaredTypes.put(component.getName(), ReflectionHelper.getSerializableClassName(component.getType().getObjectClass()));
    }
    return declaredTypes;
  }

}
