package cat.daisy.daisySRV.embed

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import org.bukkit.configuration.file.FileConfiguration
import java.awt.Color
import java.time.Instant

/**
 * Manages the creation of Discord embeds for various events
 */
class EmbedManager(
    private val config: FileConfiguration,
) {
    companion object {
        // Configuration paths
        private const val CONFIG_EMBED_ENABLED = "embeds.enabled"
        private const val CONFIG_EMBED_COLOR_JOIN = "embeds.colors.join"
        private const val CONFIG_EMBED_COLOR_LEAVE = "embeds.colors.leave"
        private const val CONFIG_EMBED_COLOR_ACHIEVEMENT = "embeds.colors.achievement"
        private const val CONFIG_EMBED_COLOR_SERVER = "embeds.colors.server"
        private const val CONFIG_EMBED_COLOR_PLAYERLIST = "embeds.colors.playerlist"

        // Default colors
        private val DEFAULT_COLOR_JOIN = Color(0x55FF55) // Green
        private val DEFAULT_COLOR_LEAVE = Color(0xFF5555) // Red
        private val DEFAULT_COLOR_ACHIEVEMENT = Color(0xFFAA00) // Gold
        private val DEFAULT_COLOR_SERVER = Color(0x55FFFF) // Aqua
        private val DEFAULT_COLOR_PLAYERLIST = Color(0x5555FF) // Blue
    }

    /**
     * Checks if embeds are enabled in the configuration
     *
     * @return true if embeds are enabled, false otherwise
     */
    fun areEmbedsEnabled(): Boolean = config.getBoolean(CONFIG_EMBED_ENABLED, true)

    /**
     * Creates an embed for a player join event
     *
     * @param playerName The name of the player who joined
     * @return The created MessageEmbed
     */
    fun createPlayerJoinEmbed(playerName: String): MessageEmbed {
        val color = getColorFromConfig(CONFIG_EMBED_COLOR_JOIN, DEFAULT_COLOR_JOIN)

        return EmbedBuilder()
            .setColor(color)
            .setTitle("Player Joined")
            .setDescription("**$playerName** joined the server")
            .setTimestamp(Instant.now())
            .build()
    }

    /**
     * Creates an embed for a player leave event
     *
     * @param playerName The name of the player who left
     * @return The created MessageEmbed
     */
    fun createPlayerLeaveEmbed(playerName: String): MessageEmbed {
        val color = getColorFromConfig(CONFIG_EMBED_COLOR_LEAVE, DEFAULT_COLOR_LEAVE)

        return EmbedBuilder()
            .setColor(color)
            .setTitle("Player Left")
            .setDescription("**$playerName** left the server")
            .setTimestamp(Instant.now())
            .build()
    }

    /**
     * Creates an embed for a player achievement event
     *
     * @param playerName The name of the player who earned the achievement
     * @param achievementName The name of the achievement
     * @param achievementDescription The description of the achievement
     * @return The created MessageEmbed
     */
    fun createAchievementEmbed(
        playerName: String,
        achievementName: String,
        achievementDescription: String,
    ): MessageEmbed {
        val color = getColorFromConfig(CONFIG_EMBED_COLOR_ACHIEVEMENT, DEFAULT_COLOR_ACHIEVEMENT)

        return EmbedBuilder()
            .setColor(color)
            .setTitle("Achievement Unlocked")
            .setDescription("**$playerName** earned the achievement **$achievementName**")
            .setTimestamp(Instant.now())
            .build()
    }

    /**
     * Creates an embed for a server status change event
     *
     * @param online Whether the server is online or offline
     * @param playerCount The current player count (if online)
     * @param maxPlayers The maximum player count (if online)
     * @return The created MessageEmbed
     */
    fun createServerStatusEmbed(
        online: Boolean,
        playerCount: Int = 0,
        maxPlayers: Int = 0,
    ): MessageEmbed {
        val color = getColorFromConfig(CONFIG_EMBED_COLOR_SERVER, DEFAULT_COLOR_SERVER)

        val builder =
            EmbedBuilder()
                .setColor(color)
                .setTitle(if (online) "Server Online" else "Server Offline")
                .setTimestamp(Instant.now())

        if (online) {
            builder.setDescription("The server is now online")
            builder.addField("Players", "$playerCount/$maxPlayers", true)
        } else {
            builder.setDescription("The server is now offline")
        }

        return builder.build()
    }

    /**
     * Creates an embed for the player list command
     *
     * @param playerCount The current player count
     * @param maxPlayers The maximum player count
     * @param playerList The list of online players
     * @return The created MessageEmbed
     */
    fun createPlayerListEmbed(
        playerCount: Int,
        maxPlayers: Int,
        playerList: List<String>,
    ): MessageEmbed {
        val color = getColorFromConfig(CONFIG_EMBED_COLOR_PLAYERLIST, DEFAULT_COLOR_PLAYERLIST)

        val builder =
            EmbedBuilder()
                .setColor(color)
                .setTitle("Online Players")
                .setDescription("There are **$playerCount/$maxPlayers** players online")
                .setTimestamp(Instant.now())

        if (playerList.isNotEmpty()) {
            builder.addField("Players", playerList.joinToString("\n"), false)
        } else {
            builder.addField("Players", "No players online", false)
        }

        return builder.build()
    }

    /**
     * Gets a color from the configuration
     *
     * @param path The configuration path
     * @param defaultColor The default color to use if not found in config
     * @return The color from config or default
     */
    private fun getColorFromConfig(
        path: String,
        defaultColor: Color,
    ): Color {
        val hexColor = config.getString(path)

        return if (hexColor != null) {
            try {
                // Parse hex color (with or without #)
                val colorStr = hexColor.removePrefix("#")
                Color(Integer.parseInt(colorStr, 16))
            } catch (e: Exception) {
                defaultColor
            }
        } else {
            defaultColor
        }
    }
}
