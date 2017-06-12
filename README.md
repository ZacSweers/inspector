Inspector
===========

Inspector is a tiny class validation tool that has a tiny, flexible, and powerful API.

If you've ever used Moshi or GSON, it should feel very familiar.

The base type is `Inspector`, which can contain any number of `Validator`s or `Validator.Factory`s.

Like Moshi, Inspector operates on `Type`s directly. Validation is a decentralized, self-serve system. 
There is a reflective class-based validator generator if you want, or you can use the `auto-value-gson` 
AutoValue extension to generate validator implementations for you.

Validation normally operates on method return types. Everything is assumed non-null, unless annotated 
with one of the number of `@Nullable` annotations out there. Nullability is the only validation run 
out of the box by the reflective adapter as well. Inspector is purposefully stupid, you know your own models!

Looks like this

```java
Inspector inspector = new Inspector.Builder()
    .add(...)  // Whatever you want!
    .add(Foo.class, new FooValidator())
    .add(BarValidator.FACTORY)
    .build();

boolean isValid = inspector.validator(Foo.class).isValid(myFooStance);

// Or more aggressively, but with descriptive error messages
try {
  inspector.validator(Foo.class).validate(myFooInstance);
} catch (ValidationException e) {
  // Server's sending you bad stuff
}
```

### Annotations

`@ValidationQualifier` - Like Moshi's `@JsonQualifier` or javax's `@Qualifer`. Use these on your own 
annotations for custom behavior.

`@InspectorExcluded` - Use this to exclude methods from validation.

`@ValidatedBy(SomeValidator.class)` - Use this to designate that this should be validated by another validator.

### inspector-compiler

Features:
- Annotation processor that generates validator implementations
- Supports a service-loader-based extensions API via `InspectorExtension`
  - First party implementations are under the `compiler-extensions` directory
- Works just like auto-value-gson or auto-value-moshi
- Has support for Android support annotations and RAVE annotations

Simply add a static `Validator`-returning method to your model and be sure to annotate it with 
something to look for, such as `@AutoValue` (this can be done automatically via the `inspector-autovalue-compiler-extension`).

```java
@AutoValue
public abstract class Foo {
  
  public abstract String bar();
  
  public static Validator<Foo> validator(Inspector inspector) {
    // Generated class is in the same package, prefixed with "Validator_"
    return new Validator_Foo(insepctor);
  }
}
```

If you have a lot of these, you may not want to manually have to hook all these up to an inspector instance. To solve 
this, you can use the `inspectory-factory-compiler` annotation processor to generate a factory implementation. Simply stub
your factory class like so:

```java
@InspectorFactory(include = AutoValue.class) 
public abstract class MyFactory implements Validator.Factory {
  public static MyFactory create() {
    return new InspectorFactory_MyFactory();
  }
}
```

- Your class must implement ValidatorFactory and not be `final`.
- You mark which annotations you want to target your factory to, such as `@AutoValue`.
- An `InspectorFactory_<YourClassName>` will be generated in the same package for referencing with an implementation
 of the `create()` method.

### inspector-android

This is just a for-fun proof of concept of how some non-generated support library annotation validators 
would look. This unfortunately is not currently possible since support annotations are not RUNTIME retained.

### Usage

Your gradle file could look like this:

```gradle
depedencies {
  compileOnly 'io.sweers.inspector:inspector-compiler:x.y.z'
  
  // Optional compiler extensions
  compileOnly 'io.sweers.inspector:inspector-android-compiler-extension:x.y.z'
  compileOnly 'io.sweers.inspector:inspector-autovalue-compiler-extension:x.y.z'
  compileOnly 'io.sweers.inspector:inspector-rave-compiler-extension:x.y.z'

  implementation 'io.sweers.inspector:inspector:x.y.z'
}
```

### TODO

- Revisit `AdapterMethodsFactory`.
- Extract RAVE and support annotations support to pluggable SPIs to AV extension
- ValidatorFactory generation for the AV extension


### Notes

This library is heavily influenced by Moshi and RAVE.

License
-------

    Copyright (C) 2017 Zac Sweers

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
