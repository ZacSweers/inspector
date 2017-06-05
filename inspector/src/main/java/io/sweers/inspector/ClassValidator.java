package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Emits a validator that validates validatable methods of a class.
 *
 * <h3>Platform Types</h3>
 * Fields from platform classes are omitted from both serialization and deserialization unless
 * they are either public or protected. This includes the following packages and their subpackages:
 *
 * <ul>
 * <li>android.*
 * <li>java.*
 * <li>javax.*
 * <li>kotlin.*
 * <li>scala.*
 * </ul>
 */
final class ClassValidator<T> extends Validator<T> {
  public static final Validator.Factory FACTORY = new Validator.Factory() {
    @Override public @Nullable Validator<?> create(Type type,
        Set<? extends Annotation> annotations,
        Inspector inspector) {
      Class<?> rawType = Types.getRawType(type);
      if (rawType.isInterface() || rawType.isEnum()) return null;
      if (isPlatformType(rawType) && !Types.isAllowedPlatformType(rawType)) {
        throw new IllegalArgumentException("Platform "
            + type
            + " annotated "
            + annotations
            + " requires explicit Validator to be registered");
      }
      if (!annotations.isEmpty()) return null;

      if (rawType.getEnclosingClass() != null && !Modifier.isStatic(rawType.getModifiers())) {
        if (rawType.getSimpleName()
            .isEmpty()) {
          throw new IllegalArgumentException("Cannot validate anonymous class "
              + rawType.getName());
        } else {
          throw new IllegalArgumentException("Cannot validate non-static nested class "
              + rawType.getName());
        }
      }
      if (Modifier.isAbstract(rawType.getModifiers())) {
        throw new IllegalArgumentException("Cannot validate abstract class " + rawType.getName());
      }

      ClassFactory<Object> classFactory = ClassFactory.get(rawType);
      Map<String, MethodBinding<?>> methods = new TreeMap<>();
      for (Type t = type; t != Object.class; t = Types.getGenericSuperclass(t)) {
        createMethodBindings(inspector, t, methods);
      }
      return new ClassValidator<>(classFactory, methods).nullSafe();
    }

    /** Creates a method binding for each of declared method of {@code type}. */
    private void createMethodBindings(Inspector inspector,
        Type type,
        Map<String, MethodBinding<?>> methodBindings) {
      Class<?> rawType = Types.getRawType(type);
      boolean platformType = isPlatformType(rawType);
      for (final Method method : rawType.getDeclaredMethods()) {
        if (!includeMethod(platformType, method.getModifiers())) continue;
        if (method.getParameterTypes().length != 0) continue;
        if (method.getAnnotation(InspectorIgnored.class) != null) continue;

        // Look up a type validator for this type.
        Type returnType = Types.resolve(type, rawType, method.getGenericReturnType());
        Set<? extends Annotation> annotations = Util.validationAnnotations(method);
        Validator<Object> validator;
        ValidatedBy validatedBy = method.getAnnotation(ValidatedBy.class);
        if (validatedBy != null) {
          try {
            //noinspection unchecked
            validator = (Validator<Object>) validatedBy.value()
                .newInstance();
          } catch (InstantiationException e) {
            throw new RuntimeException("Could not instantiate delegate validator "
                + validatedBy.value()
                + " for "
                + method.getName()
                + ". Make sure it has a public default constructor.");
          } catch (IllegalAccessException e) {
            throw new RuntimeException("Delegate validator "
                + validatedBy.value()
                + " for "
                + method.getName()
                + " is not accessible. Make sure it has a public default constructor.");
          }
        } else {
          validator = inspector.validator(returnType, annotations);
        }

        if (!method.getReturnType()
            .isPrimitive() && !Util.hasNullable(method.getDeclaredAnnotations())) {
          validator = new Validator<Object>() {
            @Override public void validate(Object object) throws ValidationException {
              Object result;
              try {
                result = method.invoke(object);
              } catch (IllegalAccessException e) {
                // Shouldn't happen, but just in case
                throw new ValidationException(method.getName() + " is inaccessible.", e);
              } catch (InvocationTargetException e) {
                throw new ValidationException(method.getName() + " threw an exception when called.",
                    e);
              }
              if (result == null) {
                throw new ValidationException("Returned value of "
                    + method.getName()
                    + "() was null.");
              }
            }
          };
        }

        // Create the binding between method and validator.
        method.setAccessible(true);

        // Store it using the method's name. If there was already a method with this name, fail!
        String name = method.getName();
        MethodBinding<Object> methodBinding = new MethodBinding<>(name, method, validator);
        MethodBinding<?> replaced = methodBindings.put(name, methodBinding);
        if (replaced != null) {
          throw new IllegalArgumentException("Conflicting methods:\n"
              + "    "
              + replaced.method
              + "\n"
              + "    "
              + methodBinding.method);
        }
      }
    }

    /** Returns true if methods with {@code modifiers} are included in the emitted validator. */
    private boolean includeMethod(boolean platformType, int modifiers) {
      if (Modifier.isStatic(modifiers)) return false;
      return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers) || !platformType;
    }
  };

  /**
   * Returns true if {@code rawType} is built in. We don't reflect on private methods of platform
   * types because they're unspecified and likely to be different on Java vs. Android.
   */
  static boolean isPlatformType(Class<?> rawType) {
    String name = rawType.getName();
    return name.startsWith("android.")
        || name.startsWith("java.")
        || name.startsWith("javax.")
        || name.startsWith("kotlin.")
        || name.startsWith("scala.");
  }

  private final ClassFactory<T> classFactory;
  private final MethodBinding<?>[] methodsArray;

  ClassValidator(ClassFactory<T> classFactory, Map<String, MethodBinding<?>> methodsMap) {
    this.classFactory = classFactory;
    this.methodsArray = methodsMap.values()
        .toArray(new MethodBinding[methodsMap.size()]);
  }

  @Override public String toString() {
    return "Validator(" + classFactory + ")";
  }

  @Override public void validate(T object) throws ValidationException {
    try {
      for (MethodBinding<?> methodBinding : methodsArray) {
        methodBinding.validate(object);
      }
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    }
  }

  static class MethodBinding<T> {
    final String name;
    final Method method;
    final Validator<T> validator;

    MethodBinding(String name, Method method, Validator<T> validator) {
      this.name = name;
      this.method = method;
      this.validator = validator;
    }

    @SuppressWarnings("unchecked") void validate(Object object) throws IllegalAccessException {
      validator.validate((T) object);
    }
  }
}
