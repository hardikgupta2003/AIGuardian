package dev.hardik.aiguardian.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.Modifier

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToMedicines = { navController.navigate("medicines") },
                onNavigateToScams = { navController.navigate("scams") }
            )
        }
        composable("medicines") {
            MedicineScreen(onBack = { navController.popBackStack() })
        }
        composable("scams") {
            ScamLogScreen(onBack = { navController.popBackStack() })
        }


    }
}
