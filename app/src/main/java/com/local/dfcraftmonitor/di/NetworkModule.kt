package com.local.dfcraftmonitor.di

import com.local.dfcraftmonitor.data.remote.AmsHeadersInterceptor
import com.local.dfcraftmonitor.data.remote.CraftingApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/**
 * 网络层 Hilt 依赖图。提供 OkHttp、Retrofit、CraftingApi 单例。
 *
 * g_tkCalculator / AmsInterceptor 单例由 Hilt 按构造器注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHeadersInterceptor(): AmsHeadersInterceptor = AmsHeadersInterceptor()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        headersInterceptor: AmsHeadersInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(headersInterceptor)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        return Retrofit.Builder()
            .baseUrl("https://comm.ams.game.qq.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideCraftingApi(retrofit: Retrofit): CraftingApi = retrofit.create(CraftingApi::class.java)
}
