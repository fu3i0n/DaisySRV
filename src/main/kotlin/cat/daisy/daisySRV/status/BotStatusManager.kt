package cat.daisy.daisySRV.status

import cat.daisy.daisySRV.DaisySRV
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Activity
import org.bukkit.configuration.file.FileConfiguration
import java.util.concurrent.atomic.AtomicReference
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

        // Map of activity type strings to enum values (for faster lookups)
        private val ACTIVITY_TYPES =
            mapOf(
                "WATCHING" to Activity.ActivityType.WATCHING,
                "LISTENING" to Activity.ActivityType.LISTENING,
                "COMPETING" to Activity.ActivityType.COMPETING,
                "STREAMING" to Activity.ActivityType.STREAMING,
                "CUSTOM_STATUS" to Activity.ActivityType.CUSTOM_STATUS,
                "PLAYING" to Activity.ActivityType.PLAYING,
            )
    }

    private var statusEnabled: Boolean = true
    private var lastUpdateTime: Long = 0

    // Cache the current status to avoid unnecessary updates
    private val lastStatusMessage = AtomicReference<String>()
    private val lastActivityType = AtomicReference<Activity.ActivityType>()

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

        try {
            // Get the status format and type from config
            val statusFormat = config.getString(CONFIG_STATUS_FORMAT) ?: DEFAULT_STATUS_FORMAT
            val statusTypeStr = config.getString(CONFIG_STATUS_TYPE) ?: DEFAULT_STATUS_TYPE

            // Format the status message
            val statusMessage =
                statusFormat
                    .replace("{playerCount}", playerCount.toString())
                    .replace("{maxPlayers}", maxPlayers.toString())

            // Determine the activity type
            val activityType = parseActivityType(statusTypeStr)

            // Check if status is actually different before updating
            if (statusMessage == lastStatusMessage.get() && activityType == lastActivityType.get()) {
                if (config.getBoolean(CONFIG_DEBUG, false)) {
                    plugin.logger.info("Skipping identical status update")
                }
                return
            }

            // Prevent updating too frequently (Discord rate limits)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime < STATUS_UPDATE_COOLDOWN) {
                if (config.getBoolean(CONFIG_DEBUG, false)) {
                    plugin.logger.info("Queuing status update due to cooldown")
                }

                // Schedule the update to happen after the cooldown
                plugin.server.scheduler.runTaskLaterAsynchronously(
                    plugin,
                    Runnable {
                        updateStatusWithoutCheck(statusMessage, activityType)
                    },
                    (STATUS_UPDATE_COOLDOWN / 50).toLong(), // Convert ms to ticks
                )
                return
            }

            updateStatusWithoutCheck(statusMessage, activityType)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to update bot status", e)
        }
    }

    /**
     * Updates the bot's status without checks
     * Internal helper method to avoid code duplication
     */
    private fun updateStatusWithoutCheck(
        statusMessage: String,
        activityType: Activity.ActivityType,
    ) {
        try {
            // Update the bot's activity
            jda.presence.setPresence(Activity.of(activityType, statusMessage), false)

            // Update cache
            lastStatusMessage.set(statusMessage)
            lastActivityType.set(activityType)
            lastUpdateTime = System.currentTimeMillis()

            if (config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Updated bot status: ${activityType.name} $statusMessage")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to update bot status: ${e.message}", e)
        }
    }

    /**
     * Parses the activity type from string configuration
     *
     * @param type The activity type as string
     * @return The Activity.ActivityType enum value
     */
    private fun parseActivityType(type: String): Activity.ActivityType = ACTIVITY_TYPES[type.uppercase()] ?: Activity.ActivityType.PLAYING

    /**
     * Resets the bot status to default value
     * Called when the server is stopping
     */
    fun resetStatus() {
        if (!statusEnabled || plugin.isShuttingDown) return

        try {
            val statusMessage = "Server offline"
            val activityType = Activity.ActivityType.PLAYING

            jda.presence.setPresence(Activity.of(activityType, statusMessage), false)

            // Update cache
            lastStatusMessage.set(statusMessage)
            lastActivityType.set(activityType)

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

            // Update cache
            lastStatusMessage.set(message)
            lastActivityType.set(activityType)
            lastUpdateTime = System.currentTimeMillis()

            if (config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Set temporary bot status: $message")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to set temporary bot status", e)
        }
    }
}
