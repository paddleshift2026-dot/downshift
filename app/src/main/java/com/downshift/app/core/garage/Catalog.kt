package com.downshift.app.core.garage

import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.build.IntakePart
import com.downshift.app.core.build.TransmissionPart

enum class Category { ENGINE, INTAKE, EXHAUST, TRANSMISSION }

/** NORMAL parts are unlocked with points; a PLACEHOLDER only holds a place, a LIMITED pack is sold apart from points. */
enum class PartKind { NORMAL, PLACEHOLDER, LIMITED }

/** How a part shifts the sound-character bars (the engine's own fx is its starting point). */
data class SoundFx(val bass: Int = 0, val rasp: Int = 0, val whistle: Int = 0, val pop: Int = 0)

data class Part(
    val id: String,
    val category: Category,
    val name: String,
    val code: String,
    val tier: Int,
    val price: Int,
    val desc: String,
    val fx: SoundFx = SoundFx(),
    val kind: PartKind = PartKind.NORMAL,
)

/** Every garage part in the design's order, with the PRD prices. No performance figures on purpose. */
object Catalog {
    private val engines = listOf(
        Part("i4", Category.ENGINE, "직렬 4기통", "I4", 1, 0, "가볍고 경쾌하게 도는 소리", SoundFx(bass = 3, rasp = 6)),
        Part("v8", Category.ENGINE, "V8", "V8", 2, 800, "묵직한 저음과 고동감", SoundFx(bass = 7, rasp = 4)),
        Part("v12", Category.ENGINE, "V12", "V12", 3, 2400, "매끄럽게 높이 뻗는 음색", SoundFx(bass = 5, rasp = 8)),
        Part("e4", Category.ENGINE, "다음 엔진", "?", 0, 0, "후보 중에서 고르는 중", kind = PartKind.PLACEHOLDER),
        Part("ltd", Category.ENGINE, "콜라보 사운드 팩", "LTD", 0, 0, "실제 차를 녹음한 한정 팩 (예시)", kind = PartKind.LIMITED),
    )
    private val intakes = listOf(
        Part("na", Category.INTAKE, "자연흡기", "NA", 0, 0, "회전 그대로의 깨끗한 소리"),
        Part("t1", Category.INTAKE, "터보 Stage 1", "TURBO 1", 1, 300, "은은한 흡기 휘슬", SoundFx(whistle = 3)),
        Part("t2", Category.INTAKE, "터보 Stage 2", "TURBO 2", 2, 600, "또렷한 휘슬과 블로오프", SoundFx(whistle = 5)),
        Part("t3", Category.INTAKE, "터보 Stage 3", "TURBO 3", 3, 1500, "크게 차오르는 휘슬", SoundFx(bass = 1, whistle = 8)),
        Part("sc", Category.INTAKE, "슈퍼차저", "SC", 2, 900, "회전 따라 올라가는 윙 소리", SoundFx(rasp = 1, whistle = 4)),
    )
    private val exhausts = listOf(
        Part("stock", Category.EXHAUST, "순정", "STOCK", 0, 0, "조용하고 단정한 배기"),
        Part("sport", Category.EXHAUST, "스포츠", "SPORT", 1, 400, "저음이 한 겹 두꺼워져요", SoundFx(bass = 1, rasp = 1, pop = 1)),
        Part("track", Category.EXHAUST, "트랙", "TRACK", 2, 1200, "직관 사운드를 실내에서만", SoundFx(bass = 2, rasp = 2, pop = 3)),
    )
    private val transmissions = listOf(
        Part("at", Category.TRANSMISSION, "자동", "AT", 0, 0, "부드럽게 이어지는 변속"),
        Part("dct", Category.TRANSMISSION, "DCT", "DCT", 1, 500, "짧고 단단한 변속음", SoundFx(pop = 1)),
        Part("mt", Category.TRANSMISSION, "수동 블립", "MT", 2, 1000, "내릴 때 회전을 맞추는 블립", SoundFx(pop = 2)),
    )

    val ALL: List<Part> = engines + intakes + exhausts + transmissions
    private val byId = ALL.associateBy { it.id }

    /** The parts every garage starts with: the base build. */
    val BASE_IDS = setOf("i4", "na", "stock", "at")

    private val engineIds = mapOf(EngineId.I4 to "i4", EngineId.V8 to "v8", EngineId.V12 to "v12")
    private val intakeIds = mapOf(
        IntakePart.NA to "na", IntakePart.TURBO1 to "t1", IntakePart.TURBO2 to "t2", IntakePart.TURBO3 to "t3", IntakePart.SUPERCHARGER to "sc",
    )
    private val exhaustIds = mapOf(ExhaustPart.STOCK to "stock", ExhaustPart.SPORT to "sport", ExhaustPart.TRACK to "track")
    private val transmissionIds = mapOf(TransmissionPart.AT to "at", TransmissionPart.DCT to "dct", TransmissionPart.MT_BLIP to "mt")

    fun of(category: Category): List<Part> = when (category) {
        Category.ENGINE -> engines
        Category.INTAKE -> intakes
        Category.EXHAUST -> exhausts
        Category.TRANSMISSION -> transmissions
    }

    fun part(id: String): Part? = byId[id]

    fun idOf(build: Build, category: Category): String = when (category) {
        Category.ENGINE -> engineIds.getValue(build.engine)
        Category.INTAKE -> intakeIds.getValue(build.intake)
        Category.EXHAUST -> exhaustIds.getValue(build.exhaust)
        Category.TRANSMISSION -> transmissionIds.getValue(build.transmission)
    }

    /** The build with this part in its slot; placeholders and limited packs change nothing. */
    fun apply(build: Build, part: Part): Build {
        if (part.kind != PartKind.NORMAL) return build
        return when (part.category) {
            Category.ENGINE -> build.copy(engine = engineIds.entries.first { it.value == part.id }.key)
            Category.INTAKE -> build.copy(intake = intakeIds.entries.first { it.value == part.id }.key)
            Category.EXHAUST -> build.copy(exhaust = exhaustIds.entries.first { it.value == part.id }.key)
            Category.TRANSMISSION -> build.copy(transmission = transmissionIds.entries.first { it.value == part.id }.key)
        }
    }
}
