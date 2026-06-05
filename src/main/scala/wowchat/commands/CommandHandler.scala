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

  private def parseWhoArgument(message: String): Option[String] = {
    val body = message.substring(trigger.length).trim
    val spaceIdx = body.indexOf(' ')
    if (spaceIdx == -1) {
      None
    } else {
      val raw = body.substring(spaceIdx + 1).trim
      if (raw.isEmpty) {
        None
      } else {
        // Discord mentions arrive as @DisplayName; WoW names cannot contain @
        val normalized = raw.stripPrefix("@")
        if (normalized.nonEmpty && normalized.length <= 12) Some(normalized) else None
      }
    }
  }

  // returns back the message as an option if unhandled
  // needs to be refactored into a Map[String, <Intelligent Command Handler Function>]
  def apply(fromChannel: MessageChannel, message: String): Boolean = {
    if (!message.startsWith(trigger)) {
      return false
    }

    val body = message.substring(trigger.length).trim
    val spaceIdx = body.indexOf(' ')
    val possibleCommand = (if (spaceIdx == -1) body else body.substring(0, spaceIdx)).toLowerCase
    val arguments = if (possibleCommand == "who" || possibleCommand == "online") {
      parseWhoArgument(message)
    } else {
      None
    }

    Try {
      possibleCommand match {
        case "who" | "online" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(game => {
            arguments match {
              case Some(name) =>
                logger.debug(s"WHO query for player: $name")
                whoRequest = WhoRequest(fromChannel, name)
              case None =>
                logger.debug("WHO query for guild online list")
            }
            game.handleWho(arguments)
          })
        case "gmotd" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(_.handleGmotd())
      }
    }.fold(throwable => {
      logger.error(s"Command failed: $message", throwable)
      false
    }, opt => {
      // command found, do not send to wow chat
      if (opt.isDefined) {
        Discord.sendMessage(fromChannel, opt.get)
      }
      true
    })
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
