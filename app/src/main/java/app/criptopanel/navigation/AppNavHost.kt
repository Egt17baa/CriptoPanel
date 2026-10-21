package app.criptopanel.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.criptopanel.AppContainer
import app.criptopanel.CriptoPanelApp
import app.criptopanel.market.OverviewScreen
import app.criptopanel.market.OverviewViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("market", "Mercado", Icons.Default.Home),
    Tab("top", "Top 100", Icons.AutoMirrored.Filled.List),
    Tab("defi", "DeFi", Icons.Default.Lock),
)

@Composable
private fun appContainer(): AppContainer = (LocalContext.current.applicationContext as CriptoPanelApp).container

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val container = appContainer()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "market", modifier = Modifier.padding(padding)) {
            composable("market") {
                OverviewScreen(
                    vm = viewModel { OverviewViewModel(container.marketRepository) },
                    onSearch = { nav.navigate("search") },
                )
            }
            composable("top") { Text("Próximamente") }
            composable("defi") { Text("Próximamente") }
        }
    }
}
