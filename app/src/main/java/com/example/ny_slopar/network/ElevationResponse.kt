package com.example.ny_slopar.network

import com.google.gson.annotations.SerializedName

data class ElevationResponse(
    @SerializedName("koordsys") val coordinateSystem: Int,
    @SerializedName("punkter") val points: List<ElevationPoint>
)

data class ElevationPoint(
    @SerializedName("datakilde") val dataSource: String,
    @SerializedName("terreng") val terrain: String?,  // ✅ Added this
    @SerializedName("x") val longitude: Double,
    @SerializedName("y") val latitude: Double,
    @SerializedName("z") val elevation: Double
)