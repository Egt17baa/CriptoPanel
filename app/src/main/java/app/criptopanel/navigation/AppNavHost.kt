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
import androidx.compose.material.icons.filled.Menu
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
import app.criptopanel.tools.WhatIfViewModel
import app.criptopanel.tools.WhatIfScreen
import app.criptopanel.tools.MoreScreen
import app.criptopanel.tools.CompareViewModel
import app.criptopanel.tools.CompareScreen
import app.criptopanel.tools.CalculatorViewModel
import app.criptopanel.tools.CalculatorScreen
import app.criptopanel.legal.termsAccepted
import app.criptopanel.legal.acceptTerms
import app.criptopanel.legal.TermsGateScreen
import app.criptopanel.legal.LegalScreen
import app.criptopanel.legal.LegalDoc
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import app.criptopanel.learn.readLessons
import app.criptopanel.learn.lessonById
import app.criptopanel.learn.LessonScreen
import app.criptopanel.learn.LearnScreen
import app.criptopanel.learn.LESSONS
import app.criptopanel.learn.GlossaryScreen
import app.criptopanel.core.ui.message
import app.criptopanel.core.savedCurrencyCode
import app.criptopanel.core.saveCurrency
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.format.Money
import android.app.Activity
import app.criptopanel.security.TextCryptoScreen
import app.criptopanel.security.SecurityScreen
import app.criptopanel.security.GeneratorScreen
import app.criptopanel.security.FileHashScreen
import app.criptopanel.security.FileCryptoScreen
import app.criptopanel.security.BreachScreen
import app.criptopanel.security.VaultScreen
import app.criptopanel.security.VaultEntryEditScreen
import app.criptopanel.security.VaultEntryDetailScreen
import app.criptopanel.security.EntryType

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("market", "Mercado", Icons.Default.Home),
    Tab("top", "Top 100", Icons.AutoMirrored.Filled.List),
    Tab("defi", "DeFi", Icons.Default.Lock),
    Tab("portfolio", "Cartera", Icons.Default.AccountBox),
    Tab("more", "Más", Icons.Default.Menu),
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
            runCatching { nav.navigate(route) { launchSingleTop = true } }
            pendingRoute.value = null
        }
    }
    val onAddAlert: (String?) -> Unit = { coinId ->
        nav.navigate(if (coinId == null) "alert" else "alert?coinId=${Uri.encode(coinId)}")
    }
    val onAddOperation: (String?) -> Unit = { coinId ->
        nav.navigate(if (coinId == null) "operation" else "operation?coinId=${Uri.encode(coinId)}")
    }

    val alertsData by alertStore.data.collectAsStateWithLifecycle()
    var currencyNote by remember { mutableStateOf<String?>(null) }
    // In euros, refresh the saved rate at start-up (the last known one is used meanwhile and offline).
    LaunchedEffect(Unit) {
        if (savedCurrencyCode(context) == "EUR") {
            (container.fx.eurPerUsd() as? Outcome.Ok)?.let { saveCurrency(context, "EUR", it.value) }
        }
    }
    var termsOk by remember { mutableStateOf(termsAccepted(context)) }

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        if (!termsOk) {
            TermsGateScreen(onAccept = {
                acceptTerms(context)
                termsOk = true
            })
            return@Box
        }
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
                        vm = viewModel {
                            PortfolioViewModel(store, container.marketRepository, container::displayRates, { Money.current.perUsd })
                        },
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
                        vm = viewModel { OperationViewModel(store, container.marketRepository, container.historicalPrices, editId, coinId, perUsdOn = container::displayPerUsdOn) },
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
                        displayRates = container::displayRates,
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
                composable("more") {
                    MoreScreen(
                        activeAlerts = alertsData.alerts.count { it.enabled },
                        lessonsRead = readLessons(context).count { id -> LESSONS.any { it.id == id } },
                        onLearn = { nav.navigate("learn") },
                        onSecurity = { nav.navigate("security") },
                        onCompare = { nav.navigate("compare") },
                        onCalculator = { nav.navigate("calculator") },
                        onWhatIf = { nav.navigate("whatif") },
                        onAlerts = { nav.navigate("portfolio?tab=2") },
                        onLegal = { doc -> nav.navigate("legal/${doc.name}") },
                        currencyCode = Money.current.code,
                        currencyNote = currencyNote,
                        onCurrency = { code ->
                            scope.launch {
                                if (code == "EUR") {
                                    when (val r = container.fx.eurPerUsd(force = true)) {
                                        is Outcome.Ok -> {
                                            saveCurrency(context, "EUR", r.value)
                                            (context as? Activity)?.recreate()
                                        }
                                        is Outcome.Err -> currencyNote = "No se pudo obtener el cambio euro/dólar (${r.error.message()}). Inténtalo de nuevo."
                                    }
                                } else if (Money.current.code != "USD") {
                                    saveCurrency(context, "USD", null)
                                    (context as? Activity)?.recreate()
                                }
                            }
                        },
                    )
                }
                composable("security") { SecurityScreen(onTool = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
                composable("security/vault") {
                    VaultScreen(container.vault, totpOnly = false, onOpen = { nav.navigate("vault/entry/$it") }, onNew = { nav.navigate("vault/edit?type=${it.name}") }, onBack = { nav.popBackStack() })
                }
                composable("security/totp") {
                    VaultScreen(container.vault, totpOnly = true, onOpen = { nav.navigate("vault/entry/$it") }, onNew = { nav.navigate("vault/edit?type=${it.name}") }, onBack = { nav.popBackStack() })
                }
                composable("vault/entry/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    VaultEntryDetailScreen(container.vault, id, onEdit = { nav.navigate("vault/edit?id=$id") }, onBack = { nav.popBackStack() })
                }
                composable(
                    "vault/edit?id={id}&type={type}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    VaultEntryEditScreen(
                        container.vault,
                        id = entry.arguments?.getString("id"),
                        initialType = EntryType.entries.firstOrNull { it.name == entry.arguments?.getString("type") } ?: EntryType.LOGIN,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable("security/text") { TextCryptoScreen(onBack = { nav.popBackStack() }) }
                composable("security/file") { FileCryptoScreen(onBack = { nav.popBackStack() }) }
                composable("security/generator") { GeneratorScreen(onBack = { nav.popBackStack() }) }
                composable("security/breach") { BreachScreen(container.pwnedPasswords, onBack = { nav.popBackStack() }) }
                composable("security/hash") { FileHashScreen(onBack = { nav.popBackStack() }) }
                composable("learn") {
                    LearnScreen(
                        onLesson = { id -> nav.navigate("lesson/$id") },
                        onGlossary = { nav.navigate("glossary") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("lesson/{id}") { entry ->
                    val lesson = lessonById(entry.arguments?.getString("id").orEmpty()) ?: LESSONS.first()
                    LessonScreen(
                        lesson,
                        onNext = { next -> nav.navigate("lesson/$next") { popUpTo("learn") } },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("glossary") { GlossaryScreen(onBack = { nav.popBackStack() }) }
                composable("compare") {
                    CompareScreen(
                        vm = viewModel { CompareViewModel(container.marketRepository) },
                        search = viewModel(key = "compare-search") { SearchViewModel(container.marketRepository) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("calculator") {
                    CalculatorScreen(
                        vm = viewModel { CalculatorViewModel(container.marketRepository) },
                        search = viewModel(key = "calculator-search") { SearchViewModel(container.marketRepository) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("whatif") {
                    WhatIfScreen(
                        vm = viewModel { WhatIfViewModel(container.marketRepository) },
                        search = viewModel(key = "whatif-search") { SearchViewModel(container.marketRepository) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("legal/{doc}") { entry ->
                    val doc = LegalDoc.entries.firstOrNull { it.name == entry.arguments?.getString("doc") } ?: LegalDoc.TERMS
                    LegalScreen(doc, onBack = { nav.popBackStack() })
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
