package com.myapp.familycode.data.api

import com.myapp.familycode.data.model.SimpleResponse
import com.myapp.familycode.data.model.SyncResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface GoogleSheetsApi {

    @FormUrlEncoded
    @POST
    suspend fun postAction(
        @Url url: String,
        @Field("action") action: String,
        @Field("api_key") apiKey: String,
        @Field("device_id") deviceId: String? = null,
        @Field("device_name") deviceName: String? = null,
        @Field("bank_name") bankName: String? = null,
        @Field("otp_code") otpCode: String? = null,
        @Field("full_message") fullMessage: String? = null
    ): SimpleResponse

    @GET
    suspend fun fetchData(
        @Url url: String,
        @Query("action") action: String = "fetch_data",
        @Query("api_key") apiKey: String
    ): SyncResponse
}
