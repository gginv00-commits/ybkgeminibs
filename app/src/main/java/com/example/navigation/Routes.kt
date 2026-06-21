package com.example.navigation

import kotlinx.serialization.Serializable

@Serializable
object LandingRoute

@Serializable
object DashboardRoute

@Serializable
data class RoomRoute(val roomId: Int)

@Serializable
object AdminRoute
