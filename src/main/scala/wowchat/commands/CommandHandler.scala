package wowchat.commands

import com.typesafe.scalalogging.StrictLogging
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import wowchat.common.Global
import wowchat.discord.Discord
import wowchat.game.{GamePackets, GameResources, GuildInfo, GuildMember}

import scala.collection.mutable

case class WhoRequest(messageChannel: MessageChannel, playerName: String)
case class WhoResponse(playerName: String, guildName: String, lvl: Int, cls: String, race: String, gender: Option[String], zone: String)

object CommandHandler extends StrictLogging {

  private val NOT_ONLINE = "Bot is not online."
  private val trigger = "?"

  var whoRequest: WhoRequest = _

  def apply(fromChannel: MessageChannel, message: String): Boolean = {
    if (!message.startsWith(trigger)) {
      return false
    }

    val splt = message.substring(trigger.length).split(" ")
    val possibleCommand = splt(0).toLowerCase
    val arguments = if (splt.length > 1) Some(splt(1)) else None

    possibleCommand match {
      case "who" | "online" =>
        handleWhoCommand(fromChannel, arguments)
        true

      case "gmotd" =>
        handleGmotdCommand(fromChannel)
        true

      case _ =>
        false
    }
  }

  private def handleWhoCommand(fromChannel: MessageChannel, arguments: Option[String]): Unit = {
    Global.game.fold({
      Discord.sendMessage(fromChannel, NOT_ONLINE)
    })(game => {
      if (arguments.isDefined) {
        // User is querying for a specific player - send WHO packet to WoW and wait for response
        logger.debug(s"WHO query for player: ${arguments.get}")
        whoRequest = WhoRequest(fromChannel, arguments.get)
        game.handleWho(arguments)
      } else {
        // User is querying for all online guildies - return immediately from guild roster
        logger.debug("WHO query for all online guildies")
        val response = game.handleWho(arguments)
        if (response.isDefined) {
          Discord.sendMessage(fromChannel, response.get)
        } else {
          Discord.sendMessage(fromChannel, "Currently no guildies online.")
        }
      }
    })
  }

  private def handleGmotdCommand(fromChannel: MessageChannel): Unit = {
    Global.game.fold({
      Discord.sendMessage(fromChannel, NOT_ONLINE)
    })(game => {
      val response = game.handleGmotd()
      if (response.isDefined) {
        Discord.sendMessage(fromChannel, response.get)
      }
    })
  }

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
          val cls = new GamePackets{}.Classes.valueOf(guildMember.charClass)
          val days = guildMember.lastLogoff.toInt
          val hours = ((guildMember.lastLogoff * 24) % 24).toInt
          val minutes = ((guildMember.lastLogoff * 24 * 60) % 60).toInt
          val minutesStr = s" $minutes minute${if (minutes != 1) "s" else ""}"
          val hoursStr = if (hours > 0) s" $hours hour${if (hours != 1) "s" else ""}," else ""
          val daysStr = if (days > 0) s" $days day${if (days != 1) "s" else ""}," else ""

          val guildNameStr = if (guildInfo != null) {
            s" <${guildInfo.name}>"
          } else {
            ""
          }

          s"${guildMember.name}$guildNameStr is a level ${guildMember.level} $cls currently offline. " +
            s"Last seen$daysStr$hoursStr$minutesStr ago in ${GameResources.AREA.getOrElse(guildMember.zoneId, "Unknown Zone")}."
        })
    })
  }
}
