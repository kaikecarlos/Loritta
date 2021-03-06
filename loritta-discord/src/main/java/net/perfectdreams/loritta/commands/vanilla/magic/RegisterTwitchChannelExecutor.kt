package net.perfectdreams.loritta.commands.vanilla.magic

import net.perfectdreams.loritta.api.commands.CommandContext
import net.perfectdreams.loritta.api.messages.LorittaReply

object RegisterTwitchChannelExecutor : LoriToolsCommand.LoriToolsExecutor {
	override val args = "register twitch <channel-id>"

	override fun executes(): suspend CommandContext.() -> Boolean = task@{
		if (args.getOrNull(0) != "register")
			return@task false
		if (args.getOrNull(1) != "twitch")
			return@task false

		val code = com.mrpowergamerbr.loritta.utils.loritta.twitch.makeTwitchApiRequest("https://api.twitch.tv/helix/webhooks/hub", "POST",
				mapOf(
						"hub.callback" to "${com.mrpowergamerbr.loritta.utils.loritta.instanceConfig.loritta.website.url}api/v1/callbacks/pubsubhubbub?type=twitch&userid=${args.getOrNull(2)}",
						"hub.lease_seconds" to "864000",
						"hub.mode" to "subscribe",
						"hub.secret" to com.mrpowergamerbr.loritta.utils.loritta.config.mixer.webhookSecret,
						"hub.topic" to "https://api.twitch.tv/helix/streams?user_id=${args.getOrNull(2)}"
				))
				.code()

		reply(
				LorittaReply(
						"Feito! Code: $code"
				)
		)
		return@task true
	}
}