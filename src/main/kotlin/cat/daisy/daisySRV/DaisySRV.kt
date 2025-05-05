package cat.daisy.daisySRV

import cat.daisy.daisySRV.command.DiscordCommandHandler
import cat.daisy.daisySRV.embed.EmbedManager
import cat.daisy.daisySRV.event.MinecraftEventHandler
import cat.daisy.daisySRV.status.BotStatusManager
import cat.daisy.daisySRV.webhook.WebhookManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
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
class DaisySRV : JavaPlugin(), Listener {

    companion object {
        // Configuration paths
        private const val CONFIG_DISCORD_TOKEN = "discord.token"
        private const val CONFIG_DISCORD_CHANNEL_ID = "discord.channel-id"
        private const val CONFIG_FORMAT_DISCORD_TO_MC = "format.discord-to-minecraft"
        private const val CONFIG_DEBUG = "settings.debug"

        // Default values
        private const val DEFAULT_DISCORD_TO_MC_FORMAT = "&b[Discord] &f{username}: &7{message}"
    }

    private var jda: JDA? = null
    private var discordChannel: TextChannel? = null
    private var embedManager: EmbedManager? = null
    private var minecraftEventHandler: MinecraftEventHandler? = null
    private var discordCommandHandler: DiscordCommandHandler? = null
    private var botStatusManager: BotStatusManager? = null
    private var webhookManager: WebhookManager? = null

    override fun onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig()

        // Initialize Discord bot
        initializeDiscordBot()

        // Register command executor
        getCommand("ddiscord")?.setExecutor(this)

        logger.info("DaisySRV has been enabled!")
    }

    override fun onDisable() {
        // Send server stopping message if event handler is initialized
        minecraftEventHandler?.sendServerStoppingMessage()

        // Shutdown Discord bot
        try {
            jda?.shutdownNow() // Ensures immediate termination of JDA resources
            logger.info("JDA connection closed successfully.")
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error while shutting down JDA", e)
        }

        // Clear webhook manager
        webhookManager = null

        logger.info("DaisySRV has been disabled!")
    }

    /**
     * Initializes the Discord bot connection using the token from config
     * Sets up the JDA instance and connects to the specified Discord channel
     */
    private fun initializeDiscordBot() {
        val token = config.getString(CONFIG_DISCORD_TOKEN)
        if (token.isNullOrBlank() || token == "YOUR_BOT_TOKEN_HERE") {
            logger.warning("Discord bot token is not configured! Please set it in the config.yml")
            return
        }

        try {
            // Build JDA instance with optimized settings
            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .enableCache(CacheFlag.MEMBER_OVERRIDES)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .setAutoReconnect(true)
                .build()
                .awaitReady()

            // Get the Discord channel
            val channelId = config.getString(CONFIG_DISCORD_CHANNEL_ID)
            if (channelId.isNullOrBlank() || channelId == "YOUR_CHANNEL_ID_HERE") {
                logger.warning("Discord channel ID is not configured! Please set it in the config.yml")
                return
            }

            discordChannel = jda?.getTextChannelById(channelId)
            if (discordChannel == null) {
                logger.warning("Could not find Discord channel with ID: $channelId")
                return
            }

            // Initialize managers and handlers
            initializeManagers()

            logger.info("Successfully connected to Discord!")
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to initialize Discord bot", e)
        }
    }

    /**
     * Initializes all managers and handlers
     */
    private fun initializeManagers() {
        if (jda == null || discordChannel == null) return

        // Create embed manager
        embedManager = EmbedManager(config)

        // Create webhook manager
        webhookManager = WebhookManager(this, discordChannel!!, config)

        // Create bot status manager
        botStatusManager = BotStatusManager(this, jda!!, config)

        // Create and register Minecraft event handler
        minecraftEventHandler = MinecraftEventHandler(this, discordChannel!!, embedManager!!, webhookManager)
        server.pluginManager.registerEvents(minecraftEventHandler!!, this)

        // Create and register Discord command handler
        discordCommandHandler = DiscordCommandHandler(this, jda!!, embedManager!!)
        jda?.addEventListener(discordCommandHandler)

        // Register Discord message listener
        jda?.addEventListener(DiscordListener(this))

        // Register commands with Discord
        discordCommandHandler?.registerCommands()

        // Update bot status with initial player count
        updateBotStatus(Bukkit.getOnlinePlayers().size, Bukkit.getMaxPlayers())
    }

    /**
     * Sends a message from Discord to Minecraft
     * 
     * @param username The Discord username
     * @param message The message content
     */
    fun sendMessageToMinecraft(username: String, message: String) {
        val format = config.getString(CONFIG_FORMAT_DISCORD_TO_MC) ?: DEFAULT_DISCORD_TO_MC_FORMAT
        val formattedMessage = ChatColor.translateAlternateColorCodes('&', format
            .replace("{username}", username)
            .replace("{message}", message))

        // Run on the main thread since Bukkit API is not thread-safe
        Bukkit.getScheduler().runTask(this, Runnable {
            Bukkit.broadcastMessage(formattedMessage)
            if (config.getBoolean(CONFIG_DEBUG, false)) {
                logger.info("Sent message to Minecraft: $formattedMessage")
            }
        })
    }

    /**
     * Updates the bot's status with the current player count
     * 
     * @param playerCount The current player count
     * @param maxPlayers The maximum player count
     */
    fun updateBotStatus(playerCount: Int, maxPlayers: Int) {
        botStatusManager?.updateStatus(playerCount, maxPlayers)
    }

    /**
     * Handles plugin commands
     * 
     * @param sender The command sender
     * @param command The command being executed
     * @param label The command label used
     * @param args The command arguments
     * @return true if the command was handled, false otherwise
     */
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("ddiscord", ignoreCase = true)) {
            if (!sender.hasPermission("DaisySRV.admin")) {
                sender.sendMessage("${ChatColor.RED}You don't have permission to use this command.")
                return true
            }

            if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
                sender.sendMessage("${ChatColor.YELLOW}Reloading DaisySRV configuration...")

                // Shutdown current JDA instance
                jda?.shutdown()

                // Clear event handlers
                minecraftEventHandler = null
                discordCommandHandler = null
                botStatusManager = null
                embedManager = null
                webhookManager = null

                // Reload config
                reloadConfig()

                // Reinitialize Discord bot
                initializeDiscordBot()

                sender.sendMessage("${ChatColor.GREEN}DaisySRV configuration reloaded!")
                return true
            }

            // Show help message
            sender.sendMessage("${ChatColor.GOLD}DaisySRV Commands:")
            sender.sendMessage("${ChatColor.YELLOW}/ddiscord reload ${ChatColor.GRAY}- Reload the configuration")
            return true
        }
        return false
    }

    /**
     * Inner class for handling Discord messages
     * Listens for messages in the configured channel and forwards them to Minecraft
     */
    private inner class DiscordListener(private val plugin: DaisySRV) : net.dv8tion.jda.api.hooks.ListenerAdapter() {
        /**
         * Handles incoming Discord messages
         * 
         * @param event The message received event
         */
        override fun onMessageReceived(event: net.dv8tion.jda.api.events.message.MessageReceivedEvent) {
            // Ignore messages from bots (including our own)
            if (event.author.isBot) return

            // Check if the message is from the configured channel
            val channelId = plugin.config.getString(CONFIG_DISCORD_CHANNEL_ID)
            if (event.channel.id != channelId) return

            // Get the message content
            val message = event.message.contentDisplay

            // Skip empty messages
            if (message.isBlank()) return

            // Get the author's name (use nickname if available)
            val member = event.member
            val username = member?.effectiveName ?: event.author.name

            // Send the message to Minecraft
            plugin.sendMessageToMinecraft(username, message)
        }
    }
}
