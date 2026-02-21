pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // 补充：阿里云镜像（解决国内网络访问 Google/Maven 仓库慢/失败的问题）
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 补充：同样添加阿里云镜像，确保依赖下载稳定
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/central")
    }
}

rootProject.name = "bm-android-app"
include(":app")