plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)

}

android {
    namespace = "com.labactivity.studysync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.labactivity.studysync"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.google.firebase:firebase-auth:22.3.0")
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation ("com.google.guava:guava:31.0.1-android")
    implementation ("org.reactivestreams:reactive-streams:1.0.4")
    implementation(libs.credentials)
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.core.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics.buildtools)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation ("androidx.core:core:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("com.github.yalantis:ucrop:2.2.10")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    implementation ("com.firebaseui:firebase-ui-firestore:8.0.2")
    implementation ("com.tbuonomo:dotsindicator:5.0")
    implementation ("com.github.chrisbanes:PhotoView:2.3.0")
    implementation ("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation ("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation ("com.itextpdf:itext7-core:7.1.18") // if using iText (or)
    implementation ("com.google.code.gson:gson:2.10.1")












}
apply(plugin = "com.google.gms.google-services")
