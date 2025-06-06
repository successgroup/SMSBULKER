package com.gscube.smsbulker.di

import com.gscube.smsbulker.data.network.AccountApiService
import com.gscube.smsbulker.data.network.AnalyticsApiService
import com.gscube.smsbulker.data.network.ArkeselApi
import com.gscube.smsbulker.data.network.AuthApiService
import com.gscube.smsbulker.data.network.BulkSmsApiService
import com.gscube.smsbulker.utils.SecureStorage
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import com.gscube.smsbulker.BuildConfig
import android.util.Log

@ContributesTo(AppScope::class)
@Module
object NetworkModule {
    private const val BASE_URL = "https://sms.arkesel.com" // Arkesel API base URL

    @Provides
    @Singleton
    @Named("api_key")
    fun provideApiKey(secureStorage: SecureStorage): String {
        // Use the user's API key from secure storage
        return secureStorage.getApiKey() ?: ""
    }

    @Provides
    @Singleton
    @Named("sandbox_mode")
    fun provideSandboxMode(): Boolean {
        // Production mode
        return false
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(@Named("api_key") apiKey: String): Interceptor {
        return Interceptor { chain ->
            // Use the user's API key if available, otherwise fall back to the default
            val actualApiKey = if (apiKey.isNotBlank()) apiKey else BuildConfig.ARKESEL_API_KEY
            
            Log.d("NetworkModule", "Using API key: ${actualApiKey.take(4)}...${actualApiKey.takeLast(4)}")
            
            val request = chain.request().newBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("api-key", actualApiKey)
                .build()
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    // In the provideOkHttpClient method
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Add this connection spec to enable TLS 1.2
            .connectionSpecs(listOf(
                ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build()
            ))
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideAccountApiService(retrofit: Retrofit): AccountApiService {
        return retrofit.create(AccountApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideArkeselApi(retrofit: Retrofit): ArkeselApi {
        return retrofit.create(ArkeselApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideBulkSmsApiService(retrofit: Retrofit): BulkSmsApiService {
        return retrofit.create(BulkSmsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAnalyticsApiService(retrofit: Retrofit): AnalyticsApiService {
        return retrofit.create(AnalyticsApiService::class.java)
    }
}