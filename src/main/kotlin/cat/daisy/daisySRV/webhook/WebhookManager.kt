package cat.daisy.daisySRV.webhook

import cat.daisy.daisySRV.DaisySRV
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.util.logging.Level

/**
 * Manages Discord webhooks for sending messages with player avatars and names
 */
class WebhookManager(
    private val plugin: DaisySRV,
    private val discordChannel: TextChannel,
    private val config: FileConfiguration
) {
    companion object {
        // Configuration paths
        private const val CONFIG_WEBHOOK_ENABLED = "webhook.enabled"
        private const val CONFIG_WEBHOOK_URL = "webhook.url"
        private const val CONFIG_WEBHOOK_NAME = "webhook.name"
        private const val CONFIG_WEBHOOK_AVATAR_URL = "webhook.avatar-url"
        private const val CONFIG_DEBUG = "settings.debug"
        
        // Default values
        private const val DEFAULT_WEBHOOK_NAME = "DaisySRV"
        private const val DEFAULT_WEBHOOK_AVATAR_URL = "https://www.mc-heads.net/avatar/{username}"
    }
    
    private var webhookUrl: String? = null
    private var webhookEnabled = false
    
    init {
        // Check if webhook is enabled
        webhookEnabled = config.getBoolean(CONFIG_WEBHOOK_ENABLED, false)
        
        if (webhookEnabled) {
            initializeWebhook()
        }
    }
    
    /**
     * Initializes the webhook configuration
     */
    private fun initializeWebhook() {
        webhookUrl = config.getString(CONFIG_WEBHOOK_URL)
        
        if (webhookUrl.isNullOrBlank() || webhookUrl == "YOUR_WEBHOOK_URL_HERE") {
            plugin.logger.warning("Webhook URL is not configured! Please set it in the config.yml")
            webhookEnabled = false
            return
        }
        
        plugin.logger.info("Webhook functionality initialized")
    }
    
    /**
     * Checks if webhooks are enabled
     * 
     * @return true if webhooks are enabled and initialized, false otherwise
     */
    fun isWebhookEnabled(): Boolean {
        return webhookEnabled && !webhookUrl.isNullOrBlank()
    }
    
    /**
     * Sends a message via webhook with player information
     * 
     * @param player The Minecraft player
     * @param message The message content
     */
    fun sendPlayerMessage(player: Player, message: String) {
        if (!isWebhookEnabled()) return
        
        val playerName = player.name
        val avatarUrl = getPlayerAvatarUrl(playerName)
        
        // Run async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                // Use the webhook URL directly with Discord's webhook API
                val url = webhookUrl ?: return@Runnable
                
                // Create a JSON payload for the webhook
                val jsonPayload = """
                    {
                        "content": "$message",
                        "username": "$playerName",
                        "avatar_url": "$avatarUrl"
                    }
                """.trimIndent()
                
                // Send the webhook request
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                val outputStream = connection.outputStream
                outputStream.write(jsonPayload.toByteArray())
                outputStream.flush()
                outputStream.close()
                
                val responseCode = connection.responseCode
                if (responseCode == 204) {
                    if (config.getBoolean(CONFIG_DEBUG, false)) {
                        plugin.logger.info("Sent webhook message for player $playerName: $message")
                    }
                } else {
                    plugin.logger.warning("Failed to send webhook message: HTTP $responseCode")
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to send webhook message", e)
            }
        })
    }
    
    /**
     * Sends a message via webhook with custom name and avatar
     * 
     * @param name The name to display
     * @param avatarUrl The avatar URL to use
     * @param message The message content
     */
    fun sendCustomMessage(name: String, avatarUrl: String, message: String) {
        if (!isWebhookEnabled()) return
        
        // Run async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                // Use the webhook URL directly with Discord's webhook API
                val url = webhookUrl ?: return@Runnable
                
                // Create a JSON payload for the webhook
                val jsonPayload = """
                    {
                        "content": "$message",
                        "username": "$name",
                        "avatar_url": "$avatarUrl"
                    }
                """.trimIndent()
                
                // Send the webhook request
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                val outputStream = connection.outputStream
                outputStream.write(jsonPayload.toByteArray())
                outputStream.flush()
                outputStream.close()
                
                val responseCode = connection.responseCode
                if (responseCode == 204) {
                    if (config.getBoolean(CONFIG_DEBUG, false)) {
                        plugin.logger.info("Sent webhook message as $name: $message")
                    }
                } else {
                    plugin.logger.warning("Failed to send webhook message: HTTP $responseCode")
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to send webhook message", e)
            }
        })
    }
    
    /**
     * Sends an embed via webhook with the default name and avatar
     * 
     * @param embed The MessageEmbed to send
     */
    fun sendEmbed(embed: MessageEmbed) {
        if (!isWebhookEnabled()) return
        
        val name = config.getString(CONFIG_WEBHOOK_NAME) ?: DEFAULT_WEBHOOK_NAME
        val avatarUrl = config.getString(CONFIG_WEBHOOK_AVATAR_URL)?.replace("{username}", "DaisySRV") 
            ?: DEFAULT_WEBHOOK_AVATAR_URL.replace("{username}", "DaisySRV")
        
        // For embeds, we'll use the JDA's TextChannel since webhooks don't directly support embeds via HTTP
        // Run async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                discordChannel.sendMessageEmbeds(embed).queue(
                    { 
                        if (config.getBoolean(CONFIG_DEBUG, false)) {
                            plugin.logger.info("Sent embed: ${embed.getTitle()}")
                        }
                    },
                    { error -> 
                        plugin.logger.log(Level.WARNING, "Failed to send embed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to send embed", e)
            }
        })
    }
    
    /**
     * Gets the avatar URL for a player
     * 
     * @param playerName The player's name
     * @return The avatar URL with the player's name inserted
     */
    private fun getPlayerAvatarUrl(playerName: String): String {
        val avatarUrlTemplate = config.getString(CONFIG_WEBHOOK_AVATAR_URL) ?: DEFAULT_WEBHOOK_AVATAR_URL
        return avatarUrlTemplate.replace("{username}", playerName)
    }
}