package cat.daisy.daisySRV.status

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Activity
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

/**
 * Manages the Discord bot's status display
 */
class BotStatusManager(
    private val plugin: JavaPlugin,
    private val jda: JDA,
    private val config: FileConfiguration,
) {
    companion object {
        // Configuration paths
        private const val CONFIG_STATUS_ENABLED = "status.enabled"
        private const val CONFIG_STATUS_TYPE = "status.type"
        private const val CONFIG_STATUS_FORMAT = "status.format"
        private const val CONFIG_DEBUG = "settings.debug"

        // Default values
        private const val DEFAULT_STATUS_FORMAT = "{playerCount}/{maxPlayers} players online"
    }

    /**
     * Updates the bot's status with the current player count
     *
     * @param playerCount The current player count
     * @param maxPlayers The maximum player count
     */
    fun updateStatus(
        playerCount: Int,
        maxPlayers: Int,
    ) {
        // Check if status updates are enabled
        if (!config.getBoolean(CONFIG_STATUS_ENABLED, true)) return

        try {
            // Get the status format from config
            val format = config.getString(CONFIG_STATUS_FORMAT) ?: DEFAULT_STATUS_FORMAT

            // Format the status message
            val statusMessage =
                format
                    .replace("{playerCount}", playerCount.toString())
                    .replace("{maxPlayers}", maxPlayers.toString())

            // Get the activity type from config
            val activityType = getActivityType(config.getString(CONFIG_STATUS_TYPE) ?: "PLAYING")

            // Set the bot's activity
            jda.presence.activity = Activity.of(activityType, statusMessage)

            if (config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Updated bot status: $statusMessage (${activityType.name})")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to update bot status", e)
        }
    }

    /**
     * Gets the Activity.ActivityType from a string
     *
     * @param type The activity type as a string
     * @return The ActivityType enum value
     */
    private fun getActivityType(type: String): Activity.ActivityType =
        when (type.uppercase()) {
            "PLAYING" -> Activity.ActivityType.PLAYING
            "WATCHING" -> Activity.ActivityType.WATCHING
            "LISTENING" -> Activity.ActivityType.LISTENING
            "COMPETING" -> Activity.ActivityType.COMPETING
            "STREAMING" -> Activity.ActivityType.STREAMING
            else -> Activity.ActivityType.PLAYING // Default to PLAYING
        }
}
