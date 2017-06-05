package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import javax.annotation.Nullable;

final class AdapterMethodsFactory implements Validator.Factory {
  @Nullable @Override public Validator<?> create(Type type,
      Set<? extends Annotation> annotations,
      Inspector inspector) {
    return null;
  }
  //private final List<AdapterMethod> toAdapters;
  //private final List<AdapterMethod> fromAdapters;
  //
  //AdapterMethodsFactory(List<AdapterMethod> toAdapters, List<AdapterMethod> fromAdapters) {
  //  this.toAdapters = toAdapters;
  //  this.fromAdapters = fromAdapters;
  //}

  //@Override public @Nullable Validator<?> create(
  //    final Type type, final Set<? extends Annotation> annotations, final Inspector inspector) {
  //  final AdapterMethod toAdapter = get(toAdapters, type, annotations);
  //  final AdapterMethod fromAdapter = get(fromAdapters, type, annotations);
  //  if (toAdapter == null && fromAdapter == null) return null;
  //
  //  final Validator<Object> delegate;
  //  if (toAdapter == null || fromAdapter == null) {
  //    try {
  //      delegate = inspector.nextValidator(this, type, annotations);
  //    } catch (IllegalArgumentException e) {
  //      String missingAnnotation = toAdapter == null ? "@ToJson" : "@FromJson";
  //      throw new IllegalArgumentException("No " + missingAnnotation + " adapter for "
  //          + type + " annotated " + annotations);
  //    }
  //  } else {
  //    delegate = null;
  //  }
  //
  //  if (toAdapter != null) toAdapter.bind(inspector, this);
  //  if (fromAdapter != null) fromAdapter.bind(inspector, this);
  //
  //  return new Validator<Object>() {
  //    @Override public void toJson(JsonWriter writer, @Nullable Object value) throws IOException {
  //      if (toAdapter == null) {
  //        delegate.toJson(writer, value);
  //      } else if (!toAdapter.nullable && value == null) {
  //        writer.nullValue();
  //      } else {
  //        try {
  //          toAdapter.toJson(inspector, writer, value);
  //        } catch (InvocationTargetException e) {
  //          Throwable cause = e.getCause();
  //          if (cause instanceof IOException) throw (IOException) cause;
  //          throw new JsonDataException(cause + " at " + writer.getPath(), cause);
  //        }
  //      }
  //    }
  //
  //    @Override public @Nullable Object fromJson(JsonReader reader) throws IOException {
  //      if (fromAdapter == null) {
  //        return delegate.fromJson(reader);
  //      } else if (!fromAdapter.nullable && reader.peek() == JsonReader.Token.NULL) {
  //        reader.nextNull();
  //        return null;
  //      } else {
  //        try {
  //          return fromAdapter.fromJson(inspector, reader);
  //        } catch (InvocationTargetException e) {
  //          Throwable cause = e.getCause();
  //          if (cause instanceof IOException) throw (IOException) cause;
  //          throw new JsonDataException(cause + " at " + reader.getPath(), cause);
  //        }
  //      }
  //    }
  //
  //    @Override public String toString() {
  //      return "Validator" + annotations + "(" + type + ")";
  //    }
  //  };
  //}
  //
  //public static AdapterMethodsFactory get(Object adapter) {
  //  List<AdapterMethod> toAdapters = new ArrayList<>();
  //  List<AdapterMethod> fromAdapters = new ArrayList<>();
  //
  //  for (Class<?> c = adapter.getClass(); c != Object.class; c = c.getSuperclass()) {
  //    for (Method m : c.getDeclaredMethods()) {
  //      if (m.isAnnotationPresent(Validate.class)) {
  //        AdapterMethod toAdapter = toAdapter(adapter, m);
  //        AdapterMethod conflicting = get(toAdapters, toAdapter.type, toAdapter.annotations);
  //        if (conflicting != null) {
  //          throw new IllegalArgumentException("Conflicting @ToJson methods:\n"
  //              + "    " + conflicting.method + "\n"
  //              + "    " + toAdapter.method);
  //        }
  //        toAdapters.add(toAdapter);
  //      }
  //
  //      if (m.isAnnotationPresent(FromJson.class)) {
  //        AdapterMethod fromAdapter = fromAdapter(adapter, m);
  //        AdapterMethod conflicting = get(fromAdapters, fromAdapter.type, fromAdapter.annotations);
  //        if (conflicting != null) {
  //          throw new IllegalArgumentException("Conflicting @FromJson methods:\n"
  //              + "    " + conflicting.method + "\n"
  //              + "    " + fromAdapter.method);
  //        }
  //        fromAdapters.add(fromAdapter);
  //      }
  //    }
  //  }
  //
  //  if (toAdapters.isEmpty() && fromAdapters.isEmpty()) {
  //    throw new IllegalArgumentException("Expected at least one @ToJson or @FromJson method on "
  //        + adapter.getClass().getName());
  //  }
  //
  //  return new AdapterMethodsFactory(toAdapters, fromAdapters);
  //}
  //
  ///**
  // * Returns an object that calls a {@code method} method on {@code adapter} in service of
  // * converting an object to JSON.
  // */
  //static AdapterMethod toAdapter(Object adapter, Method method) {
  //  method.setAccessible(true);
  //  final Type returnType = method.getGenericReturnType();
  //  final Type[] parameterTypes = method.getGenericParameterTypes();
  //  final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
  //
  //  if (parameterTypes.length >= 2
  //      && parameterTypes[0] == JsonWriter.class
  //      && returnType == void.class
  //      && parametersAreValidators(2, parameterTypes)) {
  //    // void pointToJson(JsonWriter jsonWriter, Point point) {
  //    // void pointToJson(JsonWriter jsonWriter, Point point, Validator<?> adapter, ...) {
  //    Set<? extends Annotation> qualifierAnnotations = jsonAnnotations(parameterAnnotations[1]);
  //    return new AdapterMethod(parameterTypes[1], qualifierAnnotations, adapter, method,
  //        parameterTypes.length, 2, true) {
  //      @Override public void toJson(Inspector inspector, JsonWriter writer, @Nullable Object value)
  //          throws IOException, InvocationTargetException {
  //        invoke(writer, value);
  //      }
  //    };
  //
  //  } else if (parameterTypes.length == 1 && returnType != void.class) {
  //    // List<Integer> pointToJson(Point point) {
  //    final Set<? extends Annotation> returnTypeAnnotations = jsonAnnotations(method);
  //    Set<? extends Annotation> qualifierAnnotations = jsonAnnotations(parameterAnnotations[0]);
  //    boolean nullable = Util.hasNullable(parameterAnnotations[0]);
  //    return new AdapterMethod(parameterTypes[0], qualifierAnnotations, adapter, method,
  //        parameterTypes.length, 1, nullable) {
  //      private Validator<Object> delegate;
  //
  //      @Override public void bind(Inspector inspector, Validator.Factory factory) {
  //        super.bind(inspector, factory);
  //        delegate = inspector.validator(returnType, returnTypeAnnotations);
  //      }
  //
  //      @Override public void toJson(Inspector inspector, JsonWriter writer, @Nullable Object value)
  //          throws IOException, InvocationTargetException {
  //        Object intermediate = invoke(value);
  //        delegate.toJson(writer, intermediate);
  //      }
  //    };
  //
  //  } else {
  //    throw new IllegalArgumentException("Unexpected signature for " + method + ".\n"
  //        + "@ToJson method signatures may have one of the following structures:\n"
  //        + "    <any access modifier> void toJson(JsonWriter writer, T value) throws <any>;\n"
  //        + "    <any access modifier> void toJson(JsonWriter writer,"
  //        + " Validator<any> delegate, <any more delegates>) throws <any>;\n"
  //        + "    <any access modifier> void toJson(JsonWriter writer, T value"
  //        + " Validator<any> delegate, <any more delegates>) throws <any>;\n"
  //        + "    <any access modifier> R toJson(T value) throws <any>;\n");
  //  }
  //}
  //
  ///** Returns true if {@code parameterTypes[offset..]} contains only Validators. */
  //private static boolean parametersAreValidators(int offset, Type[] parameterTypes) {
  //  for (int i = offset, length = parameterTypes.length; i < length; i++) {
  //    if (!(parameterTypes[i] instanceof ParameterizedType)) return false;
  //    if (((ParameterizedType) parameterTypes[i]).getRawType() != Validator.class) return false;
  //  }
  //  return true;
  //}
  //
  ///**
  // * Returns an object that calls a {@code method} method on {@code adapter} in service of
  // * converting an object from JSON.
  // */
  //static AdapterMethod fromAdapter(Object adapter, Method method) {
  //  method.setAccessible(true);
  //  final Type returnType = method.getGenericReturnType();
  //  Set<? extends Annotation> returnTypeAnnotations = jsonAnnotations(method);
  //  final Type[] parameterTypes = method.getGenericParameterTypes();
  //  Annotation[][] parameterAnnotations = method.getParameterAnnotations();
  //
  //  if (parameterTypes.length >= 1
  //      && parameterTypes[0] == JsonReader.class
  //      && returnType != void.class
  //      && parametersAreValidators(1, parameterTypes)) {
  //    // Point pointFromJson(JsonReader jsonReader) {
  //    // Point pointFromJson(JsonReader jsonReader, Validator<?> adapter, ...) {
  //    return new AdapterMethod(returnType, returnTypeAnnotations, adapter, method,
  //        parameterTypes.length, 1, true) {
  //      @Override public Object fromJson(Inspector inspector, JsonReader reader)
  //          throws IOException, InvocationTargetException {
  //        return invoke(reader);
  //      }
  //    };
  //
  //  } else if (parameterTypes.length == 1 && returnType != void.class) {
  //    // Point pointFromJson(List<Integer> o) {
  //    final Set<? extends Annotation> qualifierAnnotations
  //        = validationAnnotations(parameterAnnotations[0]);
  //    boolean nullable = Util.hasNullable(parameterAnnotations[0]);
  //    return new AdapterMethod(returnType, returnTypeAnnotations, adapter, method,
  //        parameterTypes.length, 1, nullable) {
  //      Validator<Object> delegate;
  //
  //      @Override public void bind(Inspector inspector, Validator.Factory factory) {
  //        super.bind(inspector, factory);
  //        delegate = inspector.validator(parameterTypes[0], qualifierAnnotations);
  //      }
  //
  //      @Override public Object fromJson(Inspector inspector, JsonReader reader)
  //          throws IOException, InvocationTargetException {
  //        Object intermediate = delegate.fromJson(reader);
  //        return invoke(intermediate);
  //      }
  //    };
  //
  //  } else {
  //    throw new IllegalArgumentException("Unexpected signature for " + method + ".\n"
  //        + "@FromJson method signatures may have one of the following structures:\n"
  //        + "    <any access modifier> R fromJson(JsonReader jsonReader) throws <any>;\n"
  //        + "    <any access modifier> R fromJson(JsonReader jsonReader,"
  //        + " Validator<any> delegate, <any more delegates>) throws <any>;\n"
  //        + "    <any access modifier> R fromJson(T value) throws <any>;\n");
  //  }
  //}
  //
  ///** Returns the matching adapter method from the list. */
  //private static @Nullable AdapterMethod get(
  //    List<AdapterMethod> adapterMethods, Type type, Set<? extends Annotation> annotations) {
  //  for (int i = 0, size = adapterMethods.size(); i < size; i++) {
  //    AdapterMethod adapterMethod = adapterMethods.get(i);
  //    if (adapterMethod.type.equals(type) && adapterMethod.annotations.equals(annotations)) {
  //      return adapterMethod;
  //    }
  //  }
  //  return null;
  //}
  //
  //abstract static class AdapterMethod {
  //  final Type type;
  //  final Set<? extends Annotation> annotations;
  //  final Object adapter;
  //  final Method method;
  //  final int adaptersOffset;
  //  final Validator<?>[] jsonAdapters;
  //  final boolean nullable;
  //
  //  AdapterMethod(Type type, Set<? extends Annotation> annotations, Object adapter,
  //      Method method, int parameterCount, int adaptersOffset, boolean nullable) {
  //    this.type = Types.canonicalize(type);
  //    this.annotations = annotations;
  //    this.adapter = adapter;
  //    this.method = method;
  //    this.adaptersOffset = adaptersOffset;
  //    this.jsonAdapters = new Validator[parameterCount - adaptersOffset];
  //    this.nullable = nullable;
  //  }
  //
  //  public void bind(Inspector inspector, Validator.Factory factory) {
  //    if (jsonAdapters.length > 0) {
  //      Type[] parameterTypes = method.getGenericParameterTypes();
  //      Annotation[][] parameterAnnotations = method.getParameterAnnotations();
  //      for (int i = adaptersOffset, size = parameterTypes.length; i < size; i++) {
  //        Type type = ((ParameterizedType) parameterTypes[i]).getActualTypeArguments()[0];
  //        Set<? extends Annotation> jsonAnnotations = jsonAnnotations(parameterAnnotations[i]);
  //        jsonAdapters[i - adaptersOffset] =
  //            Types.equals(this.type, type) && annotations.equals(jsonAnnotations)
  //                ? inspector.nextValidator(factory, type, jsonAnnotations)
  //                : inspector.validator(type, jsonAnnotations);
  //      }
  //    }
  //  }
  //
  //  public void toJson(Inspector inspector, JsonWriter writer, @Nullable Object value)
  //      throws IOException, InvocationTargetException {
  //    throw new AssertionError();
  //  }
  //
  //  public @Nullable Object fromJson(Inspector inspector, JsonReader reader)
  //      throws IOException, InvocationTargetException {
  //    throw new AssertionError();
  //  }
  //
  //  /** Invoke the method with one fixed argument, plus any number of JSON adapter arguments. */
  //  protected @Nullable Object invoke(@Nullable Object a1) throws InvocationTargetException {
  //    Object[] args = new Object[1 + jsonAdapters.length];
  //    args[0] = a1;
  //    System.arraycopy(jsonAdapters, 0, args, 1, jsonAdapters.length);
  //
  //    try {
  //      return method.invoke(adapter, args);
  //    } catch (IllegalAccessException e) {
  //      throw new AssertionError();
  //    }
  //  }
  //
  //  /** Invoke the method with two fixed arguments, plus any number of JSON adapter arguments. */
  //  protected Object invoke(@Nullable Object a1, @Nullable Object a2)
  //      throws InvocationTargetException {
  //    Object[] args = new Object[2 + jsonAdapters.length];
  //    args[0] = a1;
  //    args[1] = a2;
  //    System.arraycopy(jsonAdapters, 0, args, 2, jsonAdapters.length);
  //
  //    try {
  //      return method.invoke(adapter, args);
  //    } catch (IllegalAccessException e) {
  //      throw new AssertionError();
  //    }
  //  }
  //}
}
