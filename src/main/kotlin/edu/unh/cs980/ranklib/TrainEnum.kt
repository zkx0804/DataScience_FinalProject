@file:JvmName("KotTrainEnum")
package edu.unh.cs980.ranklib

/**
 * Class: TrainEnum
 * Desc: Contains training method names (used for type safety)
 */
enum class TrainEnum(val value: String) : CharSequence by value {
    HYPERLINK_QUERY("hyperlink_query"),

    AVERAGE_ABSTRACT_QUERY("average_abstract_query"),

    ABSTRACT_SDM_COMPONENTS("abstract_sdm_components"),
    ABSTRACT_SDM_ALPHA("abstract_sdm_alpha"),
    ABSTRACT_SDM_QUERY("abstract_sdm_query"),

//    NAT_SDM_QUERY("nat_sdm_query"),

    SDM_ALPHA("sdm_alpha"),
    SDM_COMPONENTS("sdm_components"),
    SDM_SECTION("sdm_section"),
    SDM_QUERY("sdm_query"),

    TFIDF_SECTION("tfidf_section"),
    TFIDF_SECTION_QUERY("tfidf_section_query"),

    BM25_SECTION("bm25_section"),

    SDM_EXPANSION_COMPONENTS("sdm_expansion_components"),
    SDM_EXPANSION_QUERY("sdm_expansion_query"),

    STRING_SIMILARITY_COMPONENTS("string_similarity_components"),
    STRING_SIMILARITY_SECTION("string_similarity_section"),
    STRING_SIMILARITY_QUERY("string_similarity_query"),

    COMBINED_QUERY("combined_query"),
    SUPER_AWESOME_TEAMWORK_QUERY("super_awesome_teamwork_query"),
    ;

    override fun toString() = value

    companion object {
        private val map: Map<String, TrainEnum> = TrainEnum.values().associateBy(TrainEnum::value)

        // Converts a string method name to the corresponding enum class (if it exists)
        fun fromString(string: String) = map[string]

        // Gets all of the enum method's corresponding string names
        fun getCommands() = TrainEnum.values()
            .map(TrainEnum::value)
            .sorted()
    }
}
