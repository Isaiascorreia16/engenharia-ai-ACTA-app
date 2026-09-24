// Os plugins declaram-se aqui com "apply false" para partilharem o mesmo classpath.
// O AGP 9 já compila Kotlin (Kotlin integrado): não se aplica o plugin kotlin-android.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
