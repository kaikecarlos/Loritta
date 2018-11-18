package com.mrpowergamerbr.loritta.modules

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.collect.EvictingQueue
import com.google.common.collect.Queues
import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.commands.vanilla.administration.BanCommand
import com.mrpowergamerbr.loritta.dao.Profile
import com.mrpowergamerbr.loritta.events.LorittaMessageEvent
import com.mrpowergamerbr.loritta.userdata.ServerConfig
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.LorittaPermission
import com.mrpowergamerbr.loritta.utils.LorittaUser
import com.mrpowergamerbr.loritta.utils.MessageUtils
import com.mrpowergamerbr.loritta.utils.config.EnvironmentType
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import mu.KotlinLogging
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.User
import org.apache.commons.text.similarity.LevenshteinDistance
import java.util.*
import java.util.concurrent.TimeUnit

class AutomodModule : MessageReceivedModule {
	companion object {
		val MESSAGES  = Caffeine.newBuilder().expireAfterWrite(5L, TimeUnit.MINUTES).build<String, Queue<Message>>().asMap()
		const val FRESH_ACCOUNT_TIMEOUT = 604_800_000L
		var ANTIRAID_ENABLED = true
		var SIMILAR_MESSAGE_MULTIPLIER = 0.0025
		var SIMILARITY_THRESHOLD = 7
		var IN_ROW_SAME_USER_SIMILAR_SCORE = 0.024
		var IN_ROW_DIFFERENT_USER_SIMILAR_SCORE = 0.015
		var ATTACHED_IMAGE_SCORE = 0.005
		var SAME_LINK_SCORE = 0.007
		var SIMILAR_SAME_AUTHOR_MESSAGE_MULTIPLIER = 0.032
		var NO_AVATAR_SCORE = 0.02
		var MUTUAL_GUILDS_MULTIPLIER = 0.01
		var FRESH_ACCOUNT_DISCORD_MULTIPLIER = 0.00000000001
		var FRESH_ACCOUNT_JOINED_MULTIPLIER =  0.00000000007
		var BAN_THRESHOLD = 0.75

		private val logger = KotlinLogging.logger {}
	}

	override fun matches(event: LorittaMessageEvent, lorittaUser: LorittaUser, lorittaProfile: Profile, serverConfig: ServerConfig, locale: BaseLocale): Boolean {
		if (lorittaUser.hasPermission(LorittaPermission.BYPASS_AUTO_MOD))
			return false

		return true
	}

	override fun handle(event: LorittaMessageEvent, lorittaUser: LorittaUser, lorittaProfile: Profile, serverConfig: ServerConfig, locale: BaseLocale): Boolean {
		val message = event.message
		val textChannelConfig = serverConfig.getTextChannelConfig(message.channel.id)

		val automodConfig = textChannelConfig.automodConfig
		val automodCaps = automodConfig.automodCaps
		val automodSelfEmbed = automodConfig.automodSelfEmbed

		if (ANTIRAID_ENABLED && (Loritta.config.antiRaidIds.contains(event.channel.id)) && Loritta.config.environment == EnvironmentType.CANARY) {
			val messages = MESSAGES.getOrPut(event.textChannel!!.id) { Queues.synchronizedQueue(EvictingQueue.create<Message>(50)) }

			fun calculateRaidingPercentage(wrapper: Message): Double {
				val pattern = Constants.URL_PATTERN
				val matcher = pattern.matcher(wrapper.contentRaw)

				val urlsDetected = mutableSetOf<String>()

				while (matcher.find())
					urlsDetected.add(matcher.group(0))

				// println(wrapper.author.id + ": (original message is ${wrapper.content}")
				val raider = wrapper.author
				var raidingPercentage = 0.0

				val verySimilarMessages = mutableListOf<Message>()
				var streamFloodCounter = 0

				for (message in messages.reversed()) {
					if (message.contentRaw.isNotBlank()) {
						if (0 > streamFloodCounter)
							streamFloodCounter = 0

						val isStreamFlood = 2 > streamFloodCounter

						val threshold = LevenshteinDistance.getDefaultInstance().apply(message.contentRaw.toLowerCase(), wrapper.contentRaw.toLowerCase())

						if (3 >= threshold && wrapper.author.id == message.author.id) { // Vamos melhorar caso exista alguns "one person raider"
							verySimilarMessages.add(message)
						}

						if (5 >= threshold && isStreamFlood) { // Vamos aumentar os pontos caso sejam mensagens parecidas em seguida
							// println("Detected stream flood by ${wrapper.author.id}! - $streamFloodCounter")
							// println("Stream flood!")
							raidingPercentage += if (wrapper.author.id != message.author.id) {
								AutomodModule.IN_ROW_SAME_USER_SIMILAR_SCORE
							} else {
								AutomodModule.IN_ROW_DIFFERENT_USER_SIMILAR_SCORE
							}
							// println(">>> ${wrapper.author.id}: IN_ROW_XYZ_SIMILAR_SCORE ${raidingPercentage}")
							streamFloodCounter--
						} else {
							streamFloodCounter++
						}

						raidingPercentage += AutomodModule.SIMILAR_MESSAGE_MULTIPLIER * (Math.max(0, AutomodModule.SIMILARITY_THRESHOLD - threshold))
						// println(">>> ${wrapper.author.id}: SIMILAR_MESSAGE_MULTIPLIER ${raidingPercentage}")
					}

					if (wrapper.attachments.isNotEmpty() && message.attachments.isNotEmpty()) {
						raidingPercentage += AutomodModule.ATTACHED_IMAGE_SCORE
						// println(">>> ${wrapper.author.id}: ATTACHED_IMAGE_SCORE ${raidingPercentage}")
					}

					val matcher2 = pattern.matcher(wrapper.contentRaw)

					while (matcher2.find()) {
						if (urlsDetected.contains(matcher2.group(0))) {
							// println("Has same link!")
							raidingPercentage += AutomodModule.SAME_LINK_SCORE
							// println(">>> ${wrapper.author.id}: SAME_LINK_SCORE ${raidingPercentage}")
						}
					}
				}

				raidingPercentage += AutomodModule.SIMILAR_SAME_AUTHOR_MESSAGE_MULTIPLIER * verySimilarMessages.size
				// println(">>> ${wrapper.author.id}: SIMILAR_SAME_AUTHOR_MESSAGE_MULTIPLIER ${raidingPercentage}")

				if (wrapper.author.avatarUrl == null) {
					raidingPercentage += AutomodModule.NO_AVATAR_SCORE
					// println(">>> ${wrapper.author.id}: NO_AVATAR_SCORE ${raidingPercentage}")
				}

				// Caso o usuário esteja em poucos servidores compartilhados, a chance de ser raider é maior
				raidingPercentage += AutomodModule.MUTUAL_GUILDS_MULTIPLIER * Math.max(5 - raider.mutualGuilds.size, 1)
				// println(">>> ${wrapper.author.id}: MUTUAL_GUILDS_MULTIPLIER ${raidingPercentage}")
				// println("criada em ${Instant.ofEpochMilli(raider.createdAt).atZone(ZoneId.systemDefault()).toOffsetDateTime().fancier()} - $value")
				raidingPercentage += AutomodModule.FRESH_ACCOUNT_DISCORD_MULTIPLIER * Math.max(0, AutomodModule.FRESH_ACCOUNT_TIMEOUT - (System.currentTimeMillis() - wrapper.author.creationTime.toInstant().toEpochMilli()))
				// println(">>> ${wrapper.author.id}: FRESH_ACCOUNT_DISCORD_MULTIPLIER ${raidingPercentage}")

				val member = event.member
				if (member != null)
					raidingPercentage += AutomodModule.FRESH_ACCOUNT_JOINED_MULTIPLIER * Math.max(0, AutomodModule.FRESH_ACCOUNT_TIMEOUT - (System.currentTimeMillis() - member.joinDate.toInstant().toEpochMilli()))
				// println(">>> ${wrapper.author.id}: FRESH_ACCOUNT_JOINED_MULTIPLIER ${raidingPercentage}")
				// raidingPercentage += AutomodModule.FRESH_ACCOUNT_DISCORD_MULTIPLIER * Math.max(AutomodModule.FRESH_ACCOUNT_TIMEOUT - (wrapper.author.createdAt - AutomodModule.FRESH_ACCOUNT_TIMEOUT), 0)
				// val member = wrapper.author
				// if (member != null) {
				// 	raidingPercentage += AutomodModule.FRESH_ACCOUNT_JOINED_MULTIPLIER * (Math.max(AutomodModule.FRESH_ACCOUNT_TIMEOUT - (member.joinDate.toInstant().toEpochMilli() - AutomodModule.FRESH_ACCOUNT_TIMEOUT), 0))
				// }

				return raidingPercentage
			}

			val raidingPercentage = calculateRaidingPercentage(event.message)
			logger.info("[${event.guild!!.name} -> ${event.channel.name}] ${event.author.id} (${raidingPercentage}% chance de ser raider: ${event.message.contentRaw}")

			if (raidingPercentage >= 0.5) {
				logger.warn("[${event.guild.name} -> ${event.channel.name}] ${event.author.id} (${raidingPercentage}% chance de ser raider (CHANCE ALTA DEMAIS!): ${event.message.contentRaw}")
			}
			if (raidingPercentage >= BAN_THRESHOLD) {
				logger.info("Applying punishments to all involved!")
				val alreadyBanned = mutableListOf<User>()

				for (storedMessage in messages) {
					if (!event.guild.isMember(event.author) || alreadyBanned.contains(storedMessage.author)) // O usuário já pode estar banido
						continue

					val percentage = calculateRaidingPercentage(storedMessage)

					if (percentage >= BAN_THRESHOLD) {
						alreadyBanned.add(storedMessage.author)
						BanCommand.ban(serverConfig, event.guild, event.guild.selfMember.user, locale, storedMessage.author, "Tentativa de Raid! (Isto é experimental, caso você tenha sido banido sem querer, vá em https://loritta.website/support e entre de novo :3)", false, 7)
					}
				}

				if (!event.guild.isMember(event.author) || alreadyBanned.contains(event.author)) // O usuário já pode estar banido
					return true

				BanCommand.ban(serverConfig, event.guild, event.guild.selfMember.user, locale, event.author, "Tentativa de Raid! (Isto é experimental, caso você tenha sido banido sem querer, vá em https://loritta.website/support e entre de novo :3)", false, 7)
				return true
			}

			messages.add(event.message)
		}

		if (automodCaps.isEnabled) {
			val content = message.contentRaw.replace(" ", "")
			val capsThreshold = automodCaps.capsThreshold

			val length = content.length.toDouble()
			if (length >= automodCaps.lengthThreshold) {
				val caps = content.count { it.isUpperCase() }.toDouble()

				val percentage = (caps / length) * 100

				if (percentage >= capsThreshold) {
					if (automodCaps.deleteMessage && event.guild!!.selfMember.hasPermission(event.textChannel!!, Permission.MESSAGE_MANAGE))
						message.delete().queue()

					if (automodCaps.replyToUser && message.textChannel.canTalk()) {
						message.channel.sendMessage(
								MessageUtils.generateMessage(automodCaps.replyMessage,
										listOf(event.guild!!, event.member!!),
										event.guild
								)
						).queue {
							if (automodCaps.enableMessageTimeout && it.guild.selfMember.hasPermission(event.textChannel, Permission.MESSAGE_MANAGE)) {
								val delay = Math.min(automodCaps.messageTimeout * 1000, 60000)
								it.delete().queueAfter(delay.toLong(), TimeUnit.MILLISECONDS)
							}
						}
					}

					return true
				}
			}
		}

		/* if (automodSelfEmbed.isEnabled) {
			if (message.embeds.isNotEmpty()) {
				if (automodSelfEmbed.deleteMessage)
					message.delete().queue()

				if (automodSelfEmbed.replyToUser) {
					val message = message.channel.sendMessage(MessageUtils.generateMessage(automodSelfEmbed.replyMessage, listOf(event.guild!!, event.member!!), event.guild)).complete()

					if (automodSelfEmbed.enableMessageTimeout) {
						var delay = Math.min(automodSelfEmbed.messageTimeout * 1000, 60000)
						Thread.sleep(delay.toLong())
						message.delete().queue()
					}
				}
			}
		} */
		return false
	}
}