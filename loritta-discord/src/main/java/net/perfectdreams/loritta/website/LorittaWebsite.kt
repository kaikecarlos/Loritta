package net.perfectdreams.loritta.website

import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import com.mrpowergamerbr.loritta.utils.loritta
import com.mrpowergamerbr.loritta.website.WebsiteAPIException
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.http.content.staticRootFolder
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.request.uri
import io.ktor.request.userAgent
import io.ktor.response.respondRedirect
import io.ktor.routing.Routing
import io.ktor.routing.RoutingApplicationCall
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.util.AttributeKey
import io.ktor.util.hex
import mu.KotlinLogging
import net.perfectdreams.loritta.platform.discord.plugin.LorittaDiscordPlugin
import net.perfectdreams.loritta.website.blog.Blog
import net.perfectdreams.loritta.website.routes.LocalizedRoute
import net.perfectdreams.loritta.website.session.LorittaJsonWebSession
import net.perfectdreams.loritta.website.utils.ScriptingUtils
import net.perfectdreams.loritta.website.utils.extensions.respondHtml
import net.perfectdreams.loritta.website.utils.extensions.respondJson
import net.perfectdreams.loritta.website.utils.extensions.trueIp
import net.perfectdreams.loritta.website.utils.extensions.urlQueryString
import org.apache.commons.lang3.exception.ExceptionUtils
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.createType

/**
 * Clone of the original "LorittaWebsite" from the "sweet-morenitta" module
 *
 * This is used as a "hack" until the new website is done
 */
class LorittaWebsite(val loritta: Loritta) {
	companion object {
		lateinit var INSTANCE: LorittaWebsite
		val versionPrefix = "/v2"
		private val logger = KotlinLogging.logger {}
		private val TimeToProcess = AttributeKey<Long>("TimeToProcess")
	}

	val pathCache = ConcurrentHashMap<File, Any>()
	var config = WebsiteConfig()
	val blog = Blog()
	lateinit var server: CIOApplicationEngine

	fun start() {
		INSTANCE = this

		val routes = DefaultRoutes.defaultRoutes(loritta)

		val server = embeddedServer(CIO, loritta.instanceConfig.loritta.website.port) {
			this.environment.monitor.subscribe(Routing.RoutingCallStarted) { call: RoutingApplicationCall ->
				call.attributes.put(TimeToProcess, System.currentTimeMillis())
				val userAgent = call.request.userAgent()
				val trueIp = call.request.trueIp
				val queryString = call.request.urlQueryString
				val httpMethod = call.request.httpMethod.value

				logger.info("${trueIp} (${userAgent}): ${httpMethod} ${call.request.path()}${queryString}")

				/* if (loritta.config.loritta.website.blockedIps.contains(trueIp)) {
					logger.warn("$trueIp ($userAgent): ${httpMethod} ${call.request.path()}$queryString - Request was IP blocked")
					this.finish()
				}
				if (loritta.config.loritta.website.blockedUserAgents.contains(trueIp)) {
					logger.warn("$trueIp ($userAgent): ${httpMethod} ${call.request.path()}$queryString - Request was User-Agent blocked")
					this.finish()
				} */
			}

			this.environment.monitor.subscribe(Routing.RoutingCallFinished) { call: RoutingApplicationCall ->
				val originalStartTime = call.attributes[TimeToProcess]

				val queryString = call.request.urlQueryString
				val userAgent = call.request.userAgent()

				logger.info("${call.request.trueIp} (${userAgent}): ${call.request.httpMethod.value} ${call.request.path()}${queryString} - OK! ${System.currentTimeMillis() - originalStartTime}ms")
			}

			install(StatusPages) {
				status(HttpStatusCode.NotFound) {
					val html = ScriptingUtils.evaluateWebPageFromTemplate(
							File(
									"${INSTANCE.config.websiteFolder}/views/error_404.kts"
							),
							mapOf(
									"path" to call.request.path().split("/").drop(2).joinToString("/"),
									"websiteUrl" to INSTANCE.config.websiteUrl,
									"locale" to ScriptingUtils.WebsiteArgumentType(BaseLocale::class.createType(nullable = false), loritta.locales["default"]!!)
							)
					)

					call.respondHtml(html, HttpStatusCode.NotFound)
				}

				exception<WebsiteAPIException> { cause ->
					call.respondJson(cause.payload, HttpStatusCode.fromValue(cause.status.value()))
				}

				exception<Throwable> { cause ->
					val userAgent = call.request.userAgent()
					val trueIp = call.request.trueIp
					val queryString = call.request.urlQueryString
					val httpMethod = call.request.httpMethod.value

					logger.error(cause) { "Something went wrong when processing ${trueIp} (${userAgent}): ${httpMethod} ${call.request.path()}${queryString}" }

					call.respondHtml(
							"<pre>${ExceptionUtils.getStackTrace(cause)}</pre>"
					)
				}
			}

			install(Sessions) {
				val secretHashKey = hex("6819b57a326945c1968f45236589")

				cookie<LorittaJsonWebSession>("SESSION_FEATURE_SESSION") {
					cookie.path = "/"
					cookie.domain = "loritta.website"
					cookie.maxAgeInSeconds = 365L * 24 * 3600 // one year
					transform(SessionTransportTransformerMessageAuthentication(secretHashKey, "HmacSHA256"))
				}
			}

			routing {
				static {
					// staticRootFolder = File("/home/loritta_canary/test_website/static/assets/")
					staticRootFolder = File("${config.websiteFolder}/static/")
					files(".")
				}

				for (route in (routes + loritta.pluginManager.plugins.filterIsInstance<LorittaDiscordPlugin>().flatMap { it.routes })) {
					if (route is LocalizedRoute) {
						get(route.originalPath) {
							call.respondRedirect(config.websiteUrl + "/br${call.request.uri}")
						}
					}

					route.register(this)
				}
			}
		}
		this.server = server
		server.start(wait = true)
	}

	fun stop() {
		server.stop(0L, 0L)
	}

	fun restart() {
		stop()
		start()
	}

	class WebsiteConfig {
		val websiteUrl: String
			get() = loritta.instanceConfig.loritta.website.url.removeSuffix("/")
		val websiteFolder = File(loritta.instanceConfig.loritta.website.folder)
	}
}