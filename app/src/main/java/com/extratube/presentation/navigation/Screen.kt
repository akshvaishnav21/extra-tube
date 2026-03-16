package com.extratube.presentation.navigation

sealed class Screen(val route: String) {
    object Search : Screen("search")
    object Player : Screen("player/{videoId}") {
        fun createRoute(videoId: String) = "player/$videoId"
    }
}
