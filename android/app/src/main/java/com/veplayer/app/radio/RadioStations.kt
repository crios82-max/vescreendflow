package com.veplayer.app.radio

data class RadioStation(
    val id: String,
    val name: String,
    val city: String,
    val genre: String,
    val streamUrl: String,
)

object RadioStations {
    val all =
        listOf(
            RadioStation(
                id = "soma-groove",
                name = "SomaFM Groove Salad",
                city = "Global",
                genre = "Ambient",
                streamUrl = "https://ice1.somafm.com/groovesalad-128-mp3",
            ),
            RadioStation(
                id = "soma-beat",
                name = "SomaFM Beat Blender",
                city = "Global",
                genre = "Electronic",
                streamUrl = "https://ice1.somafm.com/beatblender-128-mp3",
            ),
            RadioStation(
                id = "radio-paradise",
                name = "Radio Paradise",
                city = "Global",
                genre = "Eclectic",
                streamUrl = "https://stream.radioparadise.com/aac-128",
            ),
            RadioStation(
                id = "france-inter",
                name = "France Inter (demo stream)",
                city = "Paris",
                genre = "Talk/Music",
                streamUrl = "https://icecast.radiofrance.fr/franceinter-midfi.mp3",
            ),
        )
}
