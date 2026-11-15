package com.origin.esportsengine.core
enum class TargetGame(
    val id: Int,
    val title: String,
    val packageName: String,
    val engineType: String,
    val defaultDownscale: Float
) {
    NONE(0, "Standby (Dormant)", "", "OS Default", 1.0f),
    BGMI(1, "BGMI (Krafton)", "com.pubg.imobile", "Unreal Engine 4", 0.60f),
    FF_INDIA(2, "Free Fire India", "com.dts.freefireth", "Unity Engine", 0.75f),
    FF_MAX(3, "Free Fire MAX", "com.dts.freefiremax", "Unity HD Engine", 0.70f);
    companion object {
        fun fromPackage(pkg: String?): TargetGame {
            return entries.find { it.packageName == pkg } ?: NONE
        }
    }
}
