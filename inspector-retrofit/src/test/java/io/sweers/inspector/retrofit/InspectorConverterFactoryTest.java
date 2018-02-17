package io.sweers.inspector.retrofit;

import io.sweers.inspector.Inspector;
import io.sweers.inspector.SelfValidating;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.retrofit.InspectorConverterFactory.ValidationExceptionCallback;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;

import static com.google.common.truth.Truth.assertThat;

public final class InspectorConverterFactoryTest {

  public static class Value implements SelfValidating {
    final String name;

    public Value(String name) {
      this.name = name;
    }

    @Override public void validate(Inspector inspector) throws ValidationException {
      if ("bad".equals(name)) {
        throw new ValidationException("name was bad!");
      }
    }
  }

  interface Service {
    @GET("/") Call<Value> value();
  }

  @Rule public final MockWebServer server = new MockWebServer();

  private Inspector inspector = new Inspector.Builder().build();

  @Test public void testBadData_throwing() throws IOException {
    ValidationExceptionCallback callback = new ValidationExceptionCallback() {
      @Override public void onValidationException(Type type, ValidationException exception) {
        throw exception;
      }
    };
    Service service = createServiceForCallback(callback);

    server.enqueue(new MockResponse().setBody("{\"name\":\"bad\"}"));

    Call<Value> call = service.value();

    try {
      Response<Value> response = call.execute();
      throw new AssertionError("This should not be hit!");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("name was bad!");
    }
  }

  @Test public void testBadData_logging() throws IOException {
    final AtomicReference<String> loggedCause = new AtomicReference<>();
    ValidationExceptionCallback callback = new ValidationExceptionCallback() {
      @Override public void onValidationException(Type type, ValidationException exception) {
        loggedCause.set(exception.getMessage());
      }
    };
    Service service = createServiceForCallback(callback);

    server.enqueue(new MockResponse().setBody("{\"name\":\"bad\"}"));

    Call<Value> call = service.value();
    Response<Value> response = call.execute();
    Value value = response.body();

    assertThat(value.name).isEqualTo("bad");
    assertThat(loggedCause.get()).isNotNull();
    assertThat(loggedCause.get()).contains("name was bad!");
  }

  @Test public void testGoodData() throws IOException {
    ValidationExceptionCallback callback = new ValidationExceptionCallback() {
      @Override public void onValidationException(Type type, ValidationException exception) {
        throw exception;
      }
    };
    Service service = createServiceForCallback(callback);

    server.enqueue(new MockResponse().setBody("{\"name\":\"good\"}"));

    Call<Value> call = service.value();
    Response<Value> response = call.execute();
    Value value = response.body();

    assertThat(value.name).isEqualTo("good");
  }

  private Service createServiceForCallback(ValidationExceptionCallback callback) {
    return new Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(new InspectorConverterFactory(inspector, callback))
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(Service.class);
  }
}
