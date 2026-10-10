package com.shubham.womensafety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.firebase.ui.auth.AuthUI
import com.google.maps.android.compose.MapType
import com.shubham.womensafety.guardiandetail.GuardianInfoViewModel
import com.shubham.womensafety.ui.navigation.AppNavGraph
import com.shubham.womensafety.ui.navigation.Routes
import com.shubham.womensafety.ui.theme.ColorPrimaryDark
import com.shubham.womensafety.ui.theme.DishaAITheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DishaAITheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()
                val guardianViewModel: GuardianInfoViewModel = viewModel()

                var currentRoute by remember { mutableStateOf(Routes.DASHBOARD) }
                var mapType by remember { mutableStateOf(MapType.NORMAL) }
                var showTopBarMenu by remember { mutableStateOf(false) }
                var showMapTypeMenu by remember { mutableStateOf(false) }

                val canNavigateBack = currentRoute != Routes.DASHBOARD

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        Column(
                            modifier = Modifier.background(ColorPrimaryDark)
                        ) {
                            DrawerHeader()
                        val drawerItemColors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.surface,
                            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            selectedIconColor = ColorPrimaryDark,
                            unselectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = ColorPrimaryDark,
                            unselectedTextColor = MaterialTheme.colorScheme.onPrimary
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Info, contentDescription = null) },
                            label = { Text(stringResource(R.string.drawer_guardian)) },
                            selected = currentRoute == Routes.GUARDIAN_INFO,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Routes.GUARDIAN_INFO) {
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = drawerItemColors,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            label = { Text(stringResource(R.string.drawer_location)) },
                            selected = currentRoute == Routes.MAPS,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Routes.MAPS) {
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = drawerItemColors,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "Account Settings",
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                            label = { Text(stringResource(R.string.drawer_logout)) },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                AuthUI.getInstance().signOut(this@MainActivity)
                            },
                            colors = drawerItemColors,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = when (currentRoute) {
                                            Routes.DASHBOARD -> stringResource(R.string.app_name)
                                            Routes.GUARDIAN_INFO -> "Guardian Information"
                                            Routes.ADD_GUARDIAN -> "Add Guardian"
                                            Routes.MAPS -> stringResource(R.string.title_activity_maps)
                                            else -> stringResource(R.string.app_name)
                                        }
                                    )
                                },
                                navigationIcon = {
                                    if (canNavigateBack) {
                                        IconButton(onClick = { navController.navigateUp() }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            coroutineScope.launch { drawerState.open() }
                                        }) {
                                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                                        }
                                    }
                                },
                                actions = {
                                    when (currentRoute) {
                                        Routes.GUARDIAN_INFO -> {
                                            IconButton(onClick = { showTopBarMenu = true }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Clear all")
                                            }
                                            DropdownMenu(
                                                expanded = showTopBarMenu,
                                                onDismissRequest = { showTopBarMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Delete ALL") },
                                                    onClick = {
                                                        guardianViewModel.onClear()
                                                        showTopBarMenu = false
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Delete, contentDescription = null)
                                                    }
                                                )
                                            }
                                        }
                                        Routes.MAPS -> {
                                            IconButton(onClick = { showMapTypeMenu = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Map options")
                                            }
                                            DropdownMenu(
                                                expanded = showMapTypeMenu,
                                                onDismissRequest = { showMapTypeMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.normal_map)) },
                                                    onClick = {
                                                        mapType = MapType.NORMAL
                                                        showMapTypeMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.hybrid_map)) },
                                                    onClick = {
                                                        mapType = MapType.HYBRID
                                                        showMapTypeMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.satellite_map)) },
                                                    onClick = {
                                                        mapType = MapType.SATELLITE
                                                        showMapTypeMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.terrain_map)) },
                                                    onClick = {
                                                        mapType = MapType.TERRAIN
                                                        showMapTypeMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = ColorPrimaryDark,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    ) { paddingValues ->
                        AppNavGraph(
                            navController = navController,
                            mapType = mapType,
                            onCurrentRouteChange = { route -> currentRoute = route ?: Routes.DASHBOARD },
                            guardianViewModel = guardianViewModel,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = stringResource(R.string.women_safety_app),
            modifier = Modifier
                .padding(start = 4.dp, top = 16.dp)
                .size(95.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.women_safety_app),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
