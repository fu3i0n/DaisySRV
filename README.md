# DaisySRV

An enhanced Minecraft-Discord chat bridge plugin for Paper servers with rich embeds, slash commands, and more.

## Features

- Forward messages from Minecraft to Discord
- Forward messages from Discord to Minecraft
- Rich embeds for player join/leave events
- Rich embeds for player achievements
- Server status notifications (online/offline)
- Discord slash commands (e.g., `/players`)
- Console slash commands (e.g., `/console [message]`)
- Bot status showing player count
- Webhook support for player messages with avatars and names
- Customizable message formats and colors
- Simple setup and configuration

## Requirements

- Paper 1.21+ server
- A Discord bot token
- Java 21+

## Setup

### 1. Create a Discord Bot

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Click "New Application" and give it a name
3. Go to the "Bot" tab and click "Add Bot"
4. Under the "Privileged Gateway Intents" section, enable:
   - Message Content Intent
   - Server Members Intent
5. Copy your bot token (you'll need this for the config)

### 2. Invite the Bot to Your Server

1. Go to the "OAuth2" tab in the Discord Developer Portal
2. In the "URL Generator" section, select the "bot" and "applications.commands" scopes
3. Select the following permissions:
   - Read Messages/View Channels
   - Send Messages
   - Embed Links
   - Read Message History
   - Use Slash Commands
4. Copy the generated URL and open it in your browser
5. Select your server and authorize the bot

### 3. Get Your Channel ID

1. In Discord, go to User Settings > Advanced and enable "Developer Mode"
2. Right-click on the channel you want to use and select "Copy ID"

### 4. (Optional) Create a Webhook for Player Messages

If you want player messages to be sent with their Minecraft username and avatar:

1. In Discord, go to the channel settings
2. Click on "Integrations"
3. Click on "Webhooks"
4. Click "New Webhook"
5. Give it a name (e.g., "DaisySRV")
6. Click "Copy Webhook URL" (you'll need this for the config)

### 5. Install the Plugin

1. Download the DaisySRV.jar file
2. Place it in your server's `plugins` folder
3. Start or restart your server
4. Edit the `plugins/DaisySRV/config.yml` file:
   - Set `discord.token` to your bot token
   - Set `discord.channel-id` to your channel ID
   - (Optional) Set `webhook.enabled` to `true`
   - (Optional) Set `webhook.url` to your webhook URL
5. Run the command `/qdiscord reload` in-game or restart your server

## Configuration

The `config.yml` file contains the following options:

```yaml
# ======================================
# DaisySRV Configuration
# ======================================
# Version 1.3

# ======================================
# Discord Bot Configuration
# ======================================
discord:
   # Your Discord bot token (KEEP THIS SECRET!)
   # Get this from https://discord.com/developers/applications
   token: "YOUR_BOT_TOKEN_HERE"

   # The Discord channel ID to send/receive messages
   # Right-click on a channel in Discord and select "Copy ID"
   channel-id: "YOUR_CHANNEL_ID_HERE"


# ======================================
# Console Log Configuration
# ======================================
console-log:
   # Whether to forward console messages to Discord
   enabled: false

   # The Discord role ID that is allowed to use the /console command
   # Right-click on a role in Discord server settings and select "Copy ID"
   whitelist-role: "YOUR_ADMIN_ROLE_ID_HERE"

   # The Discord channel ID for console command logs
   # You may want to use a separate, private channel for this
   log-channel-id: "YOUR_CONSOLE_LOG_CHANNEL_ID_HERE"


# ======================================
# Message Format Configuration
# ======================================
format:
   # Format for Discord messages sent to Minecraft
   # Available placeholders: {username}, {message}
   # Color codes with & are supported
   discord-to-minecraft: "&b[Discord] &f{username}: &7{message}"

   # Format for Minecraft messages sent to Discord
   # Available placeholders: {username}, {message}
   # Markdown formatting is supported
   minecraft-to-discord: "**{username}**: {message}"


# ======================================
# Embed Configuration
# ======================================
embeds:
   # Whether to use embeds for events (looks nicer but can be disabled for compatibility)
   enabled: true

   # Colors for different types of embeds (hex color codes)
   colors:
      join: "#55FF55"       # Green
      leave: "#FF5555"      # Red
      achievement: "#FFAA00" # Gold
      server: "#55FFFF"     # Aqua
      playerlist: "#5555FF" # Blue


# ======================================
# Event Configuration
# ======================================
events:
   # Whether to send messages when players join
   player-join: true

   # Whether to send messages when players leave
   player-quit: true

   # Whether to send messages when players earn achievements
   player-advancement: true

   # Whether to send a message when the server starts
   server-start: true

   # Whether to send a message when the server stops
   server-stop: true


# ======================================
# Command Configuration
# ======================================
commands:
   # Whether to enable Discord slash commands
   enabled: true

   # Whether to enable the playerlist command
   playerlist: true


# ======================================
# Bot Status Configuration
# ======================================
status:
   # Whether to show player count in bot status
   enabled: true

   # Type of status (PLAYING, WATCHING, LISTENING, COMPETING)
   type: "PLAYING"

   # Format for the status message
   # Available placeholders: {playerCount}, {maxPlayers}
   format: "{playerCount}/{maxPlayers} players online"


# ======================================
# Webhook Configuration
# ======================================
webhook:
   # Whether to use webhooks for player messages (shows player avatars and names)
   enabled: false

   # Your Discord webhook URL (KEEP THIS SECRET!)
   # Get this by editing a channel, going to Integrations > Webhooks
   url: "YOUR_WEBHOOK_URL_HERE"

   # Default webhook name (used for system messages)
   name: "DaisySRV"

   # Avatar URL template for players (use {username} as placeholder)
   avatar-url: "https://www.mc-heads.net/avatar/{username}"


# ======================================
# General Settings
# ======================================
settings:
   # Whether to enable debug logging (verbose - only enable if troubleshooting)
   debug: false

```

## Discord Commands

The plugin adds the following Discord slash commands:

- `/players` - Shows a list of online players with an embed

## Minecraft Commands

- `/qdiscord reload` - Reload the configuration

## Permissions

- `DaisySRV.admin` - Access to admin commands (default: op)

## Building from Source

1. Clone the repository
2. Run `./gradlew shadowJar`
3. The built jar will be in `build/libs/`

## License

This project is licensed under the MIT License - see the LICENSE file for details.
