package cat.daisy.daisySRV.status

import cat.daisy.daisySRV.DaisySRV
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Activity
import org.bukkit.configuration.file.FileConfiguration
import java.util.logging.Level

/**
 * Manages the Discord bot's status display
 */
class BotStatusManager(
    private val plugin: DaisySRV,
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
        private const val DEFAULT_STATUS_TYPE = "PLAYING"

        // Status update cooldown to prevent rate limiting (in milliseconds)
        private const val STATUS_UPDATE_COOLDOWN = 5000
    }

    private var statusEnabled: Boolean = true
    private var lastUpdateTime: Long = 0

    init {
        // Check if status is enabled
        statusEnabled = config.getBoolean(CONFIG_STATUS_ENABLED, true)

        if (statusEnabled) {
            plugin.logger.info("Bot status manager initialized")
        } else {
            plugin.logger.info("Bot status updates are disabled in config")
        }
    }

    /**
     * Updates the bot's status with player count information
     *
     * @param playerCount The current player count
     * @param maxPlayers The maximum player count
     */
    fun updateStatus(
        playerCount: Int,
        maxPlayers: Int,
    ) {
        if (!statusEnabled || plugin.isShuttingDown) return

        // Prevent updating too frequently (Discord rate limits)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < STATUS_UPDATE_COOLDOWN) {
            if (config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Skipping status update due to cooldown")
            }
            return
        }

        lastUpdateTime = currentTime

        // Get the status format and type from config
        val statusFormat = config.getString(CONFIG_STATUS_FORMAT) ?: DEFAULT_STATUS_FORMAT
        val statusTypeStr = config.getString(CONFIG_STATUS_TYPE) ?: DEFAULT_STATUS_TYPE

        // Format the status message
        val statusMessage =
            statusFormat
                .replace("{playerCount}", playerCount.toString())
                .replace("{maxPlayers}", maxPlayers.toString())

        try {
            // Determine the activity type
            val activityType = parseActivityType(statusTypeStr)

            // Update the bot's activity
            jda.presence.setPresence(Activity.of(activityType, statusMessage), false)

            if (config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Updated bot status: $statusTypeStr $statusMessage")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to update bot status", e)
        }
    }

    /**
     * Parses the activity type from string configuration
     *
     * @param type The activity type as string
     * @return The Activity.ActivityType enum value
     */
    private fun parseActivityType(type: String): Activity.ActivityType =
        when (type.uppercase()) {
            "WATCHING" -> Activity.ActivityType.WATCHING
            "LISTENING" -> Activity.ActivityType.LISTENING
            "COMPETING" -> Activity.ActivityType.COMPETING
            "STREAMING" -> Activity.ActivityType.STREAMING
            "CUSTOM_STATUS" -> Activity.ActivityType.CUSTOM_STATUS
            else -> Activity.ActivityType.PLAYING
        }

    /**
     * Resets the bot status to default value
     * Called when the server is stopping
     */
    fun resetStatus() {
        if (!statusEnabled || plugin.isShuttingDown) return

        try {
            jda.presence.setPresence(Activity.of(Activity.ActivityType.PLAYING, "Server offline"), false)
            plugin.logger.info("Reset bot status to 'Server offline'")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to reset bot status", e)
        }
    }

    /**
     * Sets a temporary status message
     * Useful for maintenance or special events
     *
     * @param message The temporary status message
     * @param activityType The activity type to use
     */
    fun setTemporaryStatus(
        message: String,
        activityType: Activity.ActivityType = Activity.ActivityType.PLAYING,
    ) {
        if (!statusEnabled || plugin.isShuttingDown) return

        try {
            jda.presence.setPresence(Activity.of(activityType, message), false)
            if (config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Set temporary bot status: $message")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to set temporary bot status", e)
        }
    }
}
