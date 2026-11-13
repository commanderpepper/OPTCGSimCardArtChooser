package commanderpepper.optcgsimcardartchooser.simfolder

enum class HostOs {
    Windows, MacOS, Other;

    companion object {
        val current: HostOs by lazy {
            val name = System.getProperty("os.name").orEmpty().lowercase()
            when {
                name.startsWith("windows") -> Windows
                name.startsWith("mac") -> MacOS
                else -> Other
            }
        }
    }
}
