package cat.daisy.daisySRV.command

import cat.daisy.daisySRV.DaisySRV
import cat.daisy.daisySRV.embed.EmbedManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.bukkit.Bukkit
import java.util.logging.Level

/**
 * Handles Discord slash commands
 */
class DiscordCommandHandler(
    private val plugin: DaisySRV,
    private val jda: JDA,
    private val embedManager: EmbedManager,
) : ListenerAdapter() {
    companion object {
        // Configuration paths
        private const val CONFIG_COMMANDS_ENABLED = "commands.enabled"
        private const val CONFIG_COMMANDS_PLAYERLIST = "commands.playerlist"
        private const val CONFIG_DEBUG = "settings.debug"
    }

    private var commandsRegistered = false

    /**
     * Registers slash commands with Discord
     */
    fun registerCommands() {
        if (!plugin.config.getBoolean(CONFIG_COMMANDS_ENABLED, true)) {
            plugin.logger.info("Discord slash commands are disabled in the config")
            return
        }

        try {
            // Create command data
            val commandData =
                mutableListOf(
                    Commands.slash("ping", "Check if the bot is online"),
                )

            // Add player list command if enabled
            if (plugin.config.getBoolean(CONFIG_COMMANDS_PLAYERLIST, true)) {
                commandData.add(Commands.slash("players", "List online players"))
            }

            // Register commands for each guild the bot is in
            jda.guilds.forEach { guild ->
                guild.updateCommands().addCommands(commandData).queue(
                    {
                        plugin.logger.info("Registered ${commandData.size} Discord slash commands in guild ${guild.name}")
                        commandsRegistered = true
                    },
                    { error ->
                        plugin.logger.log(Level.WARNING, "Failed to register Discord slash commands in guild ${guild.name}", error)
                    },
                )
            }

            // Always register the listener regardless of command registration success
            if (!commandsRegistered) {
                jda.addEventListener(this)
                plugin.logger.info("Registered slash command listener")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error registering Discord slash commands", e)
        }
    }

    /**
     * Unregisters slash commands when the plugin is disabled
     */
    fun unregisterCommands() {
        if (!commandsRegistered) return

        try {
            jda.updateCommands().queue(
                {
                    plugin.logger.info("Unregistered Discord slash commands")
                    // Remove the listener
                    jda.removeEventListener(this)
                },
                { error ->
                    plugin.logger.warning("Failed to unregister Discord slash commands: ${error.message}")
                },
            )
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error unregistering Discord slash commands", e)
        }
    }

    /**
     * Handles slash command interactions
     */
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val commandName = event.name

        if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
            plugin.logger.info("Received slash command: $commandName from ${event.user.name}")
        }

        when (commandName) {
            "ping" -> handlePingCommand(event)
            "players" -> handlePlayersCommand(event)
            else -> plugin.logger.warning("Unknown command: $commandName")
        }
    }

    /**
     * Handles the ping command
     */
    private fun handlePingCommand(event: SlashCommandInteractionEvent) {
        // Defer reply to show "Bot is thinking..."
        event.deferReply().queue()

        // Respond with pong message
        val message = "🏓 Pong! Discord Gateway: ${jda.gatewayPing}ms"

        // Fixed: use hook.editOriginal() instead of hookEditOriginal()
        event.hook.editOriginal(message).queue()

        if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
            plugin.logger.info("Responded to ping command from ${event.user.name}")
        }
    }

    /**
     * Handles the players command
     */
    private fun handlePlayersCommand(event: SlashCommandInteractionEvent) {
        // Defer reply to show "Bot is thinking..."
        event.deferReply().queue()

        // Get online players
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                val players = Bukkit.getOnlinePlayers()
                val playerCount = players.size
                val maxPlayers = Bukkit.getMaxPlayers()

                // Create player list embed
                Bukkit.getScheduler().runTaskAsynchronously(
                    plugin,
                    Runnable {
                        val playerNames = players.map { it.name }
                        val embed = embedManager.createPlayerListEmbed(playerCount, maxPlayers, playerNames)

                        // Fixed: use hook.editOriginalEmbeds() instead of hookEditOriginalEmbeds()
                        event.hook.editOriginalEmbeds(embed).queue()

                        if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                            plugin.logger.info("Responded to players command from ${event.user.name}")
                        }
                    },
                )
            },
        )
    }

    /**
     * Sends an error message in response to a slash command
     */
    private fun sendErrorResponse(
        event: SlashCommandInteractionEvent,
        errorMessage: String,
    ) {
        try {
            // If the interaction is already acknowledged
            if (event.isAcknowledged) {
                // Fixed: use hook.editOriginal() instead of hookEditOriginal()
                event.hook.editOriginal("❌ Error: $errorMessage").queue()
            } else {
                event.reply("❌ Error: $errorMessage").setEphemeral(true).queue()
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to send error response for slash command", e)
        }
    }
}
