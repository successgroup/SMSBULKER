package com.gscube.smsbulker.di

import android.util.Log
import com.gscube.smsbulker.data.network.PaystackApiService
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@ContributesTo(AppScope::class)
@Module
object FirebaseRetrofitModule {
    // Firebase Cloud Functions base URL - replace with your actual Firebase project URL
    private const val FIREBASE_BASE_URL = "https://smsbulker-api.onrender.com"

    @Provides
    @Singleton
    @Named("rateLimitInterceptor")
    fun provideRateLimitInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)
            
            // Check if we received a 429 Too Many Requests response
            if (response.code == 429) {
                // Get the Retry-After header value
                val retryAfterHeader = response.header("Retry-After")
                val retryAfterSeconds = retryAfterHeader?.toIntOrNull() ?: 10
                
                Log.d("RateLimitInterceptor", "Rate limited. Retry after $retryAfterSeconds seconds")
                
                // Close the current response before retrying
                response.close()
                
                // Wait for the specified time
                Thread.sleep(retryAfterSeconds * 1000L)
                
                // Retry the request once after waiting
                val newRequest = request.newBuilder().build()
                response = chain.proceed(newRequest)
            }
            
            response
        }
    }
    
    @Provides
    @Singleton
    @Named("firebaseOkHttpClient")
    fun provideFirebaseOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        @Named("rateLimitInterceptor") rateLimitInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("firebaseRetrofit")
    fun provideFirebaseRetrofit(
        @Named("firebaseOkHttpClient") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(FIREBASE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun providePaystackApiService(@Named("firebaseRetrofit") retrofit: Retrofit): PaystackApiService {
        return retrofit.create(PaystackApiService::class.java)
    }
}