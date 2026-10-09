package com.semmyx.ledger.data.remote

import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/** Public National Bank of Ukraine reference rates. No personal data is sent. */
interface RateApi {
    @GET("NBUStatService/v1/statdirectory/exchange?json")
    suspend fun latest(): List<RateDto>

    companion object {
        fun create(): RateApi {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true).build()
            return Retrofit.Builder().baseUrl("https://bank.gov.ua/")
                .client(client).addConverterFactory(GsonConverterFactory.create())
                .build().create(RateApi::class.java)
        }
    }
}

data class RateDto(
    @SerializedName("cc") val currency: String?,
    @SerializedName("rate") val rate: Double?,
    @SerializedName("exchangedate") val exchangeDate: String?,
)
