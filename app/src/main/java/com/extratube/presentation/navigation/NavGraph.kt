package com.extratube.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.extratube.presentation.player.PlayerScreen
import com.extratube.presentation.search.SearchScreen

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Search.route) {
        composable(Screen.Search.route) {
            SearchScreen(onVideoClick = { id ->
                navController.navigate(Screen.Player.createRoute(id))
            })
        }
        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            PlayerScreen(
                videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
