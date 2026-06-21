package kr.co.edoli.edoltool

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
import kr.co.edoli.edoltool.feature.home.HomeRoute
import kr.co.edoli.edoltool.feature.home.ToolDetailRoute
import kr.co.edoli.edoltool.feature.home.toolCatalog
import kr.co.edoli.edoltool.ui.theme.EdolAndroidToolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdolAndroidToolTheme {
                EdolAndroidToolApp()
            }
        }
    }
}

@Composable
private fun EdolAndroidToolApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeRoute(
                tools = toolCatalog,
                onToolClick = { tool ->
                    navController.navigate("tool/${tool.id}")
                }
            )
        }

        composable(
            route = "tool/{toolId}",
            arguments = listOf(navArgument("toolId") { type = NavType.StringType })
        ) { backStackEntry ->
            val toolId = backStackEntry.arguments?.getString("toolId").orEmpty()
            val tool = toolCatalog.firstOrNull { it.id == toolId }
            ToolDetailRoute(
                tool = tool,
                onBackClick = navController::popBackStack
            )
        }
    }
}
