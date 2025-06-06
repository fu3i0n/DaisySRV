package cat.daisy.daisySRV.event

import cat.daisy.daisySRV.DaisySRV
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import org.bukkit.Bukkit
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level

/**
 * Handles forwarding Minecraft console logs to Discord
 */
class MinecraftConsoleHandler(
    private val plugin: DaisySRV,
    private val jda: JDA,
    private val defaultChannel: TextChannel,
) {
    companion object {
        // Configuration paths
        private const val CONFIG_CONSOLE_ENABLED = "console-log.enabled"
        private const val CONFIG_CONSOLE_LOG_CHANNEL_ID = "console-log.log-channel-id"
        private const val CONFIG_DEBUG = "settings.debug"

        // Constants for log message handling
        private const val MAX_DISCORD_MESSAGE_LENGTH = 1900
        private const val MESSAGE_COOLDOWN_MS = 2000
        private const val MAX_BATCH_SIZE = 15
        private const val MESSAGE_BATCH_DELAY = 50L // Ticks (50 ticks = 2.5 seconds)

        // Patterns for message filtering (precompiled for efficiency)
        private val CONSOLE_LOGGING_PATTERN = Regex("Console logging")
        private val WEBHOOK_PATTERN = Regex("webhook", RegexOption.IGNORE_CASE)
        private val STACK_TRACE_PATTERN = Regex("^\\s+at\\s+")
        private val EXCEPTION_PATTERN = Regex("Exception in thread")
        private val DISCONNECT_PATTERN = Regex("handleDisconnect")

        // Enhanced filter patterns for common verbose logs
        private val INFO_PATTERN = Regex("\\[INFO\\]|\\[32m\\[INFO\\]")
        private val ROUTINE_LOGS_PATTERN =
            Regex(
                "(Done preparing level|Running delayed init tasks|Done \\(|Shutdown initiated|Shutdown completed|Starting background profiler|expansion registration|Preparing spawn area|Preparing start region|UUID of player|joined the game|left the game|Player.+?has (disconnected|logged in)|initialized|protocol|Connected)",
            )
        private val SPARK_PATTERN = Regex("\\[spark\\]|spark")
        private val SERVER_ROUTINE_PATTERN =
            Regex("\\[(ServerLoginPacketListenerImpl|MinecraftServer|DedicatedServer|PlayerList|Server|Player|User Authenticator)\\]")
        private val COMMON_NOISE_PATTERN =
            Regex(
                "(async-profiler|Votifier|PlaceholderAPI|Preparing start region|Loading properties|Starting minecraft server|Default game type|Ready|AdvancedServerListPlus|discord|Reloading ResourceManager)",
            )
        private val IP_ADDRESS_PATTERN = Regex("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})(:[0-9]{1,5})?")
    }

    private var consoleAppender: ConsoleLogAppender? = null
    private var logChannel: TextChannel? = null
    private var enabled = false
    private var taskId = -1

    // Message batching for rate limiting
    private val messageQueue = ConcurrentLinkedQueue<String>()
    private val messageCounter = AtomicInteger(0)
    private var lastMessageTime = 0L

    // StringBuilder pool to reduce memory allocations
    private val stringBuilderPool = ThreadLocal.withInitial { StringBuilder(MAX_DISCORD_MESSAGE_LENGTH) }

    init {
        setupConsoleHandler()
    }

    /**
     * Sets up the console handler based on configuration
     */
    private fun setupConsoleHandler() {
        // Check if console logging is enabled
        enabled = plugin.config.getBoolean(CONFIG_CONSOLE_ENABLED, false)

        if (!enabled) {
            plugin.logger.info("Console logging to Discord is disabled")
            return
        }

        // Get the log channel ID from config
        val logChannelId = plugin.config.getString(CONFIG_CONSOLE_LOG_CHANNEL_ID)
        if (logChannelId.isNullOrBlank() || logChannelId == "YOUR_CONSOLE_LOG_CHANNEL_ID_HERE") {
            plugin.logger.warning("Console log channel ID not configured, using default channel")
            logChannel = defaultChannel
        } else {
            // Try to get the specified channel
            logChannel = jda.getTextChannelById(logChannelId)
            if (logChannel == null) {
                plugin.logger.warning("Could not find console log channel with ID: $logChannelId, using default channel")
                logChannel = defaultChannel
            }
        }

        // Create and register the Log4j appender
        registerLogAppender()

        // Schedule the message processor task
        scheduleMessageProcessor()

        plugin.logger.info("Console logging to Discord initialized")
    }

    /**
     * Registers a Log4j appender to capture console logs
     */
    private fun registerLogAppender() {
        try {
            // Create a custom appender
            consoleAppender = ConsoleLogAppender()

            // Get the root logger and add our appender
            val rootLogger = LogManager.getRootLogger() as Logger
            rootLogger.addAppender(consoleAppender)

            if (plugin.config.getBoolean(CONFIG_DEBUG, false)) {
                plugin.logger.info("Registered console log appender")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to register console log appender", e)
        }
    }

    /**
     * Schedules a task to process queued messages
     */
    private fun scheduleMessageProcessor() {
        taskId =
            Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                {
                    processMessageQueue()
                },
                MESSAGE_BATCH_DELAY,
                MESSAGE_BATCH_DELAY,
            )
    }

    /**
     * Processes the message queue to send to Discord
     */
    private fun processMessageQueue() {
        if (messageQueue.isEmpty() || !enabled || messageCounter.get() == 0) return

        // Check if we're in cooldown
        val now = System.currentTimeMillis()
        if (now - lastMessageTime < MESSAGE_COOLDOWN_MS) return

        try {
            val messagesToSend = stringBuilderPool.get()
            messagesToSend.setLength(0) // Clear the StringBuilder

            val count = messageCounter.getAndSet(0)
            val overflow = count > MAX_BATCH_SIZE

            // Add header with timestamp
            messagesToSend.append("```ansi\n")

            // Get messages from queue (limited by MAX_BATCH_SIZE)
            var messageCount = 0
            while (messageCount < MAX_BATCH_SIZE && messageQueue.isNotEmpty()) {
                val message = messageQueue.poll() ?: break

                // Check if adding this message would exceed Discord's limit
                if (messagesToSend.length + message.length + 10 >= MAX_DISCORD_MESSAGE_LENGTH) {
                    // If we're about to exceed the limit, finish this batch
                    break
                }

                messagesToSend.append(message).append('\n')
                messageCount++
            }

            // Add overflow notice if needed
            if (overflow || messageQueue.isNotEmpty()) {
                messagesToSend.append("... and ${count - messageCount + messageQueue.size} more messages\n")
            }

            // Close the code block
            messagesToSend.append("```")

            // Store final message
            val finalMessage = messagesToSend.toString()

            // Send to Discord asynchronously
            val channel = logChannel ?: defaultChannel
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                Runnable {
                    try {
                        channel.sendMessage(finalMessage).queue()
                        lastMessageTime = now
                    } catch (e: Exception) {
                        plugin.logger.log(Level.WARNING, "Failed to send console logs to Discord", e)
                    }
                },
            )
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error processing console message queue", e)
        }
    }

    /**
     * Shuts down the console handler and cleans up resources
     */
    fun shutdown() {
        if (!enabled) return

        try {
            // Cancel the scheduled task
            if (taskId != -1) {
                Bukkit.getScheduler().cancelTask(taskId)
                taskId = -1
            }

            // Remove the appender from Log4j
            consoleAppender?.let {
                val rootLogger = LogManager.getRootLogger() as Logger
                rootLogger.removeAppender(it)
                it.stop()
                consoleAppender = null
            }

            // Process any remaining messages synchronously
            if (messageQueue.isNotEmpty()) {
                processRemainingMessages()
            }

            plugin.logger.info("Console handler shutdown complete")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error during console handler shutdown", e)
        }
    }

    /**
     * Processes any remaining messages during shutdown
     */
    private fun processRemainingMessages() {
        if (messageQueue.isEmpty()) return

        try {
            val channel = logChannel ?: defaultChannel
            val remaining = messageQueue.size

            if (remaining > 0) {
                val finalMessage = stringBuilderPool.get()
                finalMessage.setLength(0) // Clear the StringBuilder

                finalMessage.append("```ansi\n[Console] Shutdown - Final $remaining log messages:\n")
                var count = 0

                while (count < 10 && messageQueue.isNotEmpty()) {
                    val message = messageQueue.poll() ?: break
                    finalMessage.append(message).append('\n')
                    count++
                }

                if (messageQueue.isNotEmpty()) {
                    finalMessage.append("... and ${messageQueue.size} more messages\n")
                    messageQueue.clear()
                }

                finalMessage.append("```")

                // Send synchronously since we're shutting down
                channel.sendMessage(finalMessage.toString()).complete()
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to send final console logs", e)
        }
    }

    /**
     * Custom Log4j appender to capture console logs
     */
    private inner class ConsoleLogAppender :
        AbstractAppender(
            "DaisySRVConsoleAppender",
            null,
            PatternLayout.createDefaultLayout(),
            true,
            Property.EMPTY_ARRAY,
        ) {
        init {
            start() // Start the appender
        }

        override fun append(event: LogEvent) {
            if (!enabled) return

            try {
                // Format the log message
                val level = event.level.toString()
                val loggerName = event.loggerName.substringAfterLast(".")
                val message = event.message.formattedMessage

                // Skip certain noisy messages
                if (shouldSkipMessage(loggerName, message)) return

                // Format with ANSI colors for Discord's code blocks
                val formattedMessage = formatLogMessage(level, loggerName, message)

                // Add to queue
                messageQueue.add(formattedMessage)
                messageCounter.incrementAndGet()
            } catch (e: Exception) {
                // Don't log here to avoid infinite recursion
            }
        }

        /**
         * Determines if a message should be skipped
         */
        private fun shouldSkipMessage(
            loggerName: String,
            message: String,
        ): Boolean {
            // Skip messages from our own plugin that would cause recursion
            if (loggerName == "DaisySRV" &&
                (
                    CONSOLE_LOGGING_PATTERN.containsMatchIn(message) ||
                        message.contains("Sent") ||
                        WEBHOOK_PATTERN.containsMatchIn(message)
                )
            ) {
                return true
            }

            // Skip verbose messages
            if (DISCONNECT_PATTERN.containsMatchIn(message) ||
                EXCEPTION_PATTERN.containsMatchIn(message) ||
                STACK_TRACE_PATTERN.matches(message) // Stack traces usually start with "at "
            ) {
                return true
            }

            // Skip common routine logs
            if (INFO_PATTERN.containsMatchIn(message) ||
                ROUTINE_LOGS_PATTERN.containsMatchIn(message) ||
                SPARK_PATTERN.containsMatchIn(message) ||
                SERVER_ROUTINE_PATTERN.containsMatchIn(message) ||
                COMMON_NOISE_PATTERN.containsMatchIn(message) ||
                IP_ADDRESS_PATTERN.containsMatchIn(message)
            ) {
                return true
            }

            return false
        }

        /**
         * Cleans Minecraft formatting codes from log messages
         */
        private fun cleanLogMessage(message: String): String {
            // Remove color/formatting codes like [32m, [0m, [35m, etc.
            var cleaned = message.replace(Regex("\\u001B\\[[0-9;]*m"), "")

            // Remove redundant bracketed tags like [INFO], [MinecraftServer]
            cleaned = cleaned.replace(Regex("\\[(?:INFO|WARN|ERROR|DEBUG|MinecraftServer|Server)\\]"), "")

            // Clean up any resulting double spaces or empty brackets
            cleaned = cleaned.replace(Regex("\\s+"), " ")
            cleaned = cleaned.replace(Regex("\\[\\s*\\]"), "")
            cleaned = cleaned.replace(Regex(":\\s+:"), ":")

            return cleaned.trim()
        }

        /**
         * Formats a log message with ANSI colors for Discord
         */
        private fun formatLogMessage(
            level: String,
            loggerName: String,
            message: String,
        ): String {
            val timestamp =
                java.time.format.DateTimeFormatter
                    .ofPattern("HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(Instant.now())

            val levelColor =
                when (level) {
                    "INFO" -> "\u001B[32m" // Green
                    "WARN" -> "\u001B[33m" // Yellow
                    "ERROR", "FATAL" -> "\u001B[31m" // Red
                    "DEBUG" -> "\u001B[36m" // Cyan
                    else -> "\u001B[37m" // White
                }

            // Clean the message content before formatting
            val cleanedMessage = cleanLogMessage(message)

            return "[$timestamp] $levelColor[$level]\u001B[0m [\u001B[35m${loggerName}\u001B[0m]: $cleanedMessage"
        }
    }
}
