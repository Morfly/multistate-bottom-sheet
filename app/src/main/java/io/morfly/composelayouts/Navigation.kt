package io.morfly.composelayouts

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.morfly.composelayouts.bottomsheet.custom.CustomBottomSheetScreen
import io.morfly.composelayouts.bottomsheet.material3.Material3BottomSheetScreen
import io.morfly.composelayouts.bottomsheet.simplified.CustomSimplifiedBottomSheetScreen

enum class Destination {
    Menu, Material3Demo, CustomSimplifiedDemo, CustomDemo;
}

@Composable
fun Navigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Destination.Menu.name) {
        composable(Destination.Menu.name) {
            MenuScreen(onClick = { destination -> navController.navigate(destination.name) })
        }
        composable(Destination.Material3Demo.name) {
            Material3BottomSheetScreen()
        }
        composable(Destination.CustomSimplifiedDemo.name) {
            CustomSimplifiedBottomSheetScreen()
        }
        composable(Destination.CustomDemo.name) {
            CustomBottomSheetScreen()
        }
    }
}