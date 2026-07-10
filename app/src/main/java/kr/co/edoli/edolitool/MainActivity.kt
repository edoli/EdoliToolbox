package kr.co.edoli.edolitool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kr.co.edoli.edolitool.feature.clipboard.ClipboardAnalyzerRoute
import kr.co.edoli.edolitool.feature.colorpicker.CameraColorPickerFullscreenRoute
import kr.co.edoli.edolitool.feature.colorpicker.CameraColorPickerRoute
import kr.co.edoli.edolitool.feature.colorpicker.ImageColorPickerFullscreenRoute
import kr.co.edoli.edolitool.feature.home.HomeRoute
import kr.co.edoli.edolitool.feature.home.ToolDetailRoute
import kr.co.edoli.edolitool.feature.home.toolCatalog
import kr.co.edoli.edolitool.feature.pets.CodexPetsRoute
import kr.co.edoli.edolitool.feature.recentapps.RecentAppsWidgetRoute
import kr.co.edoli.edolitool.feature.solidcolor.SolidColorRoute
import kr.co.edoli.edolitool.feature.storage.StorageInspectorRoute
import kr.co.edoli.edolitool.ui.theme.EdoliToolboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdoliToolboxTheme {
                EdoliToolboxApp(
                    startToolId = intent.getStringExtra(EXTRA_START_TOOL_ID),
                )
            }
        }
    }

    companion object {
        const val EXTRA_START_TOOL_ID = "kr.co.edoli.edolitool.extra.START_TOOL_ID"
    }
}

@Composable
private fun EdoliToolboxApp(startToolId: String?) {
    val navController = rememberNavController()
    val startDestination = if (startToolId.isNullOrBlank()) {
        "home"
    } else {
        "tool/$startToolId"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable("home") {
            HomeRoute(
                tools = toolCatalog,
                onToolClick = { tool ->
                    navController.navigate("tool/${tool.id}")
                },
            )
        }

        composable(
            route = "tool/{toolId}",
            arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val toolId = backStackEntry.arguments?.getString("toolId").orEmpty()
            when (toolId) {
                "codex_pets" -> CodexPetsRoute(
                    onBackClick = navController::popBackStack,
                )

                "camera_color_picker" -> CameraColorPickerRoute(
                    onBackClick = navController::popBackStack,
                    onOpenCamera = {
                        navController.navigate("camera_color_picker/camera")
                    },
                    onOpenImage = { uri ->
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("selected_image_uri", uri)
                        navController.navigate("camera_color_picker/image")
                    },
                )

                "solid_color_screen" -> SolidColorRoute(
                    onBackClick = navController::popBackStack,
                )

                "clipboard_analyzer" -> ClipboardAnalyzerRoute(
                    onBackClick = navController::popBackStack,
                )

                "storage_inspector" -> StorageInspectorRoute(
                    onBackClick = navController::popBackStack,
                )

                "recent_apps_widget" -> RecentAppsWidgetRoute(
                    onBackClick = navController::popBackStack,
                )

                else -> {
                    val tool = toolCatalog.firstOrNull { it.id == toolId }
                    ToolDetailRoute(
                        tool = tool,
                        onBackClick = navController::popBackStack,
                    )
                }
            }
        }

        composable("camera_color_picker/camera") {
            CameraColorPickerFullscreenRoute(
                onExit = navController::popBackStack,
            )
        }

        composable("camera_color_picker/image") {
            val imageUri = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<android.net.Uri>("selected_image_uri")
            ImageColorPickerFullscreenRoute(
                imageUri = imageUri,
                onExit = navController::popBackStack,
            )
        }
    }
}
