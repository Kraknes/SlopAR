package com.example.ny_slopar.network

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface NveApiService {

    // 🔹 Existing single elevation request
    @GET("punkt")
    fun getElevation(
        @Query("ost") longitude: Double,
        @Query("nord") latitude: Double
    ): Call<ElevationResponse>

    // ✅ New function for batch elevation requests (supports multiple coordinates)
    @GET("punkt")
    fun getElevationBatch(
        @Query("koordsys") coordinateSystem: Int = 4258, // WGS84 / EPSG:4258
        @Query("punkter") coordinates: String // Format: "18.87,69.62;18.88,69.63;..."
    ): Call<ElevationResponse>


    @GET
    fun getElevationBatch(@Url url: String): Call<ElevationResponse>


}