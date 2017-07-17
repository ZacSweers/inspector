inspector-factory-compiler
==========================

If you have a lot of validators (such as generated ones), you can use this annotation processor to 
generate a `Validator.Factory` implementation that composes all of these generated ones.

Simply create an abstract class and annotate with `@InspectorFactory` (from the 
`inspector-factory-compiler-annotations` artifact) and implement `Validator.Factory`.
A full implementation will be implemented in the same package with the prefix `InspectorFactory_` that 
extends the annotated class.

You must specify (via the `include` argument) what classes to look for via annotation. For example, if you have validators on AutoValue 
classes, you could specify it as such. You can also write your own, and specify any number of annotation targets.

```java
@InspectorFactory(include = AutoValue.class)
public abstract class MyInspectorFactory implements Validator.Factory {
  
  public static MyInspectorFactory create() {
    return new InspectorFactory_MyInspectorFactory();    
  }
  
}
```

[![Maven Central](https://img.shields.io/maven-central/v/io.sweers.inspector/inspector-factory-compiler.svg)](https://mvnrepository.com/artifact/io.sweers.inspector/inspector-factory-compiler)
```gradle
implementation 'io.sweers.inspector:inspector-factory-compiler:x.y.z'
```
