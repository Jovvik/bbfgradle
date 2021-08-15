package com.stepanov.bbf.generator.arithmetic

enum class Type(
    val minValue: Number,
    val maxValue: Number,
    private val signed: Type? = null,
    val isFloatingPoint: Boolean = false
) {
    BYTE(Byte.MIN_VALUE, Byte.MAX_VALUE),
    UBYTE(UByte.MIN_VALUE.toLong(), UByte.MAX_VALUE.toLong(), BYTE),
    SHORT(Short.MIN_VALUE, Short.MAX_VALUE),
    USHORT(UShort.MIN_VALUE.toLong(), UShort.MAX_VALUE.toLong(), SHORT),
    INT(Int.MIN_VALUE, Int.MAX_VALUE),
    UINT(UInt.MIN_VALUE.toLong(), UInt.MAX_VALUE.toLong(), INT),
    LONG(Long.MIN_VALUE, Long.MAX_VALUE),
    ULONG(ULong.MIN_VALUE.toLong(), ULong.MAX_VALUE.toDouble(), LONG), // conversion subtracts 1
    FLOAT(Float.MIN_VALUE, Float.MAX_VALUE, isFloatingPoint = true),
    DOUBLE(Double.MIN_VALUE, Double.MAX_VALUE, isFloatingPoint = true);

    val isUnsigned = signed != null

    fun toSigned() = signed ?: this

    override fun toString(): String {
        val signedString = toSigned().name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        return (if (isUnsigned) "U" else "") + signedString
    }
}