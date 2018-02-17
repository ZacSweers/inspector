inspector-retrofit
======================================

This artifact contains a delegating retrofit `Converter.Factory` implementation that you can use to 
validate response models. It works by simply delegating to other converter factories, then validating 
the final response value with a given `Inspector` instance and passes the result to a `ValidationExceptionCallback`.

Because it's a delegating converter, you *must* add this factory before any other factories you want it
to delegate to.

Example usage:

```java
Inspector inspector = new Inspector.Builder().build();

ValidationExceptionCallback callback = new ValidationExceptionCallback() {
      @Override public void onValidationException(Type type, ValidationException validationException) {
        // This response didn't pass validation!
        
        // You could log it
        System.out.println("Validation exception: "
            + type
            + ". Error: "
            + validationException.getMessage());
        
        // Or throw it to fail hard
        throw validationException;
        
        // Or wrap in an IOException to drop it on the floor
        //throw new IOException(validationException);
      }
    };

Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(...)
        .addConverterFactory(new InspectorConverterFactory(inspector, callback)) // Add this first!
        .addConverterFactory(...) // Add other converter factories after
        .build();
```

[![Maven Central](https://img.shields.io/maven-central/v/io.sweers.inspector/inspector-retrofit.svg)](https://mvnrepository.com/artifact/io.sweers.inspector/inspector-retrofit)
```gradle
implementation 'io.sweers.inspector:inspector-retrofit:x.y.z'
```
