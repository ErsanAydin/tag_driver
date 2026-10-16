package com.tagtakip.pro.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tagtakip.pro.presentation.dashboard.DashboardScreen
import com.tagtakip.pro.presentation.map.DistanceScreen
import com.tagtakip.pro.presentation.shift.GlobalTripFab
import com.tagtakip.pro.presentation.shift.ShiftScreen
import com.tagtakip.pro.presentation.shift.ShiftViewModel
import com.tagtakip.pro.presentation.shift.TripSummaryDialog

@Composable
fun TagTakipNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Activity kapsamlı tek ShiftViewModel: global FAB, Mesai ekranı ve yolculuk özeti aynı durumu paylaşır.
    val shiftViewModel: ShiftViewModel = hiltViewModel()
    val shiftState by shiftViewModel.uiState.collectAsStateWithLifecycle()
    val isTripActive by shiftViewModel.isTripActive.collectAsStateWithLifecycle()
    val tripSummary by shiftViewModel.tripSummary.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(shiftState.message) {
        shiftState.message?.let {
            snackbarHostState.showSnackbar(it)
            shiftViewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Özet ekranındaki "Kazanç Ekle" FAB'ı sağda olduğu için yolculuk FAB'ı solda durur
        floatingActionButtonPosition = FabPosition.Start,
        floatingActionButton = {
            if (shiftState.isShiftActive) {
                GlobalTripFab(
                    isTripActive = isTripActive,
                    enabled = !shiftState.isBusy && tripSummary == null,
                    onStartTrip = shiftViewModel::onStartTrip,
                    onEndTrip = shiftViewModel::onEndTripRequested
                )
            }
        },
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        // Sekmeler mesai/yolculuk durumundan bağımsız olarak her zaman tıklanabilir
                        onClick = { navController.navigateToTab(item.route) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Screen.Dashboard> {
                DashboardScreen(onNavigateToShift = { navController.navigateToTab(Screen.Shift) })
            }
            composable<Screen.Shift> { ShiftScreen(viewModel = shiftViewModel) }
            composable<Screen.Distance> { DistanceScreen() }
        }
    }

    // Yolculuk hangi ekrandan bitirilirse bitirilsin özet burada gösterilir
    tripSummary?.let { summary ->
        TripSummaryDialog(
            summary = summary,
            isBusy = shiftState.isBusy,
            onSave = shiftViewModel::onTripSummarySave,
            onSkip = shiftViewModel::onTripSummarySkip
        )
    }
}

/**
 * Sekme geçişi. Tüm geçişler (alt bar ve ekran içi butonlar) aynı kuralla yapılmalı:
 * Özet'ten düz `navigate(Shift)` ile Mesai yığına eklenince, Özet sekmesine basıldığında
 * saveState/restoreState o yığını geri yüklüyor ve kullanıcı tekrar Mesai'ye düşüyordu.
 */
private fun NavHostController.navigateToTab(route: Screen) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
