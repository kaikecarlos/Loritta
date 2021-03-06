package net.perfectdreams.loritta.plugin.funky.commands

import com.mrpowergamerbr.loritta.utils.LorittaPermission
import net.perfectdreams.loritta.api.LorittaBot
import net.perfectdreams.loritta.api.messages.LorittaReply
import net.perfectdreams.loritta.plugin.funky.FunkyPlugin
import net.perfectdreams.loritta.plugin.funky.commands.LoopCommand.checkIfMusicIsPlaying
import net.perfectdreams.loritta.plugin.funky.commands.PlayCommand.checkMusicPremium
import net.perfectdreams.loritta.plugin.funky.commands.base.DSLCommandBase

object ShuffleCommand : DSLCommandBase {
	override fun command(loritta: LorittaBot, m: FunkyPlugin) = create(loritta, listOf("shuffle", "embaralhar")) {
		description { it["commands.audio.shuffle.description"] }

		userRequiredLorittaPermissions = listOf(LorittaPermission.DJ)

		executesDiscord {
			checkMusicPremium()

			val musicManager = checkIfMusicIsPlaying(m.funkyManager)

			// Limpar lista de qualquer música que tenha
			val shuffledQueue = musicManager.scheduler.queue.toList()
					.shuffled()

			musicManager.scheduler.queue.clear()
			musicManager.scheduler.queue.addAll(shuffledQueue)

			reply(
					LorittaReply(
							"Playlist foi bagunçada!"
					)
			)
		}
	}
}