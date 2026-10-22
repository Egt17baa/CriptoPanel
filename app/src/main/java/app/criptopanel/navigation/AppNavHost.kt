package app.criptopanel.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
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
import app.criptopanel.coins.CoinDetailScreen
import app.criptopanel.coins.CoinDetailViewModel
import app.criptopanel.coins.TopCoinsScreen
import app.criptopanel.coins.TopCoinsViewModel
import app.criptopanel.core.ui.LiquidBackground
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.defi.ChainDetailScreen
import app.criptopanel.defi.ChainDetailViewModel
import app.criptopanel.defi.DefiScreen
import app.criptopanel.defi.DefiViewModel
import app.criptopanel.market.CategoriesScreen
import app.criptopanel.market.CategoryDetailScreen
import app.criptopanel.market.ExchangesScreen
import app.criptopanel.market.IndexChartScreen
import app.criptopanel.market.MoversScreen
import app.criptopanel.market.TrendingScreen
import app.criptopanel.market.MarketIndex
import app.criptopanel.market.OverviewScreen
import app.criptopanel.market.OverviewViewModel
import app.criptopanel.search.SearchScreen
import app.criptopanel.search.SearchViewModel
import kotlinx.coroutines.launch
import app.criptopanel.portfolio.PortfolioViewModel
import app.criptopanel.portfolio.PortfolioScreen
import app.criptopanel.portfolio.OperationViewModel
import app.criptopanel.portfolio.OperationScreen
import app.criptopanel.portfolio.HistoryScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import app.criptopanel.alerts.android.AlertService
import app.criptopanel.alerts.AlertsViewModel
import app.criptopanel.alerts.AlertFormViewModel
import app.criptopanel.alerts.AlertFormScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("market", "Mercado", Icons.Default.Home),
    Tab("top", "Top 100", Icons.AutoMirrored.Filled.List),
    Tab("defi", "DeFi", Icons.Default.Lock),
    Tab("portfolio", "Cartera", Icons.Default.AccountBox),
)

@Composable
private fun appContainer(): AppContainer = (LocalContext.current.applicationContext as CriptoPanelApp).container

@Composable
fun AppNavHost(pendingRoute: MutableStateFlow<String?> = MutableStateFlow(null)) {
    val nav = rememberNavController()
    val container = appContainer()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val insights = container.marketInsights
    val onCoin: (String) -> Unit = { id -> nav.navigate("coin/${Uri.encode(id)}") }
    val store = container.portfolioStore
    val portfolio by store.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) { store.load() }
    val context = LocalContext.current
    val alertStore = container.alertStore
    // While the app is open, run the watch service exactly when some alert is enabled.
    LaunchedEffect(alertStore) {
        alertStore.load()
        alertStore.data.map { d -> d.alerts.count { it.enabled } }.distinctUntilChanged()
            .collect { AlertService.sync(context, it) }
    }
    // A tapped notification asks for a screen.
    LaunchedEffect(pendingRoute) {
        pendingRoute.filterNotNull().collect { route ->
            nav.navigate(route) { launchSingleTop = true }
            pendingRoute.value = null
        }
    }
    val onAddAlert: (String?) -> Unit = { coinId ->
        nav.navigate(if (coinId == null) "alert" else "alert?coinId=${Uri.encode(coinId)}")
    }
    val onAddOperation: (String?) -> Unit = { coinId ->
        nav.navigate(if (coinId == null) "operation" else "operation?coinId=${Uri.encode(coinId)}")
    }

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute?.substringBefore('?') == tab.route,
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
            NavHost(nav, startDestination = "market", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                composable("market") {
                    OverviewScreen(
                        vm = viewModel { OverviewViewModel(container.marketRepository) },
                        fearGreed = viewModel(key = "fear-greed") { LoaderViewModel { insights.fearGreed(it) } },
                        movers = viewModel(key = "movers") { LoaderViewModel { insights.movers(it) } },
                        trending = viewModel(key = "trending") { LoaderViewModel { insights.trending(it) } },
                        onSearch = { nav.navigate("search") },
                        onIndex = { index -> nav.navigate("index/${index.name}") },
                        onCoin = onCoin,
                        onOpen = { route -> nav.navigate(route) },
                    )
                }
                composable("movers") {
                    MoversScreen(
                        vm = viewModel { LoaderViewModel { insights.movers(it) } },
                        onCoin = onCoin,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("trending") {
                    TrendingScreen(
                        vm = viewModel { LoaderViewModel { insights.trending(it) } },
                        onCoin = onCoin,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("categories") {
                    CategoriesScreen(
                        vm = viewModel { LoaderViewModel { insights.categories(it) } },
                        onCategory = { c -> nav.navigate("category/${Uri.encode(c.id)}?name=${Uri.encode(c.name)}") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("category/{id}?name={name}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    CategoryDetailScreen(
                        title = entry.arguments?.getString("name").orEmpty(),
                        vm = viewModel(key = "category-$id") { LoaderViewModel { insights.categoryDetail(id, it) } },
                        onCoin = onCoin,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("exchanges") {
                    ExchangesScreen(vm = viewModel { LoaderViewModel { insights.exchanges(it) } }, onBack = { nav.popBackStack() })
                }
                composable("index/{name}") { entry ->
                    val name = entry.arguments?.getString("name").orEmpty()
                    IndexChartScreen(
                        index = MarketIndex.entries.firstOrNull { it.name == name } ?: MarketIndex.TOTAL,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("top") {
                    TopCoinsScreen(
                        vm = viewModel { TopCoinsViewModel(container.marketRepository) },
                        onCoin = onCoin,
                    )
                }
                composable("coin/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    CoinDetailScreen(
                        vm = viewModel(key = "coin-$id") { CoinDetailViewModel(id, container.marketRepository) },
                        isFavorite = id in portfolio.favorites,
                        onToggleFavorite = { scope.launch { store.toggleFavorite(id) } },
                        onAddOperation = { onAddOperation(id) },
                        onAddAlert = { onAddAlert(id) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "portfolio?tab={tab}",
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry ->
                    PortfolioScreen(
                        initialTab = entry.arguments?.getInt("tab") ?: 0,
                        alerts = viewModel { AlertsViewModel(alertStore, container.alertMonitor, container.marketRepository, store) },
                        onAddAlert = { onAddAlert(null) },
                        onEditAlert = { id -> nav.navigate("alert?id=${Uri.encode(id)}") },
                        vm = viewModel { PortfolioViewModel(store, container.marketRepository) },
                        store = store,
                        onCoin = onCoin,
                        onAddOperation = { onAddOperation(null) },
                        onHistory = { coinId -> nav.navigate(if (coinId == null) "history" else "history?coinId=${Uri.encode(coinId)}") },
                    )
                }
                composable(
                    "operation?coinId={coinId}&id={id}",
                    arguments = listOf(
                        navArgument("coinId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val coinId = entry.arguments?.getString("coinId")
                    val editId = entry.arguments?.getString("id")
                    OperationScreen(
                        vm = viewModel { OperationViewModel(store, container.marketRepository, editId, coinId) },
                        search = viewModel(key = "operation-search") { SearchViewModel(container.marketRepository) },
                        onDone = { nav.popBackStack() },
                    )
                }
                composable(
                    "alert?coinId={coinId}&id={id}",
                    arguments = listOf(
                        navArgument("coinId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val coinId = entry.arguments?.getString("coinId")
                    val editId = entry.arguments?.getString("id")
                    AlertFormScreen(
                        vm = viewModel { AlertFormViewModel(alertStore, container.marketRepository, store, editId, coinId) },
                        search = viewModel(key = "alert-search") { SearchViewModel(container.marketRepository) },
                        onDone = { nav.popBackStack() },
                    )
                }
                composable(
                    "history?coinId={coinId}",
                    arguments = listOf(navArgument("coinId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entry ->
                    HistoryScreen(
                        store = store,
                        initialCoinId = entry.arguments?.getString("coinId"),
                        onEdit = { opId -> nav.navigate("operation?id=${Uri.encode(opId)}") },
                        onAdd = onAddOperation,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("search") {
                    SearchScreen(
                        vm = viewModel { SearchViewModel(container.marketRepository) },
                        onCoin = onCoin,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("defi") {
                    DefiScreen(
                        vm = viewModel { DefiViewModel(container.defiRepository) },
                        extras = container.defiExtras,
                        onChain = { name -> nav.navigate("chain/${Uri.encode(name)}") },
                        onCoin = onCoin,
                    )
                }
                composable("chain/{name}") { entry ->
                    val name = entry.arguments?.getString("name").orEmpty()
                    ChainDetailScreen(
                        chain = name,
                        vm = viewModel(key = "chain-$name") { ChainDetailViewModel(name, container.defiRepository) },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
