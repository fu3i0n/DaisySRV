package cat.daisy.daisySRV.embed

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import org.bukkit.configuration.file.FileConfiguration
import java.awt.Color
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the creation of Discord embeds for various events
 */
class EmbedManager(
    private val config: FileConfiguration,
) {
    companion object {
        // Configuration paths
        private const val CONFIG_EMBEDS_ENABLED = "embeds.enabled"
        private const val CONFIG_EMBEDS_COLORS_JOIN = "embeds.colors.join"
        private const val CONFIG_EMBEDS_COLORS_LEAVE = "embeds.colors.leave"
        private const val CONFIG_EMBEDS_COLORS_ACHIEVEMENT = "embeds.colors.achievement"
        private const val CONFIG_EMBEDS_COLORS_SERVER = "embeds.colors.server"
        private const val CONFIG_EMBEDS_COLORS_PLAYERLIST = "embeds.colors.playerlist"

        // Default colors (in case they're not in config)
        private const val DEFAULT_COLOR_JOIN = "#55FF55" // Green
        private const val DEFAULT_COLOR_LEAVE = "#FF5555" // Red
        private const val DEFAULT_COLOR_ACHIEVEMENT = "#FFAA00" // Gold
        private const val DEFAULT_COLOR_SERVER = "#55FFFF" // Aqua
        private const val DEFAULT_COLOR_PLAYERLIST = "#5555FF" // Blue
    }

    // Cache for parsed colors to avoid repeated parsing
    private val colorCache = ConcurrentHashMap<String, Color>()

    /**
     * Checks if embeds are enabled in the configuration
     *
     * @return true if embeds are enabled, false otherwise
     */
    fun areEmbedsEnabled(): Boolean = config.getBoolean(CONFIG_EMBEDS_ENABLED, true)

    /**
     * Creates an embed for a player join event
     *
     * @param playerName The name of the player who joined
     * @return A MessageEmbed for the join event
     */
    fun createPlayerJoinEmbed(playerName: String): MessageEmbed =
        try {
            EmbedBuilder()
                .setColor(parseColor(CONFIG_EMBEDS_COLORS_JOIN, DEFAULT_COLOR_JOIN))
                .setTitle("Player Joined")
                .setDescription("**$playerName** joined the server")
                .setThumbnail("https://www.mc-heads.net/avatar/$playerName")
                .setTimestamp(Instant.now())
                .build()
        } catch (e: Exception) {
            // Fallback to a simpler embed if there's an error
            EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("Player Joined")
                .setDescription("**$playerName** joined the server")
                .setTimestamp(Instant.now())
                .build()
        }

    /**
     * Creates an embed for a player leave event
     *
     * @param playerName The name of the player who left
     * @return A MessageEmbed for the leave event
     */
    fun createPlayerLeaveEmbed(playerName: String): MessageEmbed =
        try {
            EmbedBuilder()
                .setColor(parseColor(CONFIG_EMBEDS_COLORS_LEAVE, DEFAULT_COLOR_LEAVE))
                .setTitle("Player Left")
                .setDescription("**$playerName** left the server")
                .setThumbnail("https://www.mc-heads.net/avatar/$playerName")
                .setTimestamp(Instant.now())
                .build()
        } catch (e: Exception) {
            // Fallback to a simpler embed if there's an error
            EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("Player Left")
                .setDescription("**$playerName** left the server")
                .setTimestamp(Instant.now())
                .build()
        }

    /**
     * Creates an embed for an achievement/advancement event
     *
     * @param playerName The name of the player
     * @param achievementName The name of the achievement
     * @param description The description of the achievement
     * @return A MessageEmbed for the achievement event
     */
    fun createAchievementEmbed(
        playerName: String,
        achievementName: String,
        description: String,
    ): MessageEmbed {
        val embedBuilder =
            EmbedBuilder()
                .setColor(parseColor(CONFIG_EMBEDS_COLORS_ACHIEVEMENT, DEFAULT_COLOR_ACHIEVEMENT))
                .setTitle("Achievement Unlocked")
                .setDescription("**$playerName** earned the achievement: **$achievementName**")
                .setThumbnail("https://www.mc-heads.net/avatar/$playerName")
                .setTimestamp(Instant.now())

        // Add description if available
        if (description.isNotBlank()) {
            embedBuilder.addField("Description", description, false)
        }

        return embedBuilder.build()
    }

    /**
     * Creates an embed for server status updates (start/stop)
     *
     * @param isOnline Whether the server is online or offline
     * @param playerCount The current player count (optional)
     * @param maxPlayers The maximum player count (optional)
     * @return A MessageEmbed for the server status
     */
    fun createServerStatusEmbed(
        isOnline: Boolean,
        playerCount: Int = 0,
        maxPlayers: Int = 0,
    ): MessageEmbed {
        val embedBuilder =
            EmbedBuilder()
                .setColor(parseColor(CONFIG_EMBEDS_COLORS_SERVER, DEFAULT_COLOR_SERVER))
                .setTimestamp(Instant.now())

        if (isOnline) {
            embedBuilder
                .setTitle("Server Online")
                .setDescription("The server is now online")

            if (playerCount >= 0 && maxPlayers > 0) {
                embedBuilder.addField("Players", "$playerCount/$maxPlayers", true)
            }
        } else {
            embedBuilder
                .setTitle("Server Offline")
                .setDescription("The server is now offline")
        }

        return embedBuilder.build()
    }

    /**
     * Creates an embed for the player list command
     *
     * @param playerCount The current player count
     * @param maxPlayers The maximum player count
     * @param playerNames The list of player names
     * @return A MessageEmbed for the player list
     */
    fun createPlayerListEmbed(
        playerCount: Int,
        maxPlayers: Int,
        playerNames: List<String>,
    ): MessageEmbed {
        val embedBuilder =
            EmbedBuilder()
                .setColor(parseColor(CONFIG_EMBEDS_COLORS_PLAYERLIST, DEFAULT_COLOR_PLAYERLIST))
                .setTitle("Online Players")
                .setDescription("$playerCount/$maxPlayers players online")
                .setTimestamp(Instant.now())

        if (playerNames.isEmpty()) {
            embedBuilder.addField("Players", "No players online", false)
        } else {
            // Split player list into chunks if needed to avoid Discord's 1024 character field limit
            val chunks = splitIntoChunks(playerNames.sorted(), 1000)

            for (i in chunks.indices) {
                val title = if (chunks.size > 1) "Players (${i + 1}/${chunks.size})" else "Players"
                embedBuilder.addField(title, chunks[i].joinToString(", "), false)
            }
        }

        return embedBuilder.build()
    }

    /**
     * Parses a color from config
     *
     * @param configPath The configuration path
     * @param defaultColor The default color if not found
     * @return The Color object
     */
    private fun parseColor(
        configPath: String,
        defaultColor: String,
    ): Color {
        val colorString = config.getString(configPath) ?: defaultColor

        // Check if this color is already in the cache
        return colorCache.computeIfAbsent(colorString) {
            try {
                // Handle hex color codes (with or without #)
                val cleanHex = it.replace("#", "")
                Color.decode("#$cleanHex")
            } catch (e: Exception) {
                // If parsing fails, use default
                Color.decode(defaultColor)
            }
        }
    }

    /**
     * Splits a list of strings into chunks that won't exceed Discord's character limits
     *
     * @param items The list of items to split
     * @param maxChunkSize The maximum size of each chunk
     * @return A list of joined string chunks
     */
    private fun splitIntoChunks(
        items: List<String>,
        maxChunkSize: Int,
    ): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val currentChunk = mutableListOf<String>()
        var currentSize = 0

        for (item in items) {
            // Add 2 for the comma and space that will be added when joining
            val itemSize = item.length + 2

            if (currentSize + itemSize > maxChunkSize && currentChunk.isNotEmpty()) {
                // Current chunk would exceed limit, start a new chunk
                result.add(currentChunk.toList())
                currentChunk.clear()
                currentSize = 0
            }

            currentChunk.add(item)
            currentSize += itemSize
        }

        // Add the last chunk if it has items
        if (currentChunk.isNotEmpty()) {
            result.add(currentChunk)
        }

        return result
    }
}
