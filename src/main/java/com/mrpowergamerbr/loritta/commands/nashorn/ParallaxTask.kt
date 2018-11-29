package com.mrpowergamerbr.loritta.commands.nashorn

import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.parallax.wrappers.ParallaxContext
import com.mrpowergamerbr.loritta.utils.loritta
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.core.EmbedBuilder
import org.apache.commons.lang3.exception.ExceptionUtils
import org.graalvm.polyglot.Context
import java.awt.Color
import java.lang.management.ManagementFactory
import java.util.concurrent.Callable

internal class ParallaxTask(var graalContext: Context, var javaScript: String, var ogContext: CommandContext, var context: ParallaxContext) : Callable<Void> {
	var running = true
	var autoKill = 0

	init {
		running = true
	}

	@Throws(Exception::class)
	override fun call(): Void? {
		val sunBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
		val id = Thread.currentThread().id
		val currentThread = Thread.currentThread()
		try {
			val t = object : Thread() {
				override fun run() {
					while (running) {
						println("bytes: " + sunBean.getThreadAllocatedBytes(id))
						autoKill++
						if (sunBean.getThreadAllocatedBytes(id) > 227402240 || autoKill > 600) {
							println("!!! Matando thread")
							running = false
							currentThread.stop() // stop now!
						}
						try {
							Thread.sleep(25)
						} catch (e: Exception) {
						}

					}
					return
				}
			}
			t.start()

			val value = graalContext.eval("js", javaScript)
			value.execute(context)
		} catch (e: Exception) {
			e.printStackTrace()
			val builder = EmbedBuilder()
			builder.setTitle("❌ Ih Serjão Sujou! 🤦", "https://youtu.be/G2u8QGY25eU")
			builder.setDescription("```" + (if (e.cause != null)
				e.cause!!.message!!.trim { it <= ' ' }
			else
				ExceptionUtils.getStackTrace(e)
						.substring(0, Math.min(2000, ExceptionUtils.getStackTrace(e).length))) + "```")
			builder.setFooter(
					"Aprender a programar seria bom antes de me forçar a executar códigos que não funcionam 😢", null)
			builder.setColor(Color.RED)
			GlobalScope.launch(loritta.coroutineDispatcher) {
				ogContext.sendMessage(builder.build())
			}
		}

		running = false
		return null
	}
}