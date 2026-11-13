package commanderpepper.optcgsimcardartchooser.catalog.traits

/**
 * Reviewed vocabulary of card traits. Sources join a card's traits with spaces
 * ("Animal Kingdom Pirates The Four Emperors"), so splitting needs to know which word runs are
 * one trait. Seeded from the optcgapi data (Nov 2026) and corrected by hand.
 *
 * When a seed run warns about an unknown trait, add it here if it's real.
 */
internal val KNOWN_TRAITS: List<String> = listOf(
    "?", // A real trait (e.g. Imu), not a placeholder.
    "Accino Family", "Admiral", "Alabasta", "Alchemi", "Alvida Pirates", "Amazon Lily", "Animal",
    "Animal Kingdom Pirates", "Arlong Pirates", "Asuka Island",
    "Baroque Works", "Barto Club", "Baterilla", "Beautiful Pirates", "Bellamy Pirates", "Big Mom Pirates",
    "Biological Weapon", "Black Cat Pirates", "Blackbeard Pirates", "Blackbeard Pirates Allies", "Bluejam Pirates",
    "Bonney Pirates", "Botanist", "Bowin Island", "Brownbeard Pirates", "Buggy Pirates", "Buggy's Delivery",
    "Caribou Pirates", "Celestial Dragons", "CP0", "CP6", "CP7", "CP8", "CP9", "Cross Guild", "Crown Island",
    "Donquixote Pirates", "Drake Pirates", "Dressrosa", "Drum Kingdom",
    "East Blue", "Egghead", "Elbaph", "Eldoraggo Crew", "Evil Black Drum Kingdom",
    "Fake Straw Hat Crew", "Fallen Monk Pirates", "FILM", "Firetank Pirates", "Fish-Man", "Fish-Man Island",
    "Five Elders", "Flevance", "Flying Pirates", "Foolshout Island", "Former Arlong Pirates", "Former Baroque Works",
    "Former Big Mom Pirates", "Former CP9", "Former Navy", "Former Rocks Pirates", "Former Roger Pirates",
    "Former Rolling Pirates", "Former Rumbar Pirates", "Former Whitebeard Pirates", "Foxy Pirates", "Frost Moon Village",
    "Galley-La Company", "Gasparde Pirates", "GERMA 66", "Giant", "Giant Pirates", "Goa Kingdom", "Golden Lion Pirates",
    "Grantesoro", "Gyro Pirates",
    "Happosui Army", "Hawkins Pirates", "Heart Pirates", "Homies", "Hot Springs Island",
    "Impel Down",
    "Jailer Beast", "Jaya", "Journalist",
    "Kid Pirates", "King of the Pirates", "Kingdom of GERMA", "Kingdom of Prodence", "Kouzuki Clan", "Krieg Pirates",
    "Kuja Pirates", "Kurozumi Clan",
    "Land of Wano", "Long Ring Long Land", "Lulucia Kingdom", "Lunarian",
    "Mary Geoise", "Mecha Island", "Merfolk", "Minks", "Mogaro Kingdom", "Monkey Mountain Alliance",
    "Mountain Bandits", "Muggy Kingdom", "Mugiwara Chase", "Music",
    "Navy", "Neo Navy", "Neptunian", "New Fish-Man Pirates", "New Giant Pirates",
    "ODYSSEY", "Ohara", "Omatsuri Island", "On-Air Pirates",
    "Peachbeard Pirates", "Plague", "Punk Hazard",
    "Red-Haired Pirates", "Red-Haired Pirates Allies", "Revolutionary Army", "Rocks Pirates", "Roger Pirates",
    "Rolling Pirates", "Rumbar Pirates",
    "Sabaody Archipelago", "Scientist", "Senor Pink's Wife", "Seraphim", "Shandian Warrior", "Shipbuilding Town",
    "Sky Island", "SMILE", "Sniper Island", "Space Pirates", "Spade Pirates", "Sprite", "Straw Hat Crew",
    "Supernovas", "SWORD",
    "The Akazaya Nine", "The Flying Fish Riders", "The Four Emperors", "The Franky Family", "The House of Lambs",
    "The Moon", "The Owner of Cindry's Shadow", "The Pirates Fest", "The Seven Warlords of the Sea", "The Sun Pirates",
    "The Tontattas", "The Victims' Club", "The Vinsmoke Family", "Thriller Bark Pirates", "Treasure Pirates",
    "Trump Pirates",
    "Vassals",
    "Water Seven", "Weevil's Mother", "Whitebeard Pirates", "Whitebeard Pirates Allies", "Whole Cake Island",
    "Windmill Village", "World Government", "World Pirates",
    "Yonta Maria Fleet",
)

/** Misspellings and inconsistent names seen in source data → the canonical trait. */
internal val TRAIT_ALIASES: Map<String, String> = mapOf(
    "Straw Hat Cre" to "Straw Hat Crew",
    "New Giant Pirate Crew" to "New Giant Pirates",
    "Barto Club Pirates" to "Barto Club",
)

/** Whole trait values that are source errors (another column's data or placeholders). */
internal val JUNK_TRAIT_VALUES: Set<String> = setOf("null", "none", "-", "strike", "slash", "special", "wisdom", "ranged")
