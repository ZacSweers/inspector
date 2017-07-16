package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Validates objects.
 */
public final class Inspector {
  static final List<Validator.Factory> BUILT_IN_FACTORIES = new ArrayList<>(5);

  static {
    BUILT_IN_FACTORIES.add(StandardValidators.FACTORY);
    BUILT_IN_FACTORIES.add(CollectionValidator.FACTORY);
    BUILT_IN_FACTORIES.add(MapValidator.FACTORY);
    BUILT_IN_FACTORIES.add(ArrayValidator.FACTORY);
    BUILT_IN_FACTORIES.add(ClassValidator.FACTORY);
  }

  private final List<Validator.Factory> factories;
  private final ThreadLocal<List<DeferredAdapter<?>>> reentrantCalls = new ThreadLocal<>();
  private final Map<Object, Validator<?>> adapterCache = new LinkedHashMap<>();

  Inspector(Builder builder) {
    List<Validator.Factory> factories =
        new ArrayList<>(builder.factories.size() + BUILT_IN_FACTORIES.size());
    factories.addAll(builder.factories);
    factories.addAll(BUILT_IN_FACTORIES);
    this.factories = Collections.unmodifiableList(factories);
  }

  /** Returns a validator for {@code type}, creating it if necessary. */
  public <T> Validator<T> validator(Type type) {
    return validator(type, Util.NO_ANNOTATIONS);
  }

  /** Returns a validator for {@code type}, creating it if necessary. */
  public <T> Validator<T> validator(Class<T> type) {
    return validator(type, Util.NO_ANNOTATIONS);
  }


  /** Returns a validator for {@code type} with {@code annotationType}, creating it if necessary. */
  public <T> Validator<T> validator(Type type, Class<? extends Annotation> annotationType) {
    return validator(type,
        Collections.singleton(Types.createValidationQualifierImplementation(annotationType)));
  }

  /** Returns a validator for {@code type} and {@code annotations}, creating it if necessary. */
  @SuppressWarnings("unchecked") // Factories are required to return only matching Validators.
  public <T> Validator<T> validator(Type type, Set<? extends Annotation> annotations) {
    type = Types.canonicalize(type);

    // If there's an equivalent adapter in the cache, we're done!
    Object cacheKey = cacheKey(type, annotations);
    synchronized (adapterCache) {
      Validator<?> result = adapterCache.get(cacheKey);
      if (result != null) return (Validator<T>) result;
    }

    // Short-circuit if this is a reentrant call.
    List<DeferredAdapter<?>> deferredAdapters = reentrantCalls.get();
    if (deferredAdapters != null) {
      for (int i = 0, size = deferredAdapters.size(); i < size; i++) {
        DeferredAdapter<?> deferredAdapter = deferredAdapters.get(i);
        if (deferredAdapter.cacheKey.equals(cacheKey)) {
          return (Validator<T>) deferredAdapter;
        }
      }
    } else {
      deferredAdapters = new ArrayList<>();
      reentrantCalls.set(deferredAdapters);
    }

    // Prepare for re-entrant calls, then ask each factory to create a type adapter.
    DeferredAdapter<T> deferredAdapter = new DeferredAdapter<>(cacheKey);
    deferredAdapters.add(deferredAdapter);
    try {
      for (int i = 0, size = factories.size(); i < size; i++) {
        Validator<T> result = (Validator<T>) factories.get(i)
            .create(type, annotations, this);
        if (result != null) {
          deferredAdapter.ready(result);
          synchronized (adapterCache) {
            adapterCache.put(cacheKey, result);
          }
          return result;
        }
      }
    } finally {
      deferredAdapters.remove(deferredAdapters.size() - 1);
      if (deferredAdapters.isEmpty()) {
        reentrantCalls.remove();
      }
    }

    throw new IllegalArgumentException("No Validator for " + type + " annotated " + annotations);
  }


  /**
   * Returns a validator for {@code type} and {@code annotations}, always creating a new one and
   * skipping past {@code skipPast} for creation. */
  @SuppressWarnings("unchecked") // Factories are required to return only matching Validators.
  public <T> Validator<T> nextValidator(Validator.Factory skipPast,
      Type type,
      Set<? extends Annotation> annotations) {
    type = Types.canonicalize(type);

    int skipPastIndex = factories.indexOf(skipPast);
    if (skipPastIndex == -1) {
      throw new IllegalArgumentException("Unable to skip past unknown factory " + skipPast);
    }
    for (int i = skipPastIndex + 1, size = factories.size(); i < size; i++) {
      Validator<T> result = (Validator<T>) factories.get(i)
          .create(type, annotations, this);
      if (result != null) return result;
    }
    throw new IllegalArgumentException("No next Validator for "
        + type
        + " annotated "
        + annotations);
  }

  /** Returns a new builder containing all custom factories used by the current instance. */
  public Inspector.Builder newBuilder() {
    int fullSize = factories.size();
    int tailSize = BUILT_IN_FACTORIES.size();
    List<Validator.Factory> customFactories = factories.subList(0, fullSize - tailSize);
    return new Builder().addAll(customFactories);
  }

  /** Returns an opaque object that's equal if the type and annotations are equal. */
  private Object cacheKey(Type type, Set<? extends Annotation> annotations) {
    if (annotations.isEmpty()) return type;
    return Arrays.asList(type, annotations);
  }

  public static final class Builder {
    final List<Validator.Factory> factories = new ArrayList<>();

    public <T> Builder add(final Type type, final Validator<T> validator) {
      if (type == null) throw new IllegalArgumentException("type == null");
      if (validator == null) throw new IllegalArgumentException("validator == null");

      return add(new Validator.Factory() {
        @Override public @Nullable Validator<?> create(Type targetType,
            Set<? extends Annotation> annotations,
            Inspector inspector) {
          return annotations.isEmpty() && Util.typesMatch(type, targetType) ? validator : null;
        }
      });
    }

    public <T> Builder add(final Type type,
        final Class<? extends Annotation> annotation,
        final Validator<T> validator) {
      if (type == null) throw new IllegalArgumentException("type == null");
      if (annotation == null) throw new IllegalArgumentException("annotation == null");
      if (validator == null) throw new IllegalArgumentException("validator == null");
      if (!annotation.isAnnotationPresent(ValidationQualifier.class)) {
        throw new IllegalArgumentException(annotation + " does not have @ValidationQualifier");
      }
      if (annotation.getDeclaredMethods().length > 0) {
        throw new IllegalArgumentException("Use Validator.Factory for annotations with elements");
      }

      return add(new Validator.Factory() {
        @Override public @Nullable Validator<?> create(Type targetType,
            Set<? extends Annotation> annotations,
            Inspector inspector) {
          if (Util.typesMatch(type, targetType)
              && annotations.size() == 1
              && Util.isAnnotationPresent(annotations, annotation)) {
            return validator;
          }
          return null;
        }
      });
    }

    public Builder add(Validator.Factory factory) {
      if (factory == null) throw new IllegalArgumentException("factory == null");
      factories.add(factory);
      return this;
    }

    Builder addAll(List<Validator.Factory> factories) {
      this.factories.addAll(factories);
      return this;
    }

    public Inspector build() {
      return new Inspector(this);
    }
  }

  /**
   * Sometimes a type adapter factory depends on its own product; either directly or indirectly.
   * To make this work, we offer this type adapter stub while the final adapter is being computed.
   * When it is ready, we wire this to delegate to that finished adapter.
   *
   * <p>Typically this is necessary in self-referential object models, such as an {@code Employee}
   * class that has a {@code List<Employee>} field for an organization's management hierarchy.
   */
  private static class DeferredAdapter<T> extends Validator<T> {
    @Nullable Object cacheKey;
    @Nullable private Validator<T> delegate;

    DeferredAdapter(Object cacheKey) {
      this.cacheKey = cacheKey;
    }

    void ready(Validator<T> delegate) {
      this.delegate = delegate;
      this.cacheKey = null;
    }

    @Override public void validate(T object) throws ValidationException {
      if (delegate == null) throw new IllegalStateException("Validator isn't ready");
      delegate.validate(object);
    }
  }
}
