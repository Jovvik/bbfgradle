package com.stepanov.bbf.generator

import kotlin.random.Random

class ProbabilityTable<T>(private val probabilities: Map<T, Double>) {

    private val totalProbability = probabilities.values.sum()

    constructor(values: Array<T>) : this(values.toList())

    constructor(values: Collection<T>) : this(values.associateWith { 1.0 })

    operator fun invoke(): T {
        var rand = Random.nextDouble() * totalProbability
        for ((value, probability) in probabilities) {
            if (rand < probability) {
                return value
            } else {
                rand -= probability
            }
        }
        throw IllegalStateException("Something went terribly wrong")
    }
}