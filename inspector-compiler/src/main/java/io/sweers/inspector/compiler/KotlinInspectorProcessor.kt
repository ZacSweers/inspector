package io.sweers.inspector.compiler

import com.google.auto.common.AnnotationMirrors
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.IN
import com.squareup.kotlinpoet.KModifier.OUT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import io.sweers.inspector.Inspector
import io.sweers.inspector.Types
import io.sweers.inspector.ValidationException
import io.sweers.inspector.ValidationQualifier
import io.sweers.inspector.Validator
import io.sweers.inspector.compiler.plugins.spi.InspectorCompilerContext
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.declaresDefaultValue
import me.eugeniomarletti.kotlin.metadata.extractFullName
import me.eugeniomarletti.kotlin.metadata.isDataClass
import me.eugeniomarletti.kotlin.metadata.isPrimary
import me.eugeniomarletti.kotlin.metadata.jvm.getJvmConstructorSignature
import me.eugeniomarletti.kotlin.metadata.kaptGeneratedOption
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.visibility
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.ProtoBuf.Type.Argument.Projection
import org.jetbrains.kotlin.serialization.ProtoBuf.TypeParameter.Variance
import org.jetbrains.kotlin.serialization.ProtoBuf.Visibility
import org.jetbrains.kotlin.serialization.ProtoBuf.Visibility.INTERNAL
import org.jetbrains.kotlin.serialization.deserialization.NameResolver
import java.io.File
import java.lang.reflect.Type
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.tools.Diagnostic.Kind.ERROR

class KotlinInspectorProcessor : InspectorProcessor {

  private lateinit var elements: Elements
  private lateinit var filer: Filer
  private lateinit var messager: Messager
  private lateinit var processingEnv: ProcessingEnvironment
  private lateinit var options: MutableMap<String, String>

  override fun process(context: InspectorCompilerContext,
      element: TypeElement,
      extensions: MutableSet<InspectorExtension>) {

    processingEnv = context.processingEnv
    elements = processingEnv.elementUtils
    filer = processingEnv.filer
    messager = processingEnv.messager
    options = processingEnv.options

    val metadata = element.kotlinMetadata

    if (metadata !is KotlinClassMetadata) {
      errorMustBeDataClass(element)
      return
    }

    val classData = metadata.data
    val (nameResolver, classProto) = classData

    fun ProtoBuf.Type.extractFullName() = extractFullName(classData)

    if (!classProto.isDataClass) {
      errorMustBeDataClass(element)
      return
    }

    val fqClassName = nameResolver.getString(classProto.fqName).replace('/', '.')

    val packageName = nameResolver.getString(classProto.fqName).substringBeforeLast('/').replace(
        '/', '.')

    val hasCompanionObject = classProto.hasCompanionObjectName()
    // todo allow custom constructor
    val protoConstructor = classProto.constructorList
        .single { it.isPrimary }
    val constructorJvmSignature = protoConstructor.getJvmConstructorSignature(nameResolver,
        classProto.typeTable)
    val constructor = classProto.fqName
        .let(nameResolver::getString)
        .replace('/', '.')
        .let(elements::getTypeElement)
        .enclosedElements
        .mapNotNull { it.takeIf { it.kind == ElementKind.CONSTRUCTOR }?.let { it as ExecutableElement } }
        .first()
    // TODO Temporary until jvm method signature matching is better
//        .single { it.jvmMethodSignature == constructorJvmSignature }
    val parameters = protoConstructor
        .valueParameterList
        .mapIndexed { index, valueParameter ->
          val paramName = nameResolver.getString(valueParameter.name)

          val nullable = valueParameter.type.nullable
          val paramFqcn = valueParameter.type.extractFullName()
              .replace("`", "")
              .removeSuffix("?")

          val actualElement = constructor.parameters[index]

          val validationQualifiers = AnnotationMirrors.getAnnotatedAnnotations(actualElement,
              ValidationQualifier::class.java)

          Property(
              apiProp = io.sweers.inspector.compiler.plugins.spi.Property(paramName, actualElement),
              name = paramName,
              fqClassName = paramFqcn,
              hasDefault = valueParameter.declaresDefaultValue,
              nullable = nullable,
              typeName = valueParameter.type.asTypeName(nameResolver, classProto::getTypeParameter),
              validationQualifiers = validationQualifiers)
        }

    val genericTypeNames = classProto.typeParameterList
        .map {
          val variance = it.variance.asKModifier().let {
            // We don't redeclare out variance here
            if (it == OUT) {
              null
            } else {
              it
            }
          }
          TypeVariableName.invoke(
              name = nameResolver.getString(it.name),
              bounds = *(it.upperBoundList
                  .map { it.asTypeName(nameResolver, classProto::getTypeParameter) }
                  .toTypedArray()),
              variance = variance)
              .reified(it.reified)
        }.let {
          if (it.isEmpty()) {
            null
          } else {
            it
          }
        }

    Adapter(
        fqClassName = fqClassName,
        packageName = packageName,
        propertyList = parameters,
        originalElement = element,
        hasCompanionObject = hasCompanionObject,
        visibility = classProto.visibility!!,
        elements = elements,
        extensions = extensions,
        genericTypeNames = genericTypeNames)
        .generateAndWrite()
  }

  private fun errorMustBeDataClass(element: Element) {
    messager.printMessage(ERROR,
        "InspectorCompiler cannot be run on $element: must be a Kotlin data class",
        element)
  }

  private fun Adapter.generateAndWrite(): Boolean {
    val adapterName = "${name}Validator"
    val outputDir = options[kaptGeneratedOption]?.let(::File) ?: mavenGeneratedDir(adapterName)
    val fileBuilder = com.squareup.kotlinpoet.FileSpec.builder(packageName, adapterName)
    fileBuilder
        .addType(createValidatorTypeSpec(adapterName))
        .build()
        .writeTo(outputDir)
    return true
  }

  private fun mavenGeneratedDir(adapterName: String): File {
    // Hack since the maven plugin doesn't supply `kapt.kotlin.generated` option
    // Bug filed at https://youtrack.jetbrains.com/issue/KT-22783
    val file = filer.createSourceFile(adapterName).toUri().let(::File)
    return file.parentFile.also { file.delete() }
  }
}

private data class Property(
    val apiProp: io.sweers.inspector.compiler.plugins.spi.Property,
    val name: String,
    val fqClassName: String,
    val hasDefault: Boolean,
    val nullable: Boolean,
    val typeName: TypeName,
    val validationQualifiers: Set<AnnotationMirror>)

private data class Adapter(
    val fqClassName: String,
    val packageName: String,
    val propertyList: List<Property>,
    val originalElement: Element,
    val name: String = fqClassName.substringAfter(packageName)
        .replace('.', '_')
        .removePrefix("_"),
    val hasCompanionObject: Boolean,
    val elements: Elements,
    val visibility: Visibility,
    val extensions: Set<InspectorExtension>,
    val genericTypeNames: List<TypeVariableName>?) {
  fun createValidatorTypeSpec(validatorName: String): TypeSpec {
    val nameAllocator = NameAllocator()
    fun String.allocate() = nameAllocator.newName(this)

    val originalTypeName = originalElement.asType().asTypeName()
    val inspectorName = "inspector".allocate()
    val inspectorParam = ParameterSpec.builder(inspectorName, Inspector::class).build()
    val typesParam = ParameterSpec.builder("types".allocate(),
        ParameterizedTypeName.get(ARRAY, Type::class.asTypeName())).build()
    val target = ParameterSpec.builder("target".allocate(),
        originalTypeName.asNullable()).build()
    val originalValidatorTypeName = ParameterizedTypeName.get(Validator::class.asClassName(),
        originalTypeName)

    // Create fields
    val adapterProperties = propertyList
        .distinctBy { it.typeName to it.validationQualifiers }
        .associate { prop ->
          val typeName = prop.typeName
          val qualifierNames = prop.validationQualifiers.joinToString("") {
            "as${it.annotationType.asElement().simpleName.toString().capitalize()}"
          }
          val propertyName = typeName.simplifiedName().allocate().let {
            if (qualifierNames.isBlank()) {
              it
            } else {
              "$qualifierNames$it"
            }
          }.let { "${it}Validator" }
          val validatorTypeName = ParameterizedTypeName.get(Validator::class.asTypeName(), typeName)
          val key = typeName to prop.validationQualifiers
          return@associate key to PropertySpec.builder(propertyName, validatorTypeName, PRIVATE)
              .apply {
                val qualifiers = prop.validationQualifiers.toList()
                val standardArgs = arrayOf(inspectorParam,
                    if (typeName is ClassName && qualifiers.isEmpty()) {
                      ""
                    } else {
                      CodeBlock.of("<%T>",
                          typeName)
                    },
                    typeName.makeType(elements, typesParam, genericTypeNames ?: emptyList()))
                val standardArgsSize = standardArgs.size + 1
                val (initializerString, args) = when {
                  qualifiers.isEmpty() -> "" to emptyArray()
                  qualifiers.size == 1 -> {
                    ", %${standardArgsSize}T::class.java" to arrayOf(
                        qualifiers.first().annotationType.asTypeName())
                  }
                  else -> {
                    val initString = qualifiers
                        .mapIndexed { index, _ ->
                          val annoClassIndex = standardArgsSize + index
                          return@mapIndexed "%${annoClassIndex}T::class.java"
                        }
                        .joinToString()
                    val initArgs = qualifiers
                        .map { it.annotationType.asTypeName() }
                        .toTypedArray()
                    ", $initString" to initArgs
                  }
                }
                val finalArgs = arrayOf(*standardArgs, *args)
                initializer(
                    "%1N.validator%2L(%3L$initializerString)${if (prop.nullable) ".nullSafe()" else ""}",
                    *finalArgs)
              }
              .build()
        }

    return TypeSpec.classBuilder(validatorName)
        .superclass(originalValidatorTypeName)
        .apply {
          genericTypeNames?.let {
            addTypeVariables(genericTypeNames)
          }
        }
        .apply {
          // TODO make this configurable. Right now it just matches the source model
          if (visibility == INTERNAL) {
            addModifiers(KModifier.INTERNAL)
          }
        }
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter(inspectorParam)
            .apply {
              genericTypeNames?.let {
                addParameter(typesParam)
              }
            }
            .build())
        .addProperties(adapterProperties.values)
        .addFunction(FunSpec.builder("toString")
            .addModifiers(OVERRIDE)
            .returns(String::class)
            .addStatement("return %S",
                "GeneratedValidator(${originalTypeName.resolveRawType()
                    .simpleNames()
                    .joinToString(".")})")
            .build())
        .addFunction(FunSpec.builder("validate")
            .addAnnotation(ValidationException::class)
            .addModifiers(OVERRIDE)
            .addParameter(inspectorParam)
            .addParameter(target)
            .apply {
              propertyList.forEach { prop ->
                addComment("Begin validation for \"%L\"", prop.name)
                addStatement("%N.validate(%N.%L)",
                    adapterProperties[prop.typeName to prop.validationQualifiers]!!,
                    target,
                    prop.name)
                extensions
                    .sortedBy(InspectorExtension::priority)
                    .filter { it.applicable(prop.apiProp) }
                    .onEach { ext ->
                      ext.generateValidation(prop.apiProp, name, target)?.let {
                        addComment("Validations contributed by %S", ext.toString())
                            .addCode(it)
                      }
                    }
              }
            }
            .build())
        .build()
  }
}

/**
 * Creates a joined string representation of simplified typename names.
 */
private fun List<TypeName>.simplifiedNames(): String {
  return joinToString("_") { it.simplifiedName() }
}

private fun TypeName.resolveRawType(): ClassName {
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> rawType
    else -> throw UnsupportedOperationException("Cannot get raw type from $this")
  }
}

/**
 * Creates a simplified string representation of a TypeName's name
 */
private fun TypeName.simplifiedName(): String {
  return when (this) {
    is ClassName -> simpleName().decapitalize()
    is ParameterizedTypeName -> {
      rawType.simpleName().decapitalize() + if (typeArguments.isEmpty()) "" else "__" + typeArguments.simplifiedNames()
    }
    is WildcardTypeName -> "wildcard__" + (lowerBounds + upperBounds).simplifiedNames()
    is TypeVariableName -> name.decapitalize() + if (bounds.isEmpty()) "" else "__" + bounds.simplifiedNames()
  // Shouldn't happen
    else -> toString().decapitalize()
  }.let { if (nullable) "${it}_nullable" else it }
}

private fun ClassName.isClass(elementUtils: Elements): Boolean {
  val fqcn = toString()
  if (fqcn.startsWith("kotlin.collections.")) {
    // These are special kotlin interfaces are only visible in kotlin, because they're replaced by
    // the compiler with concrete java classes
    return false
  } else if (this == ARRAY) {
    // This is a "fake" class and not visible to Elements
    return true
  }
  return elementUtils.getTypeElement(fqcn).kind == ElementKind.INTERFACE
}

private fun TypeName.objectType(): TypeName {
  return when (this) {
    BOOLEAN -> Boolean::class.javaObjectType.asTypeName()
    BYTE -> Byte::class.javaObjectType.asTypeName()
    SHORT -> Short::class.javaObjectType.asTypeName()
    INT -> Integer::class.javaObjectType.asTypeName()
    LONG -> Long::class.javaObjectType.asTypeName()
    CHAR -> Character::class.javaObjectType.asTypeName()
    FLOAT -> Float::class.javaObjectType.asTypeName()
    DOUBLE -> Double::class.javaObjectType.asTypeName()
    else -> this
  }
}

private fun TypeName.makeType(
    elementUtils: Elements,
    typesArray: ParameterSpec,
    genericTypeNames: List<TypeVariableName>): CodeBlock {
  if (nullable) {
    return asNonNullable().makeType(elementUtils, typesArray, genericTypeNames)
  }
  return when (this) {
    is ClassName -> CodeBlock.of("%T::class.java", this)
    is ParameterizedTypeName -> {
      // If it's an Array type, we shortcut this to return Types.arrayOf()
      if (rawType == ARRAY) {
        return CodeBlock.of("%T.arrayOf(%L)",
            Types::class,
            typeArguments[0].objectType().makeType(elementUtils, typesArray, genericTypeNames))
      }
      // If it's a Class type, we have to specify the generics.
      val rawTypeParameters = if (rawType.isClass(elementUtils)) {
        CodeBlock.of(
            typeArguments.joinTo(
                buffer = StringBuilder(),
                separator = ", ",
                prefix = "<",
                postfix = ">") { "%T" }
                .toString(),
            *(typeArguments.map { objectType() }.toTypedArray())
        )
      } else {
        CodeBlock.of("")
      }
      CodeBlock.of(
          "%T.newParameterizedType(%T%L::class.java, ${typeArguments
              .joinToString(", ") { "%L" }})",
          Types::class,
          rawType.objectType(),
          rawTypeParameters,
          *(typeArguments.map {
            it.objectType().makeType(elementUtils, typesArray, genericTypeNames)
          }.toTypedArray()))
    }
    is WildcardTypeName -> {
      val target: TypeName
      val method: String
      when {
        lowerBounds.size == 1 -> {
          target = lowerBounds[0]
          method = "supertypeOf"
        }
        upperBounds.size == 1 -> {
          target = upperBounds[0]
          method = "subtypeOf"
        }
        else -> throw IllegalArgumentException(
            "Unrepresentable wildcard type. Cannot have more than one bound: " + this)
      }
      CodeBlock.of("%T.%L(%T::class.java)", Types::class, method, target)
    }
    is TypeVariableName -> {
      CodeBlock.of("%N[%L]", typesArray, genericTypeNames.indexOfFirst { it == this })
    }
  // Shouldn't happen
    else -> throw IllegalArgumentException("Unrepresentable type: " + this)
  }
}

private fun ProtoBuf.TypeParameter.asTypeName(
    nameResolver: NameResolver,
    getTypeParameter: (index: Int) -> ProtoBuf.TypeParameter): TypeName {
  return TypeVariableName(
      name = nameResolver.getString(name),
      bounds = *(upperBoundList.map { it.asTypeName(nameResolver, getTypeParameter) }
          .toTypedArray()),
      variance = variance.asKModifier()
  )
}

private fun ProtoBuf.TypeParameter.Variance.asKModifier(): KModifier? {
  return when (this) {
    Variance.IN -> IN
    Variance.OUT -> OUT
    Variance.INV -> null
  }
}

/**
 * Returns the TypeName of this type as it would be seen in the source code,
 * including nullability and generic type parameters.
 *
 * @param [nameResolver] a [NameResolver] instance from the source proto
 * @param [getTypeParameter]
 * A function that returns the type parameter for the given index.
 * **Only called if [ProtoBuf.Type.hasTypeParameter] is `true`!**
 */
private fun ProtoBuf.Type.asTypeName(
    nameResolver: NameResolver,
    getTypeParameter: (index: Int) -> ProtoBuf.TypeParameter
): TypeName {

  val argumentList = when {
    hasAbbreviatedType() -> abbreviatedType.argumentList
    else -> argumentList
  }

  if (hasFlexibleUpperBound()) {
    return WildcardTypeName.subtypeOf(
        flexibleUpperBound.asTypeName(nameResolver, getTypeParameter))
  } else if (hasOuterType()) {
    return WildcardTypeName.supertypeOf(outerType.asTypeName(nameResolver, getTypeParameter))
  }

  val realType = when {
    hasTypeParameter() -> return getTypeParameter(typeParameter)
        .asTypeName(nameResolver, getTypeParameter)
    hasTypeParameterName() -> typeParameterName
    hasAbbreviatedType() -> abbreviatedType.typeAliasName
    else -> className
  }

  var typeName: TypeName = ClassName.bestGuess(nameResolver.getString(realType)
      .replace("/", "."))

  if (argumentList.isNotEmpty()) {
    val remappedArgs: Array<TypeName> = argumentList.map {
      val projection = if (it.hasProjection()) {
        it.projection
      } else null
      if (it.hasType()) {
        it.type.asTypeName(nameResolver, getTypeParameter)
            .let { typeName ->
              projection?.let {
                when (it) {
                  Projection.IN -> WildcardTypeName.supertypeOf(typeName)
                  Projection.OUT -> {
                    if (typeName == ANY) {
                      // This becomes a *, which we actually don't want here.
                      // List<Any> works with List<*>, but List<*> doesn't work with List<Any>
                      typeName
                    } else {
                      WildcardTypeName.subtypeOf(typeName)
                    }
                  }
                  Projection.STAR -> WildcardTypeName.subtypeOf(ANY)
                  Projection.INV -> TODO("INV projection is unsupported")
                }
              } ?: typeName
            }
      } else {
        WildcardTypeName.subtypeOf(ANY)
      }
    }.toTypedArray()
    typeName = ParameterizedTypeName.get(typeName as ClassName, *remappedArgs)
  }

  if (nullable) {
    typeName = typeName.asNullable()
  }

  return typeName
}
