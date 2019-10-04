package com.github.ohbe1.factorize.processor

import com.github.ohbe1.factorize.utils.ProcessorUtils
import com.github.ohbe1.factorize.annotation.Factorize
import com.github.ohbe1.factorize.utils.javaToKotlinType
import com.github.ohbe1.factorize.processor.FactorizeProcessor.Companion.KAPT_KOTLIN_GENERATED_OPTION_NAME
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement


@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(KAPT_KOTLIN_GENERATED_OPTION_NAME)
class FactorizeProcessor : AbstractProcessor() {

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

        private const val PACKAGE_NAME = "com.example.factorize"
        private const val LIFECYCLE_PACKAGE = "androidx.lifecycle"

        private const val CLASS_VIEWMODEL = "ViewModel"
        private const val CLASS_VIEWMODEL_PROVIDER = "ViewModelProvider"
        private const val CLASS_VIEWMODEL_FACTORY = "ViewModelProvider.Factory"

        private const val FUN_CREATE = "create"
        private const val ARGUMENT_NAME = "modelClass"

        private const val TYPE_VARIABLE = "T"

        private const val FILE_NAME = "ViewModelFactoryProvider"

        private const val UNCHECKED_CAST = "UNCHECKED_CAST"

        private const val CREATE_STATEMENT =
            """
                if (modelClass.isAssignableFrom(%T::class.java)) {
                    return %T(%L) as T
                }
                
                throw IllegalStateException("Class %T is not supported in this factory")
            """
    }

    private val typeSpecs = ArrayList<TypeSpec>()

    private val uncheckedCastAnnotation: AnnotationSpec
        get() = AnnotationSpec.builder(Suppress::class)
            .addMember("%S",
                UNCHECKED_CAST
            )
            .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
            .build()

    override fun getSupportedAnnotationTypes() =
        mutableSetOf(Factorize::class.java.name)

    override fun init(processingEnvironment: ProcessingEnvironment?) {
        super.init(processingEnvironment)

        ProcessorUtils.init(processingEnvironment!!)
    }

    override fun process(
        set: MutableSet<out TypeElement>?,
        roundEnvironment: RoundEnvironment?
    ): Boolean {
        val elements = roundEnvironment?.getElementsAnnotatedWith(Factorize::class.java)
            ?: return true

        elements.forEach {
            if (it.kind != ElementKind.CLASS) {
                ProcessorUtils.logError("Annotation ${Factorize::class.java.name} can be used with classes")

                return false
            }

            if (!generateFactory(it as TypeElement)) {
                return false
            }

            createFactoryFile()
        }

        return true
    }

    private fun generateFactory(element: TypeElement): Boolean {
        val constructorParams = getConstructorParams(element)

        val typeSpec = TypeSpec.classBuilder("${element.simpleName}Factory")
            .primaryConstructor(buildConstructorWithParams(constructorParams))
            .addProperties(getPropertiesForParams(constructorParams))
            .addSuperinterface(ClassName(
                LIFECYCLE_PACKAGE,
                CLASS_VIEWMODEL_FACTORY
            ))
            .addFunction(buildCreateFunction(element,
                constructorParams.joinToString(separator = ",") { it.simpleName.toString() }))
            .build()

        typeSpecs.add(typeSpec)

        return true
    }

    private fun buildCreateFunction(element: TypeElement, params: String) =
        FunSpec.builder(FUN_CREATE)
            .addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            .addTypeVariable(TypeVariableName("$TYPE_VARIABLE : $CLASS_VIEWMODEL?"))
            .addParameter(ParameterSpec(
                ARGUMENT_NAME, Class::class.asClassName().parameterizedBy(TypeVariableName(
                    TYPE_VARIABLE
                ))))
            .addStatement(CREATE_STATEMENT.trimIndent(), element.asClassName(), element.asClassName(), params, element.asClassName())
            .returns(TypeVariableName(TYPE_VARIABLE))
            .build()

    private fun buildConstructorWithParams(params: List<Element>) =
        FunSpec.constructorBuilder().apply {
            params.forEach {
                addParameter(it.simpleName.toString(), it.javaToKotlinType(), KModifier.PRIVATE)
            }
        }.build()

    private fun getPropertiesForParams(params: List<Element>) =
        params.map {
            PropertySpec.builder(it.simpleName.toString(), it.javaToKotlinType())
                .initializer(it.simpleName.toString()).build()
        }

    private fun getConstructorParams(element: TypeElement) =
        element.enclosedElements.filter { it.kind == ElementKind.FIELD }

    private fun createFactoryFile() {
        ProcessorUtils.generateFile(
            FileSpec.builder(
                PACKAGE_NAME,
                FILE_NAME
            )
                .addAnnotation(uncheckedCastAnnotation)
                .addImport(
                    LIFECYCLE_PACKAGE,
                    CLASS_VIEWMODEL_PROVIDER
                )
                .addImport(
                    LIFECYCLE_PACKAGE,
                    CLASS_VIEWMODEL
                ).apply {
                    typeSpecs.forEach {
                        addType(it)
                    }
                }.build()
        )
    }
}