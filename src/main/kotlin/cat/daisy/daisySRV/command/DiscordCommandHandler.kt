package cat.daisy.daisySRV.command

import cat.daisy.daisySRV.DaisySRV
import cat.daisy.daisySRV.embed.EmbedManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.bukkit.Bukkit
import java.util.logging.Level

/**
 * Handles Discord slash commands for the DaisySRV plugin
 */
class DiscordCommandHandler(
    private val plugin: DaisySRV,
    private val jda: JDA,
    private val embedManager: EmbedManager,
) : ListenerAdapter() {
    companion object {
        private const val CONFIG_COMMANDS_ENABLED = "commands.enabled"
        private const val CONFIG_COMMANDS_PLAYERLIST = "commands.playerlist"
        private const val CONFIG_DEBUG = "settings.debug"

        // Command handlers map for faster lookups
        private val COMMAND_HANDLERS =
            mapOf<String, (DiscordCommandHandler, SlashCommandInteractionEvent) -> Unit>(
                "ping" to { handler, event -> handler.handlePingCommand(event) },
                "players" to { handler, event -> handler.handlePlayerListCommand(event) },
                "console" to { handler, event -> handler.handleConsoleCommand(event) },
            )
    }

    private var commandsRegistered = false

    /**
     * Registers slash commands with the Discord bot
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

            // Add console command
            commandData.add(
                Commands
                    .slash("console", "Execute a server console command")
                    .addOption(OptionType.STRING, "command", "The command to execute", true),
            )

            // Register commands globally if no guilds are available
            if (jda.guilds.isEmpty()) {
                jda.updateCommands().addCommands(commandData).queue(
                    {
                        plugin.logger.info("Registered ${commandData.size} global Discord slash commands")
                        commandsRegistered = true
                    },
                    { error ->
                        plugin.logger.log(Level.SEVERE, "Failed to register global commands", error)
                    },
                )
            } else {
                jda.guilds.forEach { guild ->
                    guild.updateCommands().addCommands(commandData).queue(
                        {
                            plugin.logger.info("Registered ${commandData.size} commands for guild: ${guild.name}")
                            commandsRegistered = true
                        },
                        { error ->
                            plugin.logger.log(
                                Level.SEVERE,
                                "Failed to register commands for guild: ${guild.name}",
                                error,
                            )
                        },
                    )
                }
            }

            // Register the listener
            jda.addEventListener(this)
            plugin.logger.info("Registered slash command listener")
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error registering Discord slash commands", e)
        }
    }

    /**
     * Unregisters all slash commands
     */
    fun unregisterCommands() {
        if (!commandsRegistered) return

        try {
            jda.updateCommands().queue(
                { plugin.logger.info("Unregistered all global Discord slash commands") },
                { error -> plugin.logger.log(Level.SEVERE, "Failed to unregister global commands", error) },
            )
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error unregistering Discord slash commands", e)
        }
    }

    /**
     * Handles slash command interactions
     */
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val handler = COMMAND_HANDLERS[event.name]
        if (handler != null) {
            handler(this, event)
        } else {
            event.reply("❌ Unknown command").setEphemeral(true).queue()
        }
    }

    /**
     * Handles the /ping command
     */
    private fun handlePingCommand(event: SlashCommandInteractionEvent) {
        event.reply("🏓 Pong! Discord Gateway: ${jda.gatewayPing}ms").queue()
    }

    /**
     * Handles the /players command
     */
    private fun handlePlayerListCommand(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()

        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                val onlinePlayers = Bukkit.getOnlinePlayers()
                val playerCount = onlinePlayers.size
                val maxPlayers = Bukkit.getMaxPlayers()
                val playerNames = onlinePlayers.map { it.name }

                // Always use the embed, even if no players are online
                val embed = embedManager.createPlayerListEmbed(playerCount, maxPlayers, playerNames)
                event.hook.sendMessageEmbeds(embed).queue()
            },
        )
    }

    /**
     * Handles the /console command
     */
    private fun handleConsoleCommand(event: SlashCommandInteractionEvent) {
        val command =
            event.getOption("command")?.asString ?: run {
                event.reply("❌ You must provide a command to execute.").setEphemeral(true).queue()
                return
            }

        // Check if the user has the required role
        val requiredRoleId = plugin.config.getString("console-log.whitelist-role")
        if (requiredRoleId != null && event.member?.roles?.none { it.id == requiredRoleId } == true) {
            event.reply("❌ You do not have permission to use this command.").setEphemeral(true).queue()
            return
        }

        // Acknowledge the interaction
        event.deferReply(true).queue()

        // Explicitly cast the lambda to Runnable
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                try {
                    val success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                    if (success) {
                        event.hook.sendMessage("✅ Command executed: `$command`").queue()
                    } else {
                        event.hook.sendMessage("❌ Failed to execute command: `$command`").queue()
                    }
                } catch (e: Exception) {
                    event.hook.sendMessage("❌ An error occurred while executing the command: ${e.message}").queue()
                }
            },
        )
    }
}
