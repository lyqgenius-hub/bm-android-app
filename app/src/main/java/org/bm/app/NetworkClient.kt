package org.bm.app

import org.bm.app.MobileContext

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * 通用响应包装类 - 对应你服务器返回的 JSON 结构
 */
data class ApiResponse<T>(
    val code: Int,
    val msg: String?,
    val tdata: T? // 对应 JS 中的 tdata 字段
)

/**
 * API 接口定义 - 对应 JS 中的 API_CONFIG
 */
interface ApiService {
    // 提交并更新设备上下文
    @POST("bm-mobile-server/api/mobile")
    suspend fun updateMobile(@Body context: MobileContext): ApiResponse<MobileContext>

    // 如果后续有其他接口（比如 mobileUser），可以直接在这里继续加
    // @POST("bm-mobile-server/api/mobileUser")
    // suspend fun updateMobileUser(...)
}

/**
 * 网络客户端全局单例
 */
object NetworkClient {
    // 你的服务器基础地址
    private const val BASE_URL = "http://114.67.240.15:8066/"

    // 配置 OkHttp (超时时间、日志拦截器等)
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // BASIC 打印请求行和响应状态，BODY 会打印出完整的 JSON 数据（方便调试）
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // 连接超时时间
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时时间
            .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时时间
            .addInterceptor(loggingInterceptor)
            .build()
    }

    // 初始化 Retrofit 实例
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            // 自动将实体类和 JSON 相互转换（替代你之前手工写的 JSON 组装）
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}