plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.emrekekil.watchface.dijital"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.emrekekil.watchface.dijital"
        // Watch Face Format sürüm 2: Wear OS 5 (API 34) ve üzeri.
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Kadran kaynakları yalnızca XML'den adlarıyla anılır; küçültme onları silmemeli.
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
