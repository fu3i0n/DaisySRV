package cat.daisy.daisySRV.event

import cat.daisy.daisySRV.DaisySRV
import cat.daisy.daisySRV.embed.EmbedManager
import cat.daisy.daisySRV.webhook.WebhookManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.RateLimitedException
import org.bukkit.Bukkit
import org.bukkit.advancement.Advancement
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.ServerLoadEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import kotlin.toString

/**
 * Handles Minecraft events and forwards them to Discord
 */
class MinecraftEventHandler(
    private val plugin: DaisySRV,
    private val discordChannel: TextChannel,
    private val embedManager: EmbedManager,
    private val webhookManager: WebhookManager? = null,
) : Listener {
    private val sentAdvancements = ConcurrentHashMap<String, MutableSet<String>>()

    companion object {
        // Configuration paths
        private const val CONFIG_EVENTS_PLAYER_JOIN = "events.player-join"
        private const val CONFIG_EVENTS_PLAYER_QUIT = "events.player-quit"
        private const val CONFIG_EVENTS_PLAYER_ADVANCEMENT = "events.player-advancement"
        private const val CONFIG_EVENTS_SERVER_START = "events.server-start"
        private const val CONFIG_EVENTS_SERVER_STOP = "events.server-stop"
        private const val CONFIG_FORMAT_MC_TO_DISCORD = "format.minecraft-to-discord"
        private const val CONFIG_DEBUG = "settings.debug"

        // Default values
        private const val DEFAULT_MC_TO_DISCORD_FORMAT = "**{username}**: {message}"
    }

    /**
     * Checks if the JDA instance is active and connected
     *
     * @return true if JDA is connected and ready, false otherwise
     */
    private fun isJdaActive(): Boolean {
        if ((plugin as? DaisySRV)?.isShuttingDown == true) {
            plugin.logger.info("Plugin is shutting down, skipping Discord message")
            return false
        }

        val status = discordChannel.jda.status
        if (status != JDA.Status.CONNECTED) {
            plugin.logger.warning("Discord JDA is not CONNECTED (status: $status)")
            return false
        }
        return true
    }

    /**
     * Sanitizes a message to prevent Discord mentions
     */
    private fun sanitizeDiscordMessage(message: String): String =
        message
            .replace("@everyone", "@\u200Beveryone")
            .replace("@here", "@\u200Bhere")
            .replace(Regex("@([\\w-]+)"), "@\u200B$1")

    /**
     * Handles player chat events and forwards them to Discord
     *
     * @param event The AsyncPlayerChatEvent containing the player and message
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val message = sanitizeDiscordMessage(event.message)

        // If webhook is enabled and initialized, use it to send the message with player avatar
        if (webhookManager != null && webhookManager.isWebhookEnabled()) {
            webhookManager.sendPlayerMessage(player, message)
        } else {
            // Fall back to regular Discord message
            sendMessageToDiscord(player.name, message)
        }
    }

    /**
     * Handles player join events and sends an embed to Discord
     *
     * @param event The PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Check if player join events are enabled
        if (!plugin.config.getBoolean(CONFIG_EVENTS_PLAYER_JOIN, true)) return

        val player = event.player

        // Update player count in bot status
        updatePlayerCount()

        // Send embed to Discord
        if (embedManager.areEmbedsEnabled()) {
            val embed = embedManager.createPlayerJoinEmbed(player.name)
            sendEmbedToDiscord(embed)
        } else {
            // Fallback to text message
            sendSystemMessageToDiscord("${player.name} joined the server")
        }
    }

    /**
     * Handles player quit events and sends an embed to Discord
     *
     * @param event The PlayerQuitEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Check if player quit events are enabled
        if (!plugin.config.getBoolean(CONFIG_EVENTS_PLAYER_QUIT, true)) return

        val player = event.player

        // Update player count in bot status (after a short delay to ensure the player is removed)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { updatePlayerCount() }, 5L)

        // Send embed to Discord
        if (embedManager.areEmbedsEnabled()) {
            val embed = embedManager.createPlayerLeaveEmbed(player.name)
            sendEmbedToDiscord(embed)
        } else {
            // Fallback to text message
            sendSystemMessageToDiscord("${player.name} left the server")
        }
    }

    /**
     * Handles player advancement events and sends an embed to Discord
     *
     * @param event The PlayerAdvancementDoneEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerAdvancement(event: PlayerAdvancementDoneEvent) {
        if (!plugin.config.getBoolean(CONFIG_EVENTS_PLAYER_ADVANCEMENT, true)) return

        val player = event.player
        val advancement = event.advancement

        if (isRecipeAdvancement(advancement) || !hasDisplay(advancement)) return

        val advancementKey = advancement.key.key

        // Check if the advancement has already been sent
        val playerAdvancements = sentAdvancements.computeIfAbsent(player.uniqueId.toString()) { mutableSetOf() }
        if (playerAdvancements.contains(advancementKey)) return

        // Mark the advancement as sent
        playerAdvancements.add(advancementKey)

        val advancementName = getAdvancementName(advancement)
        val advancementDescription = getAdvancementDescription(advancement)

        if (embedManager.areEmbedsEnabled()) {
            val embed = embedManager.createAchievementEmbed(player.name, advancementName, advancementDescription)
            sendEmbedToDiscord(embed)
        } else {
            sendSystemMessageToDiscord("${player.name} earned the achievement $advancementName")
        }
    }

    /**
     * Handles server load events and sends an embed to Discord
     *
     * @param event The ServerLoadEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onServerLoad(event: ServerLoadEvent) {
        // Check if server start events are enabled
        if (!plugin.config.getBoolean(CONFIG_EVENTS_SERVER_START, true)) return

        // Wait a bit to ensure the server is fully started
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                // Update player count in bot status
                updatePlayerCount()

                // Send embed to Discord
                if (embedManager.areEmbedsEnabled()) {
                    val playerCount = Bukkit.getOnlinePlayers().size
                    val maxPlayers = Bukkit.getMaxPlayers()
                    val embed = embedManager.createServerStatusEmbed(true, playerCount, maxPlayers)
                    sendEmbedToDiscord(embed)
                } else {
                    // Fallback to text message
                    sendSystemMessageToDiscord("Server is now online")
                }
            },
            40L,
        ) // Wait 2 seconds (40 ticks)
    }

    /**
     * Sends a server stopping message when the plugin is disabled
     * This is called from the main plugin class onDisable method
     */
    fun sendServerStoppingMessage() {
        // Check if server stop events are enabled
        if (!plugin.config.getBoolean(CONFIG_EVENTS_SERVER_STOP, true)) return

        // Send embed to Discord
        if (embedManager.areEmbedsEnabled()) {
            val embed = embedManager.createServerStatusEmbed(false)
            sendEmbedToDiscordSync(embed)
        } else {
            // Fallback to text message
            sendSystemMessageToDiscordSync("Server is now offline")
        }
    }

    /**
     * Sends a message from Minecraft to Discord
     *
     * @param username The Minecraft username
     * @param message The message content
     */
    fun sendMessageToDiscord(
        username: String,
        message: String,
    ) {
        if (!isJdaActive()) return

        val sanitizedMessage = sanitizeDiscordMessage(message)

        val format = plugin.config.getString(CONFIG_FORMAT_MC_TO_DISCORD) ?: DEFAULT_MC_TO_DISCORD_FORMAT
        val formattedMessage =
            format
                .replace("{username}", username)
                .replace("{message}", sanitizedMessage)

        // Run async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable {
                try {
                    discordChannel.sendMessage(formattedMessage).queue(
                        {
                            if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                                plugin.logger.info("Sent message to Discord: $formattedMessage")
                            }
                        },
                        { error ->
                            when (error) {
                                is RateLimitedException ->
                                    plugin.logger.warning("Rate limited by Discord: ${error.message}")
                                is ErrorResponseException ->
                                    plugin.logger.warning("Discord API error (${error.errorCode}): ${error.meaning}")
                                else ->
                                    plugin.logger.log(Level.WARNING, "Failed to send message to Discord: ${error.message}")
                            }
                        },
                    )
                } catch (e: Exception) {
                    plugin.logger.log(Level.WARNING, "Failed to send message to Discord", e)
                }
            },
        )
    }

    /**
     * Sends a system message to Discord
     *
     * @param message The message content
     */
    private fun sendSystemMessageToDiscord(message: String) {
        if (!isJdaActive()) return

        // Run async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable {
                try {
                    discordChannel.sendMessage(message).queue(
                        {
                            if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                                plugin.logger.info("Sent system message to Discord: $message")
                            }
                        },
                        { error ->
                            when (error) {
                                is RateLimitedException ->
                                    plugin.logger.warning("Rate limited by Discord: ${error.message}")
                                is ErrorResponseException ->
                                    plugin.logger.warning("Discord API error (${error.errorCode}): ${error.meaning}")
                                else ->
                                    plugin.logger.log(Level.WARNING, "Failed to send system message to Discord: ${error.message}")
                            }
                        },
                    )
                } catch (e: Exception) {
                    plugin.logger.log(Level.WARNING, "Failed to send system message to Discord", e)
                }
            },
        )
    }

    /**
     * Sends an embed to Discord
     *
     * @param embed The MessageEmbed to send
     */
    private fun sendEmbedToDiscord(embed: net.dv8tion.jda.api.entities.MessageEmbed) {
        if (!isJdaActive()) return

        // Run async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable {
                try {
                    discordChannel.sendMessageEmbeds(embed).queue(
                        {
                            if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                                plugin.logger.info("Sent embed to Discord: ${embed.title}")
                            }
                        },
                        { error ->
                            when (error) {
                                is RateLimitedException ->
                                    plugin.logger.warning("Rate limited by Discord: ${error.message}")
                                is ErrorResponseException ->
                                    plugin.logger.warning("Discord API error (${error.errorCode}): ${error.meaning}")
                                else ->
                                    plugin.logger.log(Level.WARNING, "Failed to send embed to Discord: ${error.message}")
                            }
                        },
                    )
                } catch (e: Exception) {
                    plugin.logger.log(Level.WARNING, "Failed to send embed to Discord", e)
                }
            },
        )
    }

    /**
     * Sends an embed to Discord synchronously
     * This method is used during server shutdown when tasks cannot be scheduled
     *
     * @param embed The MessageEmbed to send
     */
    private fun sendEmbedToDiscordSync(embed: net.dv8tion.jda.api.entities.MessageEmbed) {
        // No JDA check here as we're about to shut down anyway
        try {
            if (discordChannel.jda.status != JDA.Status.CONNECTED) {
                plugin.logger.warning("Discord JDA is not CONNECTED, cannot send shutdown embed")
                return
            }

            discordChannel.sendMessageEmbeds(embed).complete()
            if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Sent embed to Discord synchronously: ${embed.getTitle()}")
            }
        } catch (e: RateLimitedException) {
            plugin.logger.warning("Rate limited while sending shutdown message: ${e.message}")
        } catch (e: ErrorResponseException) {
            plugin.logger.warning("Discord API error while sending shutdown message (${e.errorCode}): ${e.meaning}")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to send embed to Discord synchronously", e)
        }
    }

    /**
     * Sends a system message to Discord synchronously
     * This method is used during server shutdown when tasks cannot be scheduled
     *
     * @param message The message content
     */
    private fun sendSystemMessageToDiscordSync(message: String) {
        // No JDA check here as we're about to shut down anyway
        try {
            if (discordChannel.jda.status != JDA.Status.CONNECTED) {
                plugin.logger.warning("Discord JDA is not CONNECTED, cannot send shutdown message")
                return
            }

            discordChannel.sendMessage(message).complete()
            if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Sent system message to Discord synchronously: $message")
            }
        } catch (e: RateLimitedException) {
            plugin.logger.warning("Rate limited while sending shutdown message: ${e.message}")
        } catch (e: ErrorResponseException) {
            plugin.logger.warning("Discord API error while sending shutdown message (${e.errorCode}): ${e.meaning}")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to send system message to Discord synchronously", e)
        }
    }

    /**
     * Updates the player count in the bot status
     */
    private fun updatePlayerCount() {
        val playerCount = Bukkit.getOnlinePlayers().size
        val maxPlayers = Bukkit.getMaxPlayers()

        if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
            plugin.logger.info("Player count updated: $playerCount/$maxPlayers")
        }

        // Update the bot status
        (plugin as? DaisySRV)?.updateBotStatus(playerCount, maxPlayers)
    }

    /**
     * Checks if an advancement is a recipe
     *
     * @param advancement The advancement to check
     * @return true if it's a recipe, false otherwise
     */
    private fun isRecipeAdvancement(advancement: Advancement): Boolean = advancement.key.key.startsWith("recipes/")

    /**
     * Checks if an advancement has a display
     *
     * @param advancement The advancement to check
     * @return true if it has a display, false otherwise
     */
    private fun hasDisplay(advancement: Advancement): Boolean {
        // Use reflection to check if the advancement has a display
        try {
            val advancementHandle = advancement.javaClass.getDeclaredField("handle")
            advancementHandle.isAccessible = true
            val handle = advancementHandle.get(advancement)

            val displayField = handle.javaClass.getDeclaredField("c")
            displayField.isAccessible = true
            val display = displayField.get(handle)

            return display != null
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Gets the name of an advancement
     *
     * @param advancement The advancement
     * @return The advancement name or key if not found
     */
    private fun getAdvancementName(advancement: Advancement): String {
        // Try to get the display name, fall back to the key
        try {
            val advancementHandle = advancement.javaClass.getDeclaredField("handle")
            advancementHandle.isAccessible = true
            val handle = advancementHandle.get(advancement)

            val displayField = handle.javaClass.getDeclaredField("c")
            displayField.isAccessible = true
            val display = displayField.get(handle)

            if (display != null) {
                val titleField = display.javaClass.getDeclaredField("a")
                titleField.isAccessible = true
                val title = titleField.get(display)

                // Get the IChatBaseComponent text
                val textMethod = title.javaClass.getMethod("getString")
                return textMethod.invoke(title) as String
            }
        } catch (e: Exception) {
            // Ignore and use fallback
        }

        // Fallback to key
        return advancement.key.key
            .split("/")
            .last()
            .replace("_", " ")
            .capitalize()
    }

    /**
     * Gets the description of an advancement
     *
     * @param advancement The advancement
     * @return The advancement description or empty string if not found
     */
    private fun getAdvancementDescription(advancement: Advancement): String {
        // Try to get the description, fall back to empty string
        try {
            val advancementHandle = advancement.javaClass.getDeclaredField("handle")
            advancementHandle.isAccessible = true
            val handle = advancementHandle.get(advancement)

            val displayField = handle.javaClass.getDeclaredField("c")
            displayField.isAccessible = true
            val display = displayField.get(handle)

            if (display != null) {
                val descriptionField = display.javaClass.getDeclaredField("b")
                descriptionField.isAccessible = true
                val description = descriptionField.get(display)

                // Get the IChatBaseComponent text
                val textMethod = description.javaClass.getMethod("getString")
                return textMethod.invoke(description) as String
            }
        } catch (e: Exception) {
            // Ignore and use fallback
        }

        return ""
    }
}
