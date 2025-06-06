package cat.daisy.daisySRV

import cat.daisy.daisySRV.command.DiscordCommandHandler
import cat.daisy.daisySRV.embed.EmbedManager
import cat.daisy.daisySRV.event.MinecraftConsoleHandler
import cat.daisy.daisySRV.event.MinecraftEventHandler
import cat.daisy.daisySRV.status.BotStatusManager
import cat.daisy.daisySRV.webhook.WebhookManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * DaisySRV - A Discord-Minecraft chat bridge plugin with enhanced features
 *
 * This plugin connects your Minecraft server chat with a Discord channel,
 * allowing messages to be sent between both platforms seamlessly.
 *
 * Features:
 * - Discord to Minecraft chat bridge
 * - Minecraft to Discord chat bridge
 * - Rich embeds for player join/leave, achievements, and server status
 * - Discord slash commands for player list
 * - Bot status showing player count
 */
class DaisySRV :
    JavaPlugin(),
    Listener {
    companion object {
        // Configuration paths
        private const val CONFIG_DISCORD_TOKEN = "discord.token"
        private const val CONFIG_DISCORD_CHANNEL_ID = "discord.channel-id"
        private const val CONFIG_FORMAT_DISCORD_TO_MC = "format.discord-to-minecraft"
        private const val CONFIG_DEBUG = "settings.debug"

        // Default values
        private const val DEFAULT_DISCORD_TO_MC_FORMAT = "&b[Discord] &f{username}: &7{message}"

        // Connection check intervals (in ticks, 20 ticks = 1 second)
        private const val CONNECTION_CHECK_INTERVAL = 6000 // 5 minutes
    }

    var jda: JDA? = null
    private var discordChannel: TextChannel? = null
    private var embedManager: EmbedManager? = null
    private var minecraftEventHandler: MinecraftEventHandler? = null
    private var discordCommandHandler: DiscordCommandHandler? = null
    private var botStatusManager: BotStatusManager? = null
    private var minecraftConsoleHandler: MinecraftConsoleHandler? = null

    // Changed from private to internal for access from WebhookManager
    internal var webhookManager: WebhookManager? = null

    // Connection and shutdown management
    var isShuttingDown = false

    // Changed from private to internal for access from WebhookManager
    internal val messageQueue = MessageQueue()
    private var connectionCheckTask: Int = -1

    override fun onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig()

        // Initialize plugin
        logger.info("Initializing DaisySRV ${description.version}")

        // Setup Discord connection
        setupDiscordConnection()

        // Schedule connection check task
        scheduleConnectionCheck()

        // Register Bukkit events
        server.pluginManager.registerEvents(this, this)

        logger.info("DaisySRV enabled successfully!")
    }

    override fun onDisable() {
        isShuttingDown = true

        // Send server stopping message if configured
        minecraftEventHandler?.sendServerStoppingMessage()

        // Unregister slash commands
        discordCommandHandler?.unregisterCommands()

        // Shutdown console handler
        minecraftConsoleHandler?.shutdown()

        // Shutdown webhook manager to properly close HTTP resources
        webhookManager?.shutdown()

        // Shutdown JDA gracefully
        if (jda != null) {
            try {
                logger.info("Shutting down Discord connection...")
                jda?.shutdown()
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error shutting down JDA", e)
            }
        }

        // Cancel scheduled tasks
        if (connectionCheckTask != -1) {
            Bukkit.getScheduler().cancelTask(connectionCheckTask)
        }

        logger.info("DaisySRV disabled successfully!")
    }

    /**
     * Sets up the Discord connection using JDA
     */
    private fun setupDiscordConnection() {
        val token = config.getString(CONFIG_DISCORD_TOKEN)
        if (token.isNullOrBlank() || token == "YOUR_BOT_TOKEN_HERE") {
            logger.warning("Discord token not configured! Please set it in config.yml")
            return
        }

        val channelId = config.getString(CONFIG_DISCORD_CHANNEL_ID)
        if (channelId.isNullOrBlank() || channelId == "YOUR_CHANNEL_ID_HERE") {
            logger.warning("Discord channel ID not configured! Please set it in config.yml")
            return
        }

        try {
            // Build JDA instance with necessary intents
            jda =
                JDABuilder
                    .createDefault(token)
                    .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                    ).disableCache(
                        CacheFlag.VOICE_STATE,
                        CacheFlag.EMOJI,
                        CacheFlag.STICKER,
                        CacheFlag.SCHEDULED_EVENTS,
                    ).build()
                    .awaitReady()

            // Get the Discord channel
            val channel = jda?.getTextChannelById(channelId)
            if (channel == null) {
                logger.warning("Could not find Discord channel with ID: $channelId")
                return
            }
            discordChannel = channel
            logger.info("Connected to Discord channel: ${channel.name}")

            // Initialize components
            initializeComponents()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to connect to Discord", e)
        }
    }

    /**
     * Restarts the Discord session
     * Added to fix the unresolved reference error
     */
    private fun restartSession() {
        logger.info("Restarting Discord session...")
        jda?.shutdown()
        setupDiscordConnection()
    }

    /**
     * Initialize all components after Discord connection is established
     */
    private fun initializeComponents() {
        val channel = discordChannel ?: return

        // Initialize embed manager
        embedManager = EmbedManager(config)

        // Initialize webhook manager if enabled
        webhookManager = WebhookManager(this, channel, config)

        // Initialize bot status manager
        jda?.let { botStatusManager = BotStatusManager(this, it, config) }

        // Initialize Minecraft event handler
        minecraftEventHandler = MinecraftEventHandler(this, channel, embedManager!!, webhookManager)
        server.pluginManager.registerEvents(minecraftEventHandler!!, this)

        minecraftConsoleHandler = MinecraftConsoleHandler(this, jda!!, channel)

        // Initialize Discord command handler
        discordCommandHandler = DiscordCommandHandler(this, jda!!, embedManager!!)
        discordCommandHandler?.registerCommands()

        // Set initial bot status
        updateBotStatus(Bukkit.getOnlinePlayers().size, Bukkit.getMaxPlayers())

        // Setup Discord message listener
        setupDiscordMessageListener()
    }

    /**
     * Setup the Discord message listener
     */
    private fun setupDiscordMessageListener() {
        // Create a listener for Discord messages
        class DiscordMessageListener : ListenerAdapter() {
            override fun onMessageReceived(event: net.dv8tion.jda.api.events.message.MessageReceivedEvent) {
                // Ignore own messages to prevent loops
                if (event.author.isBot) return

                // Check if message is in the configured channel
                if (event.channel.id != discordChannel?.id) return

                // Get message content
                val content = event.message.contentDisplay

                // Forward message to Minecraft
                val format = config.getString(CONFIG_FORMAT_DISCORD_TO_MC) ?: DEFAULT_DISCORD_TO_MC_FORMAT
                val formattedMessage =
                    ChatColor.translateAlternateColorCodes(
                        '&',
                        format
                            .replace("{username}", event.member?.effectiveName ?: event.author.name)
                            .replace("{message}", content),
                    )

                // Broadcast to all players
                Bukkit.broadcastMessage(formattedMessage)

                if (config.getBoolean(CONFIG_DEBUG, false)) {
                    logger.info("Discord -> MC: ${event.author.name}: $content")
                }
            }

            // Removed 'override' since these don't override any parent methods
            fun onDisconnect(event: net.dv8tion.jda.api.events.session.SessionDisconnectEvent) {
                logger.warning("Disconnected from Discord: ${event.closeCode}")
            }

            // Removed 'override' since these don't override any parent methods
            fun onReconnect(event: net.dv8tion.jda.api.events.session.SessionRecreateEvent) {
                logger.info("Reconnected to Discord")
            }
        }

        // Register the listener
        jda?.addEventListener(DiscordMessageListener())
    }

    /**
     * Schedules a periodic connection check to ensure Discord connection is active
     */
    private fun scheduleConnectionCheck() {
        connectionCheckTask =
            Bukkit.getScheduler().scheduleSyncRepeatingTask(
                this,
                {
                    val jdaInstance = jda
                    if (jdaInstance == null || jdaInstance.status != JDA.Status.CONNECTED) {
                        logger.warning("Discord connection is not active (status: ${jdaInstance?.status}). Attempting to reconnect...")
                        restartSession()
                    } else if (config.getBoolean(CONFIG_DEBUG, false)) {
                        logger.info("Discord connection check: OK (status: ${jdaInstance.status})")
                    }
                },
                CONNECTION_CHECK_INTERVAL.toLong(),
                CONNECTION_CHECK_INTERVAL.toLong(),
            )
    }

    /**
     * Updates the bot status with player count information
     *
     * @param playerCount The current number of online players
     * @param maxPlayers The maximum player capacity
     */
    fun updateBotStatus(
        playerCount: Int,
        maxPlayers: Int,
    ) {
        botStatusManager?.updateStatus(playerCount, maxPlayers)
    }

    /**
     * Handles plugin commands
     */
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (command.name.equals("ddiscord", ignoreCase = true)) {
            if (args.isEmpty()) {
                sender.sendMessage("${ChatColor.AQUA}[DaisySRV] ${ChatColor.GRAY}Version ${description.version}")
                sender.sendMessage("${ChatColor.AQUA}[DaisySRV] ${ChatColor.GRAY}/ddiscord reload - Reload configuration")
                sender.sendMessage("${ChatColor.AQUA}[DaisySRV] ${ChatColor.GRAY}/ddiscord status - Check Discord connection status")
                return true
            }

            when (args[0].lowercase()) {
                "reload" -> {
                    if (!sender.hasPermission("daisysrv.reload")) {
                        sender.sendMessage("${ChatColor.RED}You don't have permission to use this command")
                        return true
                    }

                    // Reload config
                    reloadConfig()
                    sender.sendMessage("${ChatColor.AQUA}[DaisySRV] ${ChatColor.GREEN}Configuration reloaded!")

                    // Restart components
                    isShuttingDown = true
                    jda?.shutdown()
                    isShuttingDown = false
                    setupDiscordConnection()

                    return true
                }
                "status" -> {
                    if (!sender.hasPermission("daisysrv.status")) {
                        sender.sendMessage("${ChatColor.RED}You don't have permission to use this command")
                        return true
                    }

                    // Check connection status
                    val jdaInstance = jda
                    if (jdaInstance == null) {
                        sender.sendMessage("${ChatColor.AQUA}[DaisySRV] ${ChatColor.RED}Discord connection: Not connected")
                    } else {
                        val status = jdaInstance.status
                        val statusColor =
                            when (status) {
                                JDA.Status.CONNECTED -> ChatColor.GREEN
                                JDA.Status.ATTEMPTING_TO_RECONNECT -> ChatColor.YELLOW
                                else -> ChatColor.RED
                            }
                        sender.sendMessage("${ChatColor.AQUA}[DaisySRV] ${ChatColor.GRAY}Discord connection: $statusColor$status")
                        sender.sendMessage(
                            "${ChatColor.AQUA}[DaisySRV] ${ChatColor.GRAY}Channel: ${discordChannel?.name ?: "Not connected"}",
                        )
                        sender.sendMessage(
                            "${ChatColor.AQUA}[DaisySRV] ${ChatColor.GRAY}Webhook enabled: ${webhookManager?.isWebhookEnabled() ?: false}",
                        )
                    }
                    return true
                }
                else -> {
                    sender.sendMessage("${ChatColor.AQUA}[DaisySRV] ${ChatColor.RED}Unknown command. Use /ddiscord for help")
                    return true
                }
            }
        }
        return false
    }

    /**
     * Message queue for handling async messages to Discord
     */
    inner class MessageQueue {
        private val pendingMessages = ConcurrentLinkedQueue<() -> Unit>()
        private val isProcessing = AtomicBoolean(false)

        fun enqueue(message: () -> Unit) {
            pendingMessages.add(message)
            processQueue()
        }

        private fun processQueue() {
            if (isProcessing.compareAndSet(false, true)) {
                // Fix for runTaskAsynchronously ambiguity - explicitly use Runnable version
                Bukkit.getScheduler().runTaskAsynchronously(
                    this@DaisySRV,
                    Runnable {
                        try {
                            while (pendingMessages.isNotEmpty()) {
                                if (isShuttingDown) {
                                    logger.info("Plugin shutting down, clearing message queue with ${pendingMessages.size} messages")
                                    pendingMessages.clear()
                                    break
                                }

                                try {
                                    pendingMessages.poll()?.invoke()
                                } catch (e: Exception) {
                                    logger.log(Level.WARNING, "Error processing message in queue", e)
                                }

                                // Small delay to respect rate limits
                                Thread.sleep(100)
                            }
                        } catch (e: Exception) {
                            logger.log(Level.WARNING, "Error in message queue processor", e)
                        } finally {
                            isProcessing.set(false)
                            // Process any messages that were added while we were processing
                            if (pendingMessages.isNotEmpty()) {
                                processQueue()
                            }
                        }
                    },
                )
            }
        }
    }
}
