package wowchat.commands

import com.typesafe.scalalogging.StrictLogging
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import wowchat.common.Global
import wowchat.discord.Discord
import wowchat.game.{GamePackets, GameResources, GuildInfo, GuildMember}

import scala.collection.mutable
import scala.util.Try

case class WhoRequest(messageChannel: MessageChannel, playerName: String)
case class WhoResponse(playerName: String, guildName: String, lvl: Int, cls: String, race: String, gender: Option[String], zone: String)

object CommandHandler extends StrictLogging {

  private val NOT_ONLINE = "Bot is not online."

  // make some of these configurable
  private val trigger = "?"

  // gross. rewrite
  var whoRequest: WhoRequest = _
  var lastWhoChannel: Option[MessageChannel] = None

  /**
   * Cleans Discord mention and plain text arguments.
   * Converts <@userid> or @username to just the username
   */
  private def cleanArgument(arg: String): String = {
    if (arg == null || arg.isEmpty) {
      return arg
    }
    
    // Handle Discord mention format: <@userid> or <@!userid>
    if (arg.startsWith("<@") && arg.endsWith(">") && arg.length > 3) {
      try {
        // Extract just the ID/username portion and remove special chars
        arg.substring(2, arg.length - 1).replaceAll("[!&]", "")
      } catch {
        case e: Exception =>
          logger.error(s"Error parsing Discord mention '$arg': $e")
          arg
      }
    } else if (arg.startsWith("@") && arg.length > 1) {
      // Handle @username format - just strip the @
      arg.substring(1)
    } else {
      // Plain username
      arg
    }
  }

  // returns back the message as an option if unhandled
  // needs to be refactored into a Map[String, <Intelligent Command Handler Function>]
  def apply(fromChannel: MessageChannel, message: String): Boolean = {
    if (!message.startsWith(trigger)) {
      return false
    }

    val splt = message.substring(trigger.length).split(" ")
    val possibleCommand = splt(0).toLowerCase
    val arguments = if (splt.length > 1) {
      Some(cleanArgument(splt(1)))
    } else {
      None
    }

    var commandHandled = false
    var responseMessage: Option[String] = None

    Try {
      possibleCommand match {
        case "who" | "online" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            commandHandled = true
          })(game => {
            if (arguments.isDefined) {
              // User is querying for a specific player
              whoRequest = WhoRequest(fromChannel, arguments.get)
              lastWhoChannel = Some(fromChannel)
              game.handleWho(arguments)
            } else {
              // User is querying for all online guildies
              lastWhoChannel = Some(fromChannel)
              responseMessage = game.handleWho(arguments)
            }
            commandHandled = true
          })
        case "gmotd" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            commandHandled = true
          })(game => {
            responseMessage = game.handleGmotd()
            commandHandled = true
          })
        case _ =>
          commandHandled = false
      }
    }.fold(throwable => {
      logger.error(s"Error handling command '$possibleCommand': ${throwable.getMessage}", throwable)
      commandHandled = false
    }, _ => {})

    // Send response if we have one (for immediate responses like guild list)
    if (responseMessage.isDefined) {
      Discord.sendMessage(fromChannel, responseMessage.get)
    }

    commandHandled
  }

  // eww
  def handleWhoResponse(whoResponse: Option[WhoResponse],
                        guildInfo: GuildInfo,
                        guildRoster: mutable.Map[Long, GuildMember],
                        guildRosterMatcherFunc: GuildMember => Boolean): Iterable[String] = {
    whoResponse.map(r => {
      Seq(s"${r.playerName} ${if (r.guildName.nonEmpty) s"<${r.guildName}> " else ""}is a level ${r.lvl}${r.gender.fold(" ")(g => s" $g ")}${r.race} ${r.cls} currently in ${r.zone}.")
    }).getOrElse({
      // Check guild roster
      guildRoster
        .values
        .filter(guildRosterMatcherFunc)
        .map(guildMember => {
          val cls = new GamePackets{}.Classes.valueOf(guildMember.charClass) // ... should really move that out
          val days = guildMember.lastLogoff.toInt
          val hours = ((guildMember.lastLogoff * 24) % 24).toInt
          val minutes = ((guildMember.lastLogoff * 24 * 60) % 60).toInt
          val minutesStr = s" $minutes minute${if (minutes != 1) "s" else ""}"
          val hoursStr = if (hours > 0) s" $hours hour${if (hours != 1) "s" else ""}," else ""
          val daysStr = if (days > 0) s" $days day${if (days != 1) "s" else ""}," else ""

          val guildNameStr = if (guildInfo != null) {
            s" <${guildInfo.name}>"
          } else {
            // Welp, some servers don't set guild guid in character selection packet.
            // The only other way to get this information is through parsing SMSG_UPDATE_OBJECT
            // and its compressed version which is quite annoying especially across expansions.
            ""
          }

          s"${guildMember.name}$guildNameStr is a level ${guildMember.level} $cls currently offline. " +
            s"Last seen$daysStr$hoursStr$minutesStr ago in ${GameResources.AREA.getOrElse(guildMember.zoneId, "Unknown Zone")}."
        })
    })
  }
}
