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

```gradle
compileOnly 'io.sweers.inspector:inspector-android-compiler-extension:x.y.z'
```
