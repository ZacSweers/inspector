inspector-compiler-extensions-api
=================================

This is the main API entry point for creating inspector compiler extensions. 

The interface is the `InspectorExtension` interface, which has default implementations for its methods.

There are four parts to the interface:

`Set<Annotation> applicableAnnotations()` is for returning a set of annotations you want the annotation 
processor to find. These conventionally are going to be annotations on types, such as `AutoValue` classes.

`boolean applicable(...)` is for checking if a given `Property` is applicable for this extension. For instance, 
a nullability extension would check if the property was a non-primitive/non-Void type.

`CodeBlock generateValidation(...)` is for generating your actual validation. This returns a JavaPoet CodeBlock, and gives you
information about three parameters:
- `prop` - the property itself
- `propertyName` - the name of the property being validated. If you are validating a `Person`'s `name` property, this is `name`
- `typeInstance` - the instance of the type being validated. If you are validating a `Person` instance, this is the instance as passed to the `validate` method.

If no validation is needed, you can return `null`.

`Priority priority()` is for declaring priority of your extension. This is useful if you have higher priority validation
that should run as early as possible (such as nullability). Most validations should not care what order they are run in though.
Possible values are `HIGH`, `NORMAL`, and `NONE`.

All properties are represented by the `Property` class, which is handed to you in `applicable` and `generateValidation`.
This class has various information about a given property accessible via final fields as well as helper methods.

To implement your own extension, simply implement `InspectorExtension` or extend `AbstractInspectorExtension` and 
mark it in your resources as a service. If you use AutoService, it's very simple:

```java
@AutoService(InspectorExtension.class) 
public final class MyInspectorExtension extends AbstractInspectorExtension {
  // Your stuff here!
}
```

```gradle
implementation 'io.sweers.inspector:inspector-compiler-extensions-api:x.y.z'
```
