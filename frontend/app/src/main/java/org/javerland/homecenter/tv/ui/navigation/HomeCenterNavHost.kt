package org.javerland.homecenter.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.SharedFlow
import org.javerland.homecenter.tv.domain.MediaCategory
import org.javerland.homecenter.tv.ui.browse.BrowseScreen
import org.javerland.homecenter.tv.ui.detail.DetailScreen
import org.javerland.homecenter.tv.ui.home.HomeScreen
import org.javerland.homecenter.tv.ui.login.LoginScreen
import org.javerland.homecenter.tv.ui.music.MusicPlayerScreen
import org.javerland.homecenter.tv.ui.photo.PhotoViewerScreen
import org.javerland.homecenter.tv.ui.player.VideoPlayerScreen
import org.javerland.homecenter.tv.ui.server.ServerScreen
import org.javerland.homecenter.tv.ui.settings.SettingsScreen

@Composable
fun HomeCenterNavHost(
    startDestination: String,
    sessionExpired: SharedFlow<Unit>,
    navController: NavHostController = rememberNavController(),
) {
    // The server can invalidate a token at any moment—a password change in the management
    // UI logs out every television. Whatever is on screen at that point is showing data the
    // client is no longer allowed to fetch.
    LaunchedEffect(sessionExpired) {
        sessionExpired.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.SERVER) {
            ServerScreen(
                onSaved = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SERVER) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onChangeServer = { navController.navigate(Routes.SERVER) },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenCategory = { category -> navController.navigate(Routes.browse(category)) },
                onOpenItem = { mediaId -> navController.navigate(Routes.detail(mediaId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.BROWSE,
            arguments = listOf(navArgument(Routes.CATEGORY_ARG) { type = NavType.StringType }),
        ) {
            BrowseScreen(
                // Videos are worth a detail screen; a photo or a song is not. Making
                // somebody press OK twice to look at a picture would be one press too many.
                onOpenItem = { category, mediaId ->
                    val route = when (category) {
                        MediaCategory.VIDEO -> Routes.detail(mediaId)
                        MediaCategory.PHOTO -> Routes.photo(mediaId)
                        MediaCategory.AUDIO -> Routes.music(mediaId)
                    }
                    navController.navigate(route)
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.MEDIA_ID_ARG) { type = NavType.StringType }),
        ) {
            DetailScreen(
                onPlay = { mediaId, fromStart ->
                    navController.navigate(Routes.video(mediaId, fromStart))
                },
                onOpenItem = { mediaId -> navController.navigate(Routes.detail(mediaId)) },
            )
        }

        composable(
            route = Routes.VIDEO,
            arguments = listOf(
                navArgument(Routes.MEDIA_ID_ARG) { type = NavType.StringType },
                navArgument(Routes.FROM_START_ARG) { type = NavType.StringType },
            ),
        ) {
            VideoPlayerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.PHOTO,
            arguments = listOf(navArgument(Routes.MEDIA_ID_ARG) { type = NavType.StringType }),
        ) {
            PhotoViewerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.MUSIC,
            arguments = listOf(navArgument(Routes.MEDIA_ID_ARG) { type = NavType.StringType }),
        ) {
            MusicPlayerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onChangeServer = { navController.navigate(Routes.SERVER) },
            )
        }
    }
}
