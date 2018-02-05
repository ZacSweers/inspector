package io.sweers.inspector.sample.retrofit;

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

  public InspectorConverterFactory(Inspector inspector) {
    this.inspector = inspector;
  }

  @Override public Converter<ResponseBody, ?> responseBodyConverter(Type type,
      Annotation[] annotations,
      Retrofit retrofit) {

    Converter<ResponseBody, ?> delegateConverter =
        retrofit.nextResponseBodyConverter(this, type, annotations);

    return new InspectorResponseConverter(type, inspector, delegateConverter);
  }

  private static class InspectorResponseConverter implements Converter<ResponseBody, Object> {

    private final Type type;
    private final Inspector inspector;
    private final Converter<ResponseBody, ?> delegateConverter;

    InspectorResponseConverter(Type type,
        Inspector inspector,
        Converter<ResponseBody, ?> delegateConverter) {
      this.type = type;
      this.inspector = inspector;
      this.delegateConverter = delegateConverter;
    }

    @Override public Object convert(ResponseBody value) throws IOException {
      Object convert = delegateConverter.convert(value);
      try {
        inspector.validator(type)
            .validate(convert);
      } catch (ValidationException validationException) {
        // This response didn't pass validation, throw the exception.
        System.out.println("Validation exception: "
            + type
            + ". Error: "
            + validationException.getMessage());
        throw new IOException(validationException);
      }
      return convert;
    }
  }
}
