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

import org.tentackle.common.StringHelper;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripFilter;
import org.tentackle.trip.TripInstanceCreator;
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeCategory;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fallback for objects w/o a dedicated trip type.<br>
 * This is the core type for the majority of objects.
 * <p>
 * The serialization format is as follows:
 * <ul>
 *   <li>The opcode {@link DefaultOpCode#OBJECT}.</li>
 *   <li>The dictionary entry if new for this stream.<br>
 *       Otherwise, the dictionary-ordinal according to the dictionary.</li>
 *   <li>Followed by one of:
 *     <ul>
 *        <li>The null code if the object is null.</li>
 *        <li>The reference if the same or equal object was already sent.</li>
 *        <li>The reference if the object is referencable or de-duplicatable
 *            and for each component, if not the component's default value:
 *          <ul>
 *            <li>The component's ordinal, according to the dictionary, if not simply the next.</li>
 *            <li>The component's serialization according to its type.</li>
 *          </ul>
 *          followed by the opcode {@link DefaultOpCode#END}.
 *        </li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * For the receiver, the following deviations from the sender's class definition are
 * tolerated and handled gracefully:
 * <ul>
 *   <li>Unknown components will silently be ignored.</li>
 *   <li>Components missing in the stream will be initialized with their default value.
 *       Side effect: if the sender omits a component with a default value,
 *       and the receiver's default differs from the server's default, the component will
 *       be initialized with the different receiver's default.</li>
 * </ul>
 * All other deviations are not tolerated and result in a {@link TripRuntimeException},
 * especially unknown classes or type changes. Ideally, the receiver's code base of the
 * serialized objects should be identical to the sender's code base. If this is not practical,
 * both should match as closely as possible. In general, applications should compare their
 * version numbers when connecting to a remote tier and fail if incompatible,
 * just like Tentackle applications do by default.
 * <p>
 * If a class does not provide a no-args constructor, the object can still be created
 * if exactly one of its constructors is annotated with {@link org.tentackle.reflect.CanonicalConstructor}
 * and the corresponding fields are annotated with {@link org.tentackle.reflect.CanonicalField}.
 * Another option is to provide a {@link org.tentackle.trip.TripInstanceCreator}. The components
 * could also be configured with a {@link org.tentackle.trip.TripComponentProvider}.
 * <p>
 * A canonical member that is not backed by a serializable field &ndash; because the field is transient
 * or declared in a JDK class, for example &ndash; gets a component of its own, provided the member is
 * a {@link org.tentackle.reflect.CanonicalGetter}. This is how a {@link MapTripType} or
 * {@link CollectionTripType} transports state that lives in a JDK field, see
 * {@link org.tentackle.misc.ChainedProperties}.
 * <p>
 * These alternatives delay the construction of the object until some or all of its component values
 * are known, which limits how far a component may back-reference the object it belongs to:
 * <ul>
 *   <li>With a no-args constructor, the object exists before any component is read. Every component
 *       may back-reference it, directly or indirectly. This is the only case without restrictions.</li>
 *   <li>With a {@link org.tentackle.reflect.CanonicalConstructor}, the object is created as soon as
 *       all constructor arguments have been read. The canonical components themselves cannot
 *       back-reference the object, but all remaining components can, since they are read
 *       afterward.</li>
 *   <li>With a {@link org.tentackle.reflect.CanonicalBuilder}, the object does not exist before
 *       <em>all</em> components have been read. No component can back-reference it (the same
 *       restriction that applies to records, see {@link RecordTripType}).</li>
 * </ul>
 * Violating this results in a "no such reference id" {@link TripRuntimeException} during
 * deserialization. Objects forming such a cycle must provide a no-args constructor.
 * <p>
 * Application-specific object types should extend this class and annotate
 * it with @{@link org.tentackle.trip.TripTypeService}.
 *
 * @param <T> the type
 */
// created by ObjectTypeBuilder, not mapped by @TripTypeService
public class ObjectTripType<T> extends AbstractDictionaryBasedTripType<T> {

  private static final OpCode OP_CODE = DefaultOpCode.OBJECT;

  private final TripComponent<T, ?>[] components;
  private final Constructor<T> canonicalConstructor;
  private final Member[] canonicalMembers;
  private final ReflectionHelper.CanonicalBuilderInfo canonicalBuilderInfo;
  private final boolean delayedConstruction;

  /**
   * The canonical constructor's arguments at their default value, cloned per read.<br>
   * Null if there is no canonical constructor.
   *
   * @see #createCanonicalArgs()
   */
  private final Object[] canonicalArgDefaults;

  /**
   * Maps a component ordinal to its index in the canonical constructor, -1 if not canonical.<br>
   * Null if there is no canonical constructor.
   *
   * @see #getCanonicalIndex(int)
   */
  private final int[] canonicalIndexByOrdinal;

  /**
   * Creates the type.
   *
   * @param clazz the object's class
   */
  public ObjectTripType(Class<T> clazz) {
    super(clazz, TripTypeCategory.CLASS, TripTypeCategory.INTERFACE);
    canonicalConstructor = ReflectionHelper.getCanonicalConstructor(clazz);
    canonicalMembers = createCanonicalMembers(canonicalConstructor);
    canonicalBuilderInfo = ReflectionHelper.getCanonicalBuilderInfo(clazz);
    delayedConstruction = canonicalConstructor != null || canonicalBuilderInfo != null;
    components = createComponents(createInitializedDefault());
    canonicalArgDefaults = canonicalConstructor == null ? null : createCanonicalArgDefaults();
    canonicalIndexByOrdinal = canonicalConstructor == null ? null : createCanonicalIndexByOrdinal();
  }

  /**
   * Creates the canonical constructor's arguments at their default value.
   * <p>
   * Done once per type: obtaining a type's default reflectively allocates a one-element array and
   * boxes its only element, which is far too much work to repeat for every object read.
   *
   * @return the arguments, all at their default value
   */
  private Object[] createCanonicalArgDefaults() {
    Object[] args = new Object[getCanonicalMembers().length];
    Class<?>[] parameterTypes = canonicalConstructor.getParameterTypes();
    for (int i = 0; i < args.length && i < parameterTypes.length; i++) {
      // a fresh array element is the type's default: 0, false, '\0' for primitives, null otherwise
      args[i] = Array.get(Array.newInstance(parameterTypes[i], 1), 0);
    }
    return args;
  }

  /**
   * Maps each component's ordinal to its index in the canonical constructor.
   * <p>
   * Done once per type, so that reading a component is an array load instead of a linear scan
   * over the canonical members comparing names.
   *
   * @return the canonical index per component ordinal, -1 for a non-canonical component
   */
  private int[] createCanonicalIndexByOrdinal() {
    int maxOrdinal = -1;
    for (TripComponent<T, ?> component : components) {
      maxOrdinal = Math.max(maxOrdinal, component.getOrdinal());
    }
    int[] indexes = new int[maxOrdinal + 1];
    Arrays.fill(indexes, -1);
    for (TripComponent<T, ?> component : components) {
      indexes[component.getOrdinal()] = getCanonicalIndex(component.getName());
    }
    return indexes;
  }

  @Override
  public boolean isDeduplicateable(T value) {
    if (deduplicationEnabled) {
      if (deduplicatableMethod == null) {
        // all objects are not de-duplicatable, except when explicitly marked as de-duplicatable
        return Boolean.TRUE.equals(deduplicatable);
      }
      return invokeDeduplicatableMethod(value);
    }
    return false;
  }

  /**
   * Returns whether the object construction is delayed.<br>
   * Delayed construction means that the object does not provide a no-args constructor
   * or must be created by a builder.
   *
   * @return true if delayed construction, false otherwise
   */
  public boolean isDelayedConstruction() {
    return delayedConstruction;
  }

  /**
   * Gets the canonical constructor.
   *
   * @return the constructor, null if not provided
   * @see org.tentackle.reflect.CanonicalConstructor
   */
  public Constructor<T> getCanonicalConstructor() {
    return canonicalConstructor;
  }

  /**
   * Gets the canonical members.
   *
   * @return the members, empty if not provided
   * @see org.tentackle.reflect.CanonicalField
   * @see org.tentackle.reflect.CanonicalGetter
   */
  public Member[] getCanonicalMembers() {
    return canonicalMembers;
  }

  /**
   * Gets the canonical builder info.
   *
   * @return the builder info, null if not provided
   * @see org.tentackle.reflect.CanonicalBuilder
   */
  public ReflectionHelper.CanonicalBuilderInfo getCanonicalBuilderInfo() {
    return canonicalBuilderInfo;
  }

  @Override
  public TripComponent<T, ?>[] getComponents() {
    return components;
  }

  @Override
  public void write(T value, TripStream stream) {
    stream.getSerializer().serializeOpCode(getOpCode());
    sendDictionary(stream);
    if (value == null) {
      sendNull(stream);
    }
    else {
      Reference<T> reference = sendReference(stream, value);
      if (reference == null || reference.isObjectFollowing()) {
        prepareWrite(value);
        writeComponents(value, stream);
      }
    }
  }

  @Override
  public T read(TripStream stream, OpCode opCode) {
    return read(stream, opCode, null);
  }

  @Override
  public T read(TripStream stream, OpCode opCode, Consumer<T> initializer) {
    DictionaryEntry entry = null;

    if (opCode == getOpCode()) {
      // determine the dictionary entry from the stream, if provided
      opCode = stream.receiveOpCode();
      if (opCode == DefaultOpCode.DICTIONARY || opCode.isDictionaryOrdinal()) {
        entry = stream.receiveDictionary(opCode);
        opCode = stream.receiveOpCode();
      }

      if (opCode == DefaultOpCode.NULL) {
        return null;
      }
    }
    // else: non-final type invoked as a component of another object (see below in readComponents)

    if (entry == null) {
      // invoked as a component of another object. (see below)
      // this trip type is already the runtime-type!
      entry = stream.getDictionaryEntry(getObjectClass());
    }

    T value;
    if (canonicalConstructor == null && canonicalBuilderInfo == null) {
      value = createInitializedDefault();
      if (initializer != null) {
        initializer.accept(value);
      }
    }
    else {
      value = null;
    }

    boolean readContent = true;
    Reference<T> reference = null;
    if (opCode.isReference()) {
      reference = receiveReference(stream, opCode);
      readContent = reference.isObjectFollowing();
      if (readContent) {
        opCode = stream.receiveOpCode();    // fetch the next opcode for loop below
        if (value != null) {
          stream.addReference(reference, value, isDeduplicateable(value));
        }
        // else: if canonical, components cannot back-reference until the value is created.
      }
      else {
        value = reference.getObject();
        if (value == null) {
          throw new TripRuntimeException("reference " + reference + " has no object");
        }
      }
    }

    if (readContent) {
      value = readComponents(stream, opCode, entry, reference, value, initializer);
      validateRead(value);
    }
    return value;
  }


  /**
   * Gets the opcode for this trip type.<br>
   * The default is {@link DefaultOpCode#OBJECT}.
   * Can be overridden by subclasses.
   *
   * @return the opcode
   */
  protected OpCode getOpCode() {
    return OP_CODE;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected TripComponent<T, ?>[] createComponents(T object) {
    TripComponent<T, ?>[] tripComponents = super.createComponents(object);
    if (object == null) {
      Member[] members = getCanonicalMembers();
      // canonical components must come first (ordered according to the canonical constructor)
      TripComponent<T, ?>[] canonicalComponents = new TripComponent[members.length];
      Collection<TripComponent<T, ?>> otherComponents = new ArrayList<>();
      for (TripComponent<T, ?> tripComponent : tripComponents) {
        int canonicalIndex = getCanonicalIndex(tripComponent.getName());
        if (canonicalIndex >= 0) {
          canonicalComponents[canonicalIndex] = tripComponent;
        }
        else {
          otherComponents.add(tripComponent);
        }
      }
      // The ordinals of the components above are already taken, so the synthesized ones continue
      // after them. Only the array order is canonical: the ordinals stay as they are, since
      // they address the components within the dictionary entry.
      int ordinal = tripComponents.length;
      for (int i = 0; i < canonicalComponents.length; i++) {
        if (canonicalComponents[i] == null) {
          canonicalComponents[i] = createCanonicalComponent(members[i], i, ordinal++);
        }
      }
      TripComponent<T, ?>[] allComponents = new TripComponent[canonicalComponents.length + otherComponents.size()];
      System.arraycopy(canonicalComponents, 0, allComponents, 0, canonicalComponents.length);
      int otherIndex = canonicalComponents.length;
      for (TripComponent<T, ?> tripComponent : otherComponents) {
        allComponents[otherIndex++] = tripComponent;
      }
      return allComponents;
    }
    // no canonical fields
    return tripComponents;
  }

  /**
   * Creates a component for a canonical member that is not covered by a component of its own.
   * <p>
   * This is the case if the member's field is not serializable, for example, because it is
   * transient or declared in a JDK class (see {@link MapTripType#isFieldSerializable(Field)}).
   * If the member is a {@link org.tentackle.reflect.CanonicalGetter}, the getter provides the value.
   * The component needs no setter, since its value is passed to the canonical constructor.
   *
   * @param member the canonical member
   * @param canonicalIndex the index of the member within the canonical constructor's parameters
   * @param ordinal the ordinal to assign to the component
   * @return the component
   */
  protected TripComponent<T, ?> createCanonicalComponent(Member member, int canonicalIndex, int ordinal) {
    if (member instanceof Method getter) {
      // the default is the parameter type's default, just as in createCanonicalArgs()
      Class<?> parameterType = canonicalConstructor == null ? getter.getReturnType() :
                               canonicalConstructor.getParameterTypes()[canonicalIndex];
      Object defaultValue = Array.get(Array.newInstance(parameterType, 1), 0);
      return TripFactory.getInstance().createComponent(this, ordinal, getter, null, true, defaultValue);
    }
    // A field member without a component, i.e., the field is not serializable.
    // Provide a @CanonicalGetter or a TripComponentProvider!
    throw new TripRuntimeException("unmatched canonical member " + member + " in " + getObjectClass());
  }

  /**
   * Writes the components of the object.
   *
   * @param value the object to write
   * @param stream the stream to write to
   */
  protected void writeComponents(T value, TripStream stream) {
    int lastOrdinal = -1;
    TripFilter filter = stream.getFilter();
    for (TripComponent<T, ?> component : getComponents()) {
      // the component writes its ordinal and value, or nothing if it holds its default value.
      // Delegating lets a primitive component keep its value unboxed, see PrimitiveFieldTripComponent.
      // A component rejected by the filter is skipped just like one holding its default value.
      if (filter == null || filter.accept(value, component)) {
        lastOrdinal = component.write(value, stream, lastOrdinal);
      }
    }
    sendEnd(stream);
  }

  /**
   * Reads the components of the object.
   *
   * @param stream the stream to read from
   * @param opCode the next opcode
   * @param entry the dictionary entry
   * @param reference the optional reference
   * @param value the object, null for delayed construction
   * @param initializer the object initializer invoked after the new object is created
   * @param componentValues optional map to store the component values instead of the object itself
   * @return the object
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected T readComponents(TripStream stream, OpCode opCode, DictionaryEntry entry,
                             Reference<T> reference, T value, Consumer<T> initializer,
                             Map<TripComponent<T, ?>, Object> componentValues) {

    Object[] canonicalArgs = null;           // the canonical constructor's arguments
    boolean[] canonicalArgsAssigned = null;  // which of them were read from the stream
    int canonicalArgsMissing = 0;            // how many of them are still missing
    if (canonicalConstructor != null) {
      canonicalArgs = createCanonicalArgs();
      canonicalArgsAssigned = new boolean[canonicalArgs.length];
      canonicalArgsMissing = canonicalArgs.length;
    }
    Object canonicalBuilder = null;
    if (canonicalBuilderInfo != null) {
      try {
        try {
          canonicalBuilder = canonicalBuilderInfo.constructor().newInstance();
        }
        catch (IllegalAccessException ix) {
          canonicalBuilderInfo.constructor().setAccessible(true);
          canonicalBuilder = canonicalBuilderInfo.constructor().newInstance();
        }
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new TripInstantiationException(getObjectClass(), "cannot create instance canonical builder", e);
      }
    }

    if (reference != null) {
      reference.markReceived();
    }

    int lastOrdinal = -1;
    while (opCode != DefaultOpCode.END) {
      int ordinal;
      if (opCode.isComponentOrdinal()) {
        lastOrdinal = ordinal = stream.readNextComponentOrdinal(lastOrdinal, opCode);   // component ordinal in the dictionary
        opCode = stream.receiveOpCode();
      }
      else {
        ordinal = ++lastOrdinal;
      }
      TripComponent component = entry.getTypeComponent(ordinal);

      if (component == null) {
        // not provided locally -> consume the value with the type the sender's dictionary names
        TripComponent.readValue(entry.getComponent(ordinal).getType(), stream, opCode);
        // next component
        opCode = stream.receiveOpCode();
        continue;
      }

      if (componentValues == null && value != null) {
        // The common case: the object exists, so the component reads straight into it. This is
        // the only branch a primitive component can serve without boxing, which is why it is
        // separated from the delayed-construction ones below.
        component.read(value, stream, opCode);
        // next component
        opCode = stream.receiveOpCode();
        continue;
      }

      // The object does not exist yet (delayed construction), or the instance creator builds it
      // from the values: either way the value cannot be assigned, only collected.
      Object componentValue = component.readValue(stream, opCode);
      if (componentValues != null) {
        componentValues.put(component, componentValue);
      }
      else if (canonicalBuilderInfo != null) {
        Method method = canonicalBuilderInfo.getMethod(component.getName());
        if (method == null) {
          throw new TripInstantiationException(getObjectClass(), "no builder method for " + component);
        }
        try {
          method.invoke(canonicalBuilder, componentValue);
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          // the method must be public (no setAccessible)
          throw new TripInstantiationException(getObjectClass(), "cannot invoke builder method " + method, e);
        }
      }
      else if (canonicalConstructor != null) {
        // the local component's own ordinal, not the stream's: for the receiver the two
        // differ whenever the sender declares the components in another order
        int canonicalIndex = getCanonicalIndex(component.getOrdinal());
        if (canonicalIndex >= 0) {
          canonicalArgs[canonicalIndex] = componentValue;
          if (!canonicalArgsAssigned[canonicalIndex]) {
            // count each argument once: a repeated ordinal must not create the object early,
            // i.e., with arguments that were not read from the stream yet
            canonicalArgsAssigned[canonicalIndex] = true;
            if (--canonicalArgsMissing == 0) {
              // we got all canonical fields -> create the object
              value = createFromCanonicalArgs(stream, reference, initializer, canonicalArgs);
            }
          }
        }
      }
      // next component
      opCode = stream.receiveOpCode();
    }

    if (value == null && canonicalConstructor != null && canonicalBuilderInfo == null && componentValues == null) {
      // Not all canonical components arrived, for example, because the sender's version of the class
      // does not provide them (anymore). Create the object from what we got instead of returning
      // null: the missing arguments keep their default value, just like the components of a class
      // created by its no-args constructor would.
      value = createFromCanonicalArgs(stream, reference, initializer, canonicalArgs);
    }

    if (canonicalBuilderInfo != null) {
      try {
        value = (T) canonicalBuilderInfo.buildMethod().invoke(canonicalBuilder);
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        throw new TripInstantiationException(getObjectClass(),
                                             "cannot create value via builder " +
                                             canonicalBuilderInfo.constructor().getDeclaringClass(),
                                             e);
      }
      if (componentValues == null) {
        // if componentValues is set, the instance creator builds the object from the values (see caller)
        if (initializer != null) {
          initializer.accept(value);
        }
        if (reference != null) {
          // Register the object so that it can be back-referenced later in the stream.
          // Unlike the canonical constructor, the object does not exist before all components
          // are read. Components can therefore not back-reference the object itself
          // (the same restriction as for records).
          stream.addReference(reference, value, isDeduplicateable(value));
        }
      }
    }

    return value;
  }

  /**
   * Reads the components of the object.
   *
   * @param stream the stream to read from
   * @param opCode the next opcode
   * @param entry the dictionary entry
   * @param reference the optional reference
   * @param value the object, null for delayed construction
   * @param initializer the object initializer invoked after the new object is created.
   *        Notice that a {@link TripInstanceCreator} creating the object from its component values
   *        replaces the instance the initializer was applied to (see below).
   * @return the object
   */
  protected T readComponents(TripStream stream, OpCode opCode, DictionaryEntry entry,
                             Reference<T> reference, T value, Consumer<T> initializer) {

    Map<TripComponent<T, ?>, Object> componentValues = null;
    TripInstanceCreator<T> instanceCreator = getInstanceCreator();
    if (instanceCreator != null && instanceCreator.isCreationFromValuesSupported(getObjectClass())) {
      // Delegate the construction to the instance creator.
      // The creator is responsible for _all_ components, not only the constructor's parameters!
      componentValues = new LinkedHashMap<>();
    }

    value = readComponents(stream, opCode, entry, reference, value, initializer, componentValues);

    if (componentValues != null) {
      value = instanceCreator.create(getObjectClass(), componentValues);
      if (reference != null) {
        // The creator returned a new instance, replacing the one the reference was registered for
        // (either the bare instance from createInitializedDefault() or a canonically constructed one).
        // Re-register it so that back-references later in the stream resolve to the object
        // that is actually returned instead of the discarded one.
        stream.addReference(reference, value, isDeduplicateable(value));
      }
    }

    return value;
  }

  /**
   * Creates an instance of the class with its no-args constructor.<br>
   * If the class provides a constructor annotated with {@link org.tentackle.reflect.CanonicalConstructor},
   * the construction will be delayed and this method returns null.
   *
   * @return the instance, null if delayed or interface or abstract class
   */
  @Override
  protected T createInitializedDefault() {
    return delayedConstruction ? null : super.createInitializedDefault();
  }

  /**
   * Creates the canonical members.<br>
   * The members can be provided by {@link org.tentackle.reflect.CanonicalField} or
   * {@link org.tentackle.reflect.CanonicalGetter} annotations.
   * Alternatively, the constructor's parameter can be annotated with {@link org.tentackle.reflect.CanonicalName}
   * and there is a declared field or a public getter method with a matching name.
   * The annotation can also be mixed.
   *
   * @param constructor the canonical constructor, null if not provided
   * @return the fields and/or methods
   */
  protected Member[] createCanonicalMembers(Constructor<T> constructor) {
    Class<T> clazz = getObjectClass();
    Member[] canonicalMembers = ReflectionHelper.getCanonicalMembers(clazz);
    if (constructor != null && canonicalMembers.length != constructor.getParameterCount()) {
      // no or not all fields/methods are annotated with @CanonicalField/@CanonicalGetter
      // -> try to find the fields according to the constructor's parameter canonical names
      String[] canonicalNames = ReflectionHelper.getCanonicalNames(constructor);
      if (canonicalNames.length == constructor.getParameterCount()) {
        Member[] members = new Member[canonicalNames.length];
        for (int i = 0; i < members.length; i++) {
          String canonicalName = canonicalNames[i];
          if (canonicalName != null) {   // if name provided either by CanonicalName or "javac -parameter"
            for (Member canonicalMember : canonicalMembers) {
              if (canonicalName.equals(canonicalMember instanceof Method method ?
                                         ReflectionHelper.getFieldName(method) :
                                         canonicalMember.getName())) {
                // found a @CanonicalField/@CanonicalGetter for the same name as the constructor parameter
                // in this case, the ordinal parameter of the annotation is ignored.
                members[i] = canonicalMember;
                break;
              }
            }
            if (members[i] == null) {
              try {
                members[i] = getDeclaredField(clazz, canonicalNames[i]);
              }
              catch (NoSuchFieldException e) {
                // try to find a matching getter
                Class<?> parameterType = constructor.getParameterTypes()[i];
                String methodName = (Boolean.TYPE.equals(parameterType) ? "is" : "get") +
                                    StringHelper.firstToUpper(canonicalNames[i]);
                try {
                  Method method = clazz.getMethod(methodName);
                  if (ReflectionHelper.isGetter(method) && method.getReturnType().equals(parameterType)) {
                    members[i] = method;
                  }
                  else {
                    throw new TripRuntimeException("method " + methodName + " is not a getter for "
                                                   + canonicalNames[i], e);
                  }
                }
                catch (NoSuchMethodException ex) {
                  throw new TripRuntimeException("cannot find field or getter for member " + canonicalNames[i]
                                                 + " in " + clazz, e);
                }
              }
            }
          }
        }
        // fill up the missing members from the annotated canonical members
        // assumed that the ordinals in the annotations are in the correct order (value is irrelevant)
        int memberIndex = 0;
        for (int i = 0; i < members.length; i++) {
          if (members[i] == null && memberIndex < canonicalMembers.length) {
            members[i] = canonicalMembers[memberIndex++];
          }
        }
        canonicalMembers = members;
      }
    }
    return canonicalMembers;
  }

  /**
   * Finds a declared field in the given class or one of its superclasses.
   *
   * @param clazz the class
   * @param name the field name
   * @return the field
   * @throws NoSuchFieldException if no such field
   */
  protected Field getDeclaredField(Class<?> clazz, String name) throws NoSuchFieldException {
    for (Class<?> cls = clazz; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
      try {
        return cls.getDeclaredField(name);
      }
      catch (NoSuchFieldException e) {
        // try superclass
      }
    }
    throw new NoSuchFieldException(name + " in " + clazz);
  }

  /**
   * Creates the canonical constructor's argument array, preset with the parameters' default values.
   * <p>
   * Arguments that are not provided by the stream keep their default, so that a component missing
   * from the sender's class does not prevent the object from being created. Without the presets,
   * a missing primitive argument would make {@link Constructor#newInstance(Object...)} fail.
   *
   * @return the arguments, all at their default value
   */
  protected Object[] createCanonicalArgs() {
    return canonicalArgDefaults.clone();
  }

  /**
   * Creates the object from the canonical constructor's arguments.<br>
   * Applies the initializer and registers the object for back-references, if any.
   *
   * @param stream the stream to read from
   * @param reference the optional reference
   * @param initializer the object initializer invoked after the new object is created
   * @param canonicalArgs the constructor's arguments
   * @return the object, never null
   */
  protected T createFromCanonicalArgs(TripStream stream, Reference<T> reference,
                                      Consumer<T> initializer, Object[] canonicalArgs) {
    T value;
    try {
      try {
        value = canonicalConstructor.newInstance(canonicalArgs);
      }
      catch (IllegalAccessException ix) {
        canonicalConstructor.setAccessible(true);
        value = canonicalConstructor.newInstance(canonicalArgs);
      }
      if (initializer != null) {
        initializer.accept(value);
      }
    }
    catch (InaccessibleObjectException | InstantiationException | IllegalAccessException |
           InvocationTargetException e) {
      throw new TripInstantiationException(getObjectClass(), "cannot create instance via canonical constructor", e);
    }
    if (reference != null) {
      stream.addReference(reference, value, isDeduplicateable(value));
      // from now on, components may back-reference the object
    }
    return value;
  }

  /**
   * Gets the index in the canonical constructor of the component with the given ordinal.
   * <p>
   * The lookup is precomputed, so this is an array load rather than the linear name scan of
   * {@link #getCanonicalIndex(String)}, which would otherwise run for every component of every
   * object read.
   *
   * @param ordinal the ordinal of the component
   * @return the index, or -1 if not a canonical component or the ordinal is out of range
   */
  protected int getCanonicalIndex(int ordinal) {
    return ordinal >= 0 && ordinal < canonicalIndexByOrdinal.length ? canonicalIndexByOrdinal[ordinal] : -1;
  }

  /**
   * Gets the index of the given field in the canonical constructor.
   *
   * @param componentName the name of the component
   * @return the index, or -1 if not a canonical component
   */
  protected int getCanonicalIndex(String componentName) {
    Member[] members = getCanonicalMembers();
    for (int i = 0; i < members.length; i++) {
      Member member = members[i];
      if ((member instanceof Field field &&
           field.getName().equals(componentName))
          ||
          (member instanceof Method method &&
           ReflectionHelper.getFieldName(method).equals(componentName))) {
        return i;
      }
    }
    return -1;  // not a canonical field
  }

}
