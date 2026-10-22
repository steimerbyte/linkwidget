import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Credentials aus keystore.properties im Projekt-Root.
// Diese Datei steht in .gitignore und wird NICHT committet.
// Zum Erzeugen: cp keystore.properties.example keystore.properties
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}
android {
    namespace = "com.steimerbyte.linkwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.steimerbyte.linkwidget"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "1.2.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile") ?: "linkwidget.keystore")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")

    // Icon-Extraktion: findet die Icon-Adresse einer Website, inklusive
    // Groessensortierung, Manifest-Support und Redirect-Behandlung.

    // HTTP-Client, der die selbst aufgeloeste IP verwendet. Der
    // System-Resolver versagt auf manchen Geraeten; mit einem
    // eigenen Dns-Adapter wird die bereits aufgeloeste Adresse
    // verwendet, ohne das TLS-Zertifikat zu brechen.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.mm2d.touchicon:touchicon:0.9.9")
}
