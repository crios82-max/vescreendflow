package com.veplayer.app.ui

enum class VeDest(
    val route: String,
    val label: String,
) {
    Home("home", "Inicio"),
    Cameras("cameras", "Cámaras"),
    Radio("radio", "Radio"),
    YouTube("youtube", "YouTube"),
    Store("store", "Tienda"),
    Player("player", "Pantalla"),
    Map("map", "Mapa"),
    Settings("settings", "Ajustes"),
}
