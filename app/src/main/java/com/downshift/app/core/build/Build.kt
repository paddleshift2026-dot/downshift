package com.downshift.app.core.build

enum class EngineId { I4, V8, V12 }

enum class IntakePart { NA, TURBO1, TURBO2, TURBO3, SUPERCHARGER }

enum class ExhaustPart { STOCK, SPORT, TRACK }

enum class TransmissionPart { AT, DCT, MT_BLIP }

/** One garage setup: four parts plus the sliders (pops 0..1, redline offset in rpm, volume 0..1). */
data class Build(
    val engine: EngineId = EngineId.I4,
    val intake: IntakePart = IntakePart.NA,
    val exhaust: ExhaustPart = ExhaustPart.STOCK,
    val transmission: TransmissionPart = TransmissionPart.AT,
    val pops: Double = 0.5,
    val redlineOffset: Int = 0,
    val volume: Double = 0.8,
)
