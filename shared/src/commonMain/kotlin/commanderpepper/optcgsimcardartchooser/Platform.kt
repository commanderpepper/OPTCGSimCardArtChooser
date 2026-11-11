package commanderpepper.optcgsimcardartchooser

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform