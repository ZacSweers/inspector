package io.sweers.inspector.retrofit;

import io.sweers.inspector.Inspector;
import io.sweers.inspector.ValidationException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * An example implementation of a {@link Retrofit} {@link Converter.Factory} that delegates to a
 * later converter to create the response type, then validates it and throws it out if it fails
 * validation.
 */
public final class InspectorConverterFactory extends Converter.Factory {
  private final Inspector inspector;
  private final ValidationExceptionCallback callback;

  public InspectorConverterFactory(Inspector inspector, ValidationExceptionCallback callback) {
    this.inspector = inspector;
    this.callback = callback;
  }

  @Override public Converter<ResponseBody, ?> responseBodyConverter(Type type,
      Annotation[] annotations,
      Retrofit retrofit) {

    Converter<ResponseBody, ?> delegateConverter =
        retrofit.nextResponseBodyConverter(this, type, annotations);

    return new InspectorResponseConverter(type, inspector, callback, delegateConverter);
  }

  private static class InspectorResponseConverter implements Converter<ResponseBody, Object> {

    private final Type type;
    private final Inspector inspector;
    private final ValidationExceptionCallback callback;
    private final Converter<ResponseBody, ?> delegateConverter;

    InspectorResponseConverter(Type type,
        Inspector inspector,
        ValidationExceptionCallback callback,
        Converter<ResponseBody, ?> delegateConverter) {
      this.type = type;
      this.inspector = inspector;
      this.callback = callback;
      this.delegateConverter = delegateConverter;
    }

    @Override public Object convert(ResponseBody value) throws IOException {
      Object convert = delegateConverter.convert(value);
      try {
        inspector.validator(type)
            .validate(convert);
      } catch (ValidationException validationException) {
        callback.onValidationException(type, validationException);
      }
      return convert;
    }
  }

  /**
   * A callback to be notified on validation exceptions and potentially act on them. Use cases could
   * include logging, throwing, etc.
   */
  public interface ValidationExceptionCallback {

    /**
     * The callback method with the validation exception passed in.
     *
     * @param type the original type that failed.
     * @param exception the {@link ValidationException}.
     */
    void onValidationException(Type type, ValidationException exception) throws IOException;
  }
}
