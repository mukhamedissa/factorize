package com.github.ohbe1.factorize.utils

import com.github.ohbe1.factorize.processor.FactorizeProcessor.Companion.KAPT_KOTLIN_GENERATED_OPTION_NAME
import com.squareup.kotlinpoet.FileSpec
import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.tools.Diagnostic

object ProcessorUtils {

    private lateinit var processingEnvironment: ProcessingEnvironment

    fun init(environment: ProcessingEnvironment) {
        processingEnvironment = environment
    }

    fun logError(message: String) {
        processingEnvironment.messager.printMessage(Diagnostic.Kind.ERROR, message)
    }

    fun generateFile(fileSpec: FileSpec) {
        val kaptKotlinGeneratedDir = processingEnvironment.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]

        fileSpec.writeTo(File(kaptKotlinGeneratedDir, fileSpec.name))
    }

}