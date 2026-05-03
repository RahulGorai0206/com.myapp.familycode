package com.myapp.familycode.data.api

import com.myapp.familycode.data.model.SimpleResponse
import com.myapp.familycode.data.model.SyncResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
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

    @FormUrlEncoded
    @POST
    suspend fun fetchData(
        @Url url: String,
        @Field("action") action: String = "fetch_data",
        @Field("api_key") apiKey: String
    ): SyncResponse

    @FormUrlEncoded
    @POST
    suspend fun deleteExpiredOtps(
        @Url url: String,
        @Field("action") action: String = "delete_expired_otps",
        @Field("api_key") apiKey: String
    ): SimpleResponse

    @FormUrlEncoded
    @POST
    suspend fun deleteOtp(
        @Url url: String,
        @Field("action") action: String = "delete_otp",
        @Field("api_key") apiKey: String,
        @Field("timestamp") timestamp: String,
        @Field("device_id") deviceId: String
    ): SimpleResponse
}
