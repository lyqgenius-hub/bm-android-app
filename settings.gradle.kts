pluginManagement {
    repositories {
        // Gradle 插件专用镜像，必须放最前面，优先匹配
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        // Google 官方库镜像
        maven(url = "https://maven.aliyun.com/repository/google")
        // 公共镜像（已包含 mavenCentral 全量内容，无需重复配置 central）
        maven(url = "https://maven.aliyun.com/repository/public")
        // 官方仓库兜底，极端情况备用
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // 强制只使用当前文件配置的仓库，禁止项目内单独配置，避免混乱
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像优先
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        // Jitpack 阿里云镜像（比直连稳定）
        maven(url = "https://maven.aliyun.com/repository/jitpack")
        // 直连 Jitpack 兜底
        maven(url = "https://jitpack.io")
        // 官方仓库兜底
        google()
        mavenCentral()
    }
}

rootProject.name = "bm-android-app"
include(":app")