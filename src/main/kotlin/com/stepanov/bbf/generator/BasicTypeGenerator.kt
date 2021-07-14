package com.stepanov.bbf.generator

class BasicTypeGenerator {
    fun generate() = basicTypes.random()

    // floats might be too complicated, YARPGEN doesn't use them
    // can't generate random valid chars easily
    private val basicTypes = listOf("Int", "Long", "Float", "Double", "Boolean")
}