package cat.daisy.daisySRV.webhook

import cat.daisy.daisySRV.DaisySRV
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Manages Discord webhooks for sending messages with player avatars and names
 */
class WebhookManager(
    private val plugin: DaisySRV,
    private val discordChannel: TextChannel,
    private val config: FileConfiguration,
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

        // HTTP Status Codes
        private const val HTTP_OK = 200
        private const val HTTP_NO_CONTENT = 204
        private const val HTTP_RATE_LIMITED = 429
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_BAD_REQUEST = 400
    }

    private var webhookUrl: String? = null
    private var webhookEnabled = false
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

    // Track consecutive failures for backoff
    private var consecutiveFailures = 0
    private var lastFailureTime = 0L

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

        // Validate webhook URL format
        if (!isValidWebhookUrl(webhookUrl)) {
            plugin.logger.warning("Invalid webhook URL format! Please check your config.yml")
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
    fun isWebhookEnabled(): Boolean = webhookEnabled && !webhookUrl.isNullOrBlank() && !isShuttingDown()

    /**
     * Properly shuts down the WebhookManager and closes HTTP resources
     * This prevents "zip file closed" errors during plugin shutdown
     */
    fun shutdown() {
        webhookEnabled = false

        // Cancel any pending requests
        httpClient.dispatcher.cancelAll()

        // Close the HTTP client to release resources
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
        try {
            httpClient.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            // Just log and continue with shutdown
            plugin.logger.warning("Interrupted while shutting down webhook client")
        }

        plugin.logger.info("WebhookManager shutdown completed")
    }

    /**
     * Validates webhook URL format
     */
    private fun isValidWebhookUrl(url: String?): Boolean {
        if (url == null) return false

        return try {
            val parsedUrl = URL(url)
            val host = parsedUrl.host.lowercase()
            val path = parsedUrl.path

            // Basic validation for Discord webhook URL format
            host.contains("discord.com") && path.contains("/api/webhooks/")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the plugin is shutting down
     */
    private fun isShuttingDown(): Boolean = (plugin as? DaisySRV)?.isShuttingDown == true

    /**
     * Sanitizes a message for Discord - removes mentions and other problematic content
     */
    private fun sanitizeMessage(message: String): String =
        message
            .replace("@everyone", "@\u200Beveryone")
            .replace("@here", "@\u200Bhere")
            .replace(Regex("@([\\w-]+)"), "@\u200B$1")

    /**
     * Sends a message via webhook with player information
     *
     * @param player The Minecraft player
     * @param message The message content
     */
    fun sendPlayerMessage(
        player: Player,
        message: String,
    ) {
        if (!isWebhookEnabled()) return

        val sanitizedMessage = sanitizeMessage(message)
        val playerName = player.name
        val avatarUrl = getPlayerAvatarUrl(playerName)

        // Queue the message for async processing
        (plugin as? DaisySRV)?.messageQueue?.enqueue {
            sendWebhookRequest(playerName, sanitizedMessage, avatarUrl)
        } ?: run {
            // Fallback if message queue is unavailable
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                Runnable {
                    sendWebhookRequest(playerName, sanitizedMessage, avatarUrl)
                },
            )
        }
    }

    /**
     * Sends a system message via webhook
     *
     * @param message The message content
     * @param useEmbeds Whether to use embeds for the message
     * @param embed Optional embed to send
     */
    fun sendSystemMessage(
        message: String,
        useEmbeds: Boolean = false,
        embed: MessageEmbed? = null,
    ) {
        if (!isWebhookEnabled()) return

        val webhookName = config.getString(CONFIG_WEBHOOK_NAME) ?: DEFAULT_WEBHOOK_NAME
        val avatarUrl = getSystemAvatarUrl()

        // Queue the message for async processing
        (plugin as? DaisySRV)?.messageQueue?.enqueue {
            if (useEmbeds && embed != null) {
                sendWebhookEmbed(webhookName, embed, avatarUrl)
            } else {
                sendWebhookRequest(webhookName, sanitizeMessage(message), avatarUrl)
            }
        } ?: run {
            // Fallback if message queue is unavailable
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                Runnable {
                    if (useEmbeds && embed != null) {
                        sendWebhookEmbed(webhookName, embed, avatarUrl)
                    } else {
                        sendWebhookRequest(webhookName, sanitizeMessage(message), avatarUrl)
                    }
                },
            )
        }
    }

    /**
     * Gets the avatar URL for a player
     *
     * @param playerName The player's name
     * @return The avatar URL
     */
    private fun getPlayerAvatarUrl(playerName: String): String {
        val avatarUrlTemplate = config.getString(CONFIG_WEBHOOK_AVATAR_URL) ?: DEFAULT_WEBHOOK_AVATAR_URL
        return avatarUrlTemplate.replace("{username}", playerName)
    }

    /**
     * Gets the avatar URL for system messages
     *
     * @return The system avatar URL
     */
    private fun getSystemAvatarUrl(): String {
        val webhookName = config.getString(CONFIG_WEBHOOK_NAME) ?: DEFAULT_WEBHOOK_NAME
        return "https://eu.mc-api.net/v3/server/favicon/${Bukkit.getServer().ip ?: "localhost"}"
    }

    /**
     * Sends a webhook request with a message
     */
    private fun sendWebhookRequest(
        username: String,
        message: String,
        avatarUrl: String,
    ) {
        try {
            // Check for backoff due to consecutive failures
            if (shouldBackoff()) {
                plugin.logger.warning("Backing off webhook requests due to consecutive failures")
                return
            }

            val url = webhookUrl ?: return
            val jsonPayload =
                """
                {
                    "username": "$username",
                    "avatar_url": "$avatarUrl",
                    "content": "$message"
                }
                """.trimIndent()

            val request = createWebhookRequest(url, jsonPayload)
            executeWebhookRequest(request)

            // Reset failures on success
            consecutiveFailures = 0
        } catch (e: Exception) {
            handleWebhookException(e)
        }
    }

    /**
     * Sends a webhook request with an embed
     */
    private fun sendWebhookEmbed(
        username: String,
        embed: MessageEmbed,
        avatarUrl: String,
    ) {
        try {
            // Check for backoff due to consecutive failures
            if (shouldBackoff()) {
                plugin.logger.warning("Backing off webhook requests due to consecutive failures")
                return
            }

            val url = webhookUrl ?: return

            // Convert embed to JSON format
            val embedJson = convertEmbedToJson(embed)

            val jsonPayload =
                """
                {
                    "username": "$username",
                    "avatar_url": "$avatarUrl",
                    "embeds": [$embedJson]
                }
                """.trimIndent()

            val request = createWebhookRequest(url, jsonPayload)
            executeWebhookRequest(request)

            // Reset failures on success
            consecutiveFailures = 0
        } catch (e: Exception) {
            handleWebhookException(e)
        }
    }

    /**
     * Creates a webhook request with the given payload
     */
    private fun createWebhookRequest(
        url: String,
        jsonPayload: String,
    ): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toRequestBody(mediaType)

        return Request
            .Builder()
            .url(url)
            .post(body)
            .build()
    }

    /**
     * Executes a webhook request and handles the response
     */
    private fun executeWebhookRequest(request: Request) {
        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                HTTP_OK, HTTP_NO_CONTENT -> {
                    if (config.getBoolean(CONFIG_DEBUG, false)) {
                        plugin.logger.info("Webhook message sent successfully")
                    }
                }
                HTTP_RATE_LIMITED -> {
                    val retryAfter = response.headers["Retry-After"]?.toIntOrNull() ?: 5
                    plugin.logger.warning("Webhook rate limited! Retry after $retryAfter seconds")
                    consecutiveFailures++
                    lastFailureTime = System.currentTimeMillis()
                }
                HTTP_NOT_FOUND -> {
                    plugin.logger.warning("Webhook not found (404)! Check if the webhook URL is still valid")
                    webhookEnabled = false
                }
                HTTP_BAD_REQUEST -> {
                    plugin.logger.warning("Bad webhook request (400)! Response: ${response.body?.string()}")
                    consecutiveFailures++
                    lastFailureTime = System.currentTimeMillis()
                }
                else -> {
                    plugin.logger.warning("Webhook error ${response.code}: ${response.body?.string()}")
                    consecutiveFailures++
                    lastFailureTime = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * Checks if requests should back off due to consecutive failures
     */
    private fun shouldBackoff(): Boolean {
        if (consecutiveFailures == 0) return false

        // Exponential backoff: 2^failures seconds (max 5 minutes)
        val backoffSeconds = minOf(Math.pow(2.0, consecutiveFailures.toDouble()).toLong(), 300)
        val backoffMs = backoffSeconds * 1000
        val elapsedSinceLastFailure = System.currentTimeMillis() - lastFailureTime

        return elapsedSinceLastFailure < backoffMs
    }

    /**
     * Handles exceptions from webhook requests
     */
    private fun handleWebhookException(e: Exception) {
        when (e) {
            is IOException -> plugin.logger.warning("Network error sending webhook: ${e.message}")
            else -> plugin.logger.log(Level.WARNING, "Error sending webhook message", e)
        }

        consecutiveFailures++
        lastFailureTime = System.currentTimeMillis()
    }

    /**
     * Converts a MessageEmbed to JSON format for webhook
     */
    private fun convertEmbedToJson(embed: MessageEmbed): String {
        val fields =
            embed.fields.joinToString(",") { field ->
                """
                {
                    "name": "${field.name}",
                    "value": "${field.value}",
                    "inline": ${field.isInline}
                }
                """.trimIndent()
            }

        val author =
            embed.author?.let {
                """
                "author": {
                    "name": "${it.name}",
                    "url": "${it.url ?: ""}",
                    "icon_url": "${it.iconUrl ?: ""}"
                },
                """.trimIndent()
            } ?: ""

        val footer =
            embed.footer?.let {
                """
                "footer": {
                    "text": "${it.text}",
                    "icon_url": "${it.iconUrl ?: ""}"
                },
                """.trimIndent()
            } ?: ""

        val timestamp = embed.timestamp?.let { "\"timestamp\": \"${it}\"," } ?: ""

        return """
            {
                "title": "${embed.title ?: ""}",
                "description": "${embed.description ?: ""}",
                "color": ${embed.colorRaw},
                $author
                $footer
                $timestamp
                "fields": [$fields]
            }
            """.trimIndent()
    }
}
