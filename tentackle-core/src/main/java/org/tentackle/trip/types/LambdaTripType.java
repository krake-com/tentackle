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

import org.tentackle.log.Logger;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripSerializeLambda;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Wrapper type for lambdas.<br>
 * By default, lambda serialization is disabled.
 * It must be enabled by annotating the capturing class with {@link org.tentackle.trip.TripSerializeLambda}.
 */
// created by ObjectTypeBuilder, not mapped by @TripTypeService
public final class LambdaTripType extends AbstractDictionaryBasedTripType<Object> {

  private static final Logger LOGGER = Logger.get(LambdaTripType.class);

  private static final String LAMBDA_DESERIALIZE_METHOD = "$deserializeLambda$";
  private static final String WRITE_REPLACE_METHOD = "writeReplace";
  private static final String READ_RESOLVE_METHOD = "readResolve";
  private static final String CAPTURING_CLASS_FIELD = "capturingClass";

  private static boolean initialized;
  private static Method readResolveMethod;
  private static Field capturingClassField;

  private static TripType<Integer> INTEGER;
  private static TripType<String> STRING;
  @SuppressWarnings("rawtypes")
  private static TripType<Class> CLASS;


  /**
   * Creates the type.
   */
  public LambdaTripType() {
    super(Object.class);
    // initialize() cannot be called here, since it will fail due to NPE via DefaultOpCode
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public boolean isReferencable(Object value) {
    return false;
  }

  @Override
  public boolean isDictionaryBased() {
    return false;
  }

  @Override
  public void write(Object value, TripStream stream) {
    initialize();
    SerializedLambda wrapper = createWrapper(value);
    Serializer serializer = stream.getSerializer();

    Class<?> capturingClass = getCapturingClass(wrapper, stream);
    if (!capturingClass.isAnnotationPresent(TripSerializeLambda.class)) {
      throw new TripRuntimeException("cannot serialize lambda " + value + " because its capturing class " +
                                      capturingClass.getName() + " is not annotated with @TripSerializeLambda");
    }

    serializer.serializeOpCode(DefaultOpCode.LAMBDA);
    CLASS.write(capturingClass, stream);
    STRING.write(wrapper.getFunctionalInterfaceClass(), stream);
    STRING.write(wrapper.getFunctionalInterfaceMethodName(), stream);
    STRING.write(wrapper.getFunctionalInterfaceMethodSignature(), stream);
    INTEGER.write(wrapper.getImplMethodKind(), stream);
    STRING.write(wrapper.getImplClass(), stream);
    STRING.write(wrapper.getImplMethodName(), stream);
    STRING.write(wrapper.getImplMethodSignature(), stream);
    STRING.write(wrapper.getInstantiatedMethodType(), stream);

    int argCount = wrapper.getCapturedArgCount();
    INTEGER.write(argCount, stream);
    for (int i = 0; i < argCount; i++) {
      Object arg = wrapper.getCapturedArg(i);
      TripType<Object> type = stream.getType(arg.getClass());
      type.write(arg, stream);
    }
  }

  @Override
  public Object read(TripStream stream, OpCode opCode) {
    initialize();
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.LAMBDA) {
      Class<?> capturingClass = CLASS.read(stream, deserializer.deserializeOpCode());
      String functionalInterfaceClassName = STRING.read(stream, deserializer.deserializeOpCode());
      String functionalInterfaceMethodName = STRING.read(stream, deserializer.deserializeOpCode());
      String functionalInterfaceMethodSignature = STRING.read(stream, deserializer.deserializeOpCode());
      int implMethodKind = INTEGER.read(stream, deserializer.deserializeOpCode());
      String implClassName = STRING.read(stream, deserializer.deserializeOpCode());
      String implMethodName = STRING.read(stream, deserializer.deserializeOpCode());
      String implMethodSignature = STRING.read(stream, deserializer.deserializeOpCode());
      String instantiatedMethodType = STRING.read(stream, deserializer.deserializeOpCode());

      int argCount = INTEGER.read(stream, deserializer.deserializeOpCode());
      Object[] args = new Object[argCount];
      for (int i = 0; i < argCount; i++) {
        opCode = deserializer.deserializeOpCode();
        TripType<?> argType = stream.deriveType(opCode);
        if (argType.isDictionaryBased()) {
          opCode = deserializer.deserializeOpCode();
        }
        args[i] = argType.read(stream, opCode);
      }
      SerializedLambda wrapper = new SerializedLambda(capturingClass, functionalInterfaceClassName,
                                                      functionalInterfaceMethodName, functionalInterfaceMethodSignature,
                                                      implMethodKind, implClassName, implMethodName,
                                                      implMethodSignature, instantiatedMethodType, args);
      return readResolve(capturingClass, wrapper);
    }
    else {
      return super.read(stream, opCode);
    }
  }


  private SerializedLambda createWrapper(Object value) {
    try {
      Method writeReplace = value.getClass().getDeclaredMethod(WRITE_REPLACE_METHOD);
      writeReplace.setAccessible(true);
      return (SerializedLambda) writeReplace.invoke(value);
    }
    catch (Exception ex) {
      throw new TripRuntimeException("cannot create SerializedLambda for " + value.getClass(), ex);
    }
  }

  private static Class<?> getCapturingClass(SerializedLambda wrapper, TripStream stream) {
    if (capturingClassField != null) {
      try {
        return (Class<?>) capturingClassField.get(wrapper);
      }
      catch (Exception ex) {
        LOGGER.warning("access to SerializedLambda#capturingClass denied unexpectedly -> using fallback Class.forName()", ex);
      }
    }
    return stream.getClass(wrapper.getCapturingClass().replace('/', '.'));
  }

  private Object readResolve (Class<?> capturingClass, SerializedLambda wrapper) {
    if (readResolveMethod != null) {
      try {
        return readResolveMethod.invoke(wrapper);
      }
      catch (Exception ex) {
        throw new TripRuntimeException("readResolve failed for lambda " + wrapper, ex);
      }
    }
    else {
      try {
        Method method = capturingClass.getDeclaredMethod(LAMBDA_DESERIALIZE_METHOD, SerializedLambda.class);
        method.setAccessible(true);
        return method.invoke(null, wrapper);
      }
      catch (Exception ex) {
        throw new TripRuntimeException("deserialization failed for lambda " + wrapper, ex);
      }
    }
  }


  /**
   * Initializes the static fields.<br>
   * In modular applications this will always evaluate to the fallbacks
   * unless explicitly allowed via a command line option.
   */
  private static void initialize() {
    if (!initialized) {
      TripFactory factory = TripFactory.getInstance();
      INTEGER = factory.getType(Integer.TYPE);
      STRING = factory.getType(String.class);
      CLASS = factory.getType(Class.class);
      if (readResolveMethod == null) {
        try {
          readResolveMethod = SerializedLambda.class.getDeclaredMethod(READ_RESOLVE_METHOD);
          readResolveMethod.setAccessible(true);
        }
        catch (Exception ex) {
          readResolveMethod = null;
          LOGGER.warning(ex.getMessage() + " -> using fallback " + LAMBDA_DESERIALIZE_METHOD);
        }
      }
      if (capturingClassField == null) {
        try {
          capturingClassField = SerializedLambda.class.getDeclaredField(CAPTURING_CLASS_FIELD);
          capturingClassField.setAccessible(true);
        }
        catch (Exception ex) {
          capturingClassField = null;
          LOGGER.warning(ex.getMessage() + " -> using fallback Class.forName()");
        }
      }
      initialized = true;
    }
  }

}
