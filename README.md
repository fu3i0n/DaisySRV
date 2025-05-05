# DaisySRV

An enhanced Minecraft-Discord chat bridge plugin for Paper servers with rich embeds, slash commands, and more.

## Features

- Forward messages from Minecraft to Discord
- Forward messages from Discord to Minecraft
- Rich embeds for player join/leave events
- Rich embeds for player achievements
- Server status notifications (online/offline)
- Discord slash commands (e.g., `/playerlist`)
- Bot status showing player count
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

### 4. Install the Plugin

1. Download the DaisySRV.jar file
2. Place it in your server's `plugins` folder
3. Start or restart your server
4. Edit the `plugins/DaisySRV/config.yml` file:
   - Set `discord.token` to your bot token
   - Set `discord.channel-id` to your channel ID
5. Run the command `/qdiscord reload` in-game or restart your server

## Configuration

The `config.yml` file contains the following options:

```yaml
# Discord Bot Configuration
discord:
  # Your Discord bot token (KEEP THIS SECRET!)
  token: "YOUR_BOT_TOKEN_HERE"

  # The Discord channel ID to send/receive messages
  channel-id: "YOUR_CHANNEL_ID_HERE"

# Message Format Configuration
format:
  # Format for Discord messages sent to Minecraft
  # Available placeholders: {username}, {message}
  discord-to-minecraft: "&b[Discord] &f{username}: &7{message}"

  # Format for Minecraft messages sent to Discord
  # Available placeholders: {username}, {message}
  minecraft-to-discord: "**{username}**: {message}"

# Embed Configuration
embeds:
  # Whether to use embeds for events
  enabled: true

  # Colors for different types of embeds (hex color codes)
  colors:
    join: "#55FF55"       # Green
    leave: "#FF5555"      # Red
    achievement: "#FFAA00" # Gold
    server: "#55FFFF"     # Aqua
    playerlist: "#5555FF" # Blue

# Event Configuration
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

# Command Configuration
commands:
  # Whether to enable Discord slash commands
  enabled: true

  # Whether to enable the playerlist command
  playerlist: true

# Bot Status Configuration
status:
  # Whether to show player count in bot status
  enabled: true

  # Type of status (PLAYING, WATCHING, LISTENING, COMPETING)
  type: "PLAYING"

  # Format for the status message
  # Available placeholders: {playerCount}, {maxPlayers}
  format: "{playerCount}/{maxPlayers} players online"

# General Settings
settings:
  # Whether to enable debug logging
  debug: false

  # Whether to forward console messages to Discord
  forward-console: false
```

## Discord Commands

The plugin adds the following Discord slash commands:

- `/playerlist` - Shows a list of online players with an embed

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
