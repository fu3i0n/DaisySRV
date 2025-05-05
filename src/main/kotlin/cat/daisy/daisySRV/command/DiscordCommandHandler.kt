package cat.daisy.daisySRV.command

import cat.daisy.daisySRV.DaisySRV
import cat.daisy.daisySRV.embed.EmbedManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.bukkit.Bukkit

/**
 * Handles Discord slash commands
 */
class DiscordCommandHandler(
    private val plugin: DaisySRV,
    private val jda: JDA,
    private val embedManager: EmbedManager
) : ListenerAdapter() {

    companion object {
        // Configuration paths
        private const val CONFIG_COMMANDS_ENABLED = "commands.enabled"
        private const val CONFIG_COMMANDS_PLAYERLIST = "commands.playerlist"
        private const val CONFIG_DEBUG = "settings.debug"
    }
    
    /**
     * Registers all slash commands with Discord
     */
    fun registerCommands() {
        // Check if commands are enabled
        if (!plugin.config.getBoolean(CONFIG_COMMANDS_ENABLED, true)) {
            plugin.logger.info("Discord commands are disabled in config")
            return
        }
        
        // Create a list to hold our commands
        val commands = mutableListOf<net.dv8tion.jda.api.interactions.commands.build.CommandData>()
        
        // Add playerlist command if enabled
        if (plugin.config.getBoolean(CONFIG_COMMANDS_PLAYERLIST, true)) {
            commands.add(
                Commands.slash("playerlist", "Shows the list of online players")
            )
        }
        
        // Register the commands with Discord (guild commands update instantly)
        if (commands.isNotEmpty()) {
            jda.updateCommands().addCommands(commands).queue(
                { 
                    plugin.logger.info("Successfully registered ${commands.size} Discord commands")
                },
                { error ->
                    plugin.logger.warning("Failed to register Discord commands: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Handles slash command interactions
     * 
     * @param event The slash command event
     */
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        // Check if commands are enabled
        if (!plugin.config.getBoolean(CONFIG_COMMANDS_ENABLED, true)) return
        
        when (event.name) {
            "playerlist" -> handlePlayerListCommand(event)
            else -> {
                // Unknown command
                if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                    plugin.logger.info("Received unknown command: ${event.name}")
                }
            }
        }
    }
    
    /**
     * Handles the playerlist command
     * 
     * @param event The slash command event
     */
    private fun handlePlayerListCommand(event: SlashCommandInteractionEvent) {
        // Check if playerlist command is enabled
        if (!plugin.config.getBoolean(CONFIG_COMMANDS_PLAYERLIST, true)) {
            event.reply("This command is disabled").setEphemeral(true).queue()
            return
        }
        
        // Defer the reply to give us time to process
        event.deferReply().queue()
        
        // Get the player list on the main server thread
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val players = Bukkit.getOnlinePlayers()
            val playerCount = players.size
            val maxPlayers = Bukkit.getMaxPlayers()
            
            // Get player names
            val playerNames = players.map { it.name }.sorted()
            
            // Create and send the embed
            if (embedManager.areEmbedsEnabled()) {
                val embed = embedManager.createPlayerListEmbed(playerCount, maxPlayers, playerNames)
                event.hook.sendMessageEmbeds(embed).queue()
            } else {
                // Fallback to text message
                val message = if (playerCount > 0) {
                    "Online Players ($playerCount/$maxPlayers): ${playerNames.joinToString(", ")}"
                } else {
                    "There are no players online (0/$maxPlayers)"
                }
                event.hook.sendMessage(message).queue()
            }
            
            if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Processed playerlist command")
            }
        })
    }
}