package com.shubham.womensafety.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.maps.android.compose.MapType
import com.shubham.womensafety.guardiandetail.GuardianInfoViewModel
import com.shubham.womensafety.ui.screens.AddGuardianScreen
import com.shubham.womensafety.ui.screens.DashboardScreen
import com.shubham.womensafety.ui.screens.GuardianInfoScreen
import com.shubham.womensafety.ui.screens.MapsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    mapType: MapType,
    onCurrentRouteChange: (String?) -> Unit,
    guardianViewModel: GuardianInfoViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            onCurrentRouteChange(entry.destination.route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToGuardianInfo = {
                    navController.navigate(Routes.GUARDIAN_INFO)
                },
                onNavigateToMaps = {
                    navController.navigate(Routes.MAPS)
                }
            )
        }

        composable(Routes.GUARDIAN_INFO) {
            GuardianInfoScreen(
                onNavigateToAddGuardian = {
                    navController.navigate(Routes.ADD_GUARDIAN)
                },
                viewModel = guardianViewModel
            )
        }

        composable(Routes.ADD_GUARDIAN) {
            AddGuardianScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = guardianViewModel
            )
        }

        composable(Routes.MAPS) {
            MapsScreen(mapType = mapType)
        }
    }
}
