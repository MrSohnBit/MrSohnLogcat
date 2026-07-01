import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.SystemColor.menu

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.mrsohn.mrsohnlogcat.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MrSohn Logcat"
            packageVersion = "1.0.4"

            windows {
                shortcut = true
                menu = true
            }
        }
    }
}
//        nativeDistributions {
//            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
//            packageName = "MrSohn Logcat"
//            packageVersion = "1.0.0"
//
//
//            windows {
//                shortcut = true // 윈도우 시작 메뉴 바로가기 자동 생성
//                menu = true
////                iconFile.set(project.file("icons/app_icon.ico")) // 윈도우용 아이콘
////                iconFile.set(project.file("src/main/resources/icon.ico"))
//            }
//            macOS {
//                dockName = "MrSohn Logcat"
////                iconFile.set(project.file("icons/app_icon.icns")) // Mac용 아이콘
//            }
//
//        }
//    }
//}