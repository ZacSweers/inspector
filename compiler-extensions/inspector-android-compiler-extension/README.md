inspector-android-compiler-extension
====================================

An inspector compiler extension that generates validation for Android support library annotations.

Supported annotations:
- FloatRange
- IntDef
- IntRange
- Size
- StringDef

Note that nullability annotations are not supported here. Use the `inspector-nullability-compiler-extension` instead.

[![Maven Central](https://img.shields.io/maven-central/v/io.sweers.inspector/inspector-android-compiler-extension.svg)](https://mvnrepository.com/artifact/io.sweers.inspector/inspector-android-compiler-extension)
```gradle
compileOnly 'io.sweers.inspector:inspector-android-compiler-extension:x.y.z'
```
