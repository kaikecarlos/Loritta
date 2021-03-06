package net.perfectdreams.loritta.parallax.wrapper

import com.google.gson.annotations.SerializedName
import java.awt.Color

class ParallaxEmbed {
	private var rgb: ParallaxColor? = null
	private var color: Int? = null
	private var hex: String? = null
	private var title: String? = null
	private var url: String?  = null
	private var description: String?  = null
	private var author: ParallaxEmbedAuthor?  = null
	private var thumbnail: ParallaxEmbedImage?  = null
	private var image: ParallaxEmbedImage? = null
	private var footer: ParallaxEmbedFooter? = null
	private var fields: MutableList<ParallaxEmbedField>? = null

	@JvmOverloads
	fun addBlankField(inline: Boolean = false): ParallaxEmbed {
		if (fields == null)
			fields = mutableListOf()
		fields!!.add(ParallaxEmbedField(" ", " ", inline))
		return this
	}

	@JvmOverloads
	fun addField(name: String, value: String, inline: Boolean = false): ParallaxEmbed {
		if (fields == null)
			fields = mutableListOf()
		fields!!.add(ParallaxEmbedField(name, value, inline))
		return this
	}

	// TODO: attachFile
	// TODO: attachFiles

	@JvmOverloads
	fun setAuthor(name: String, icon: String? = null, url: String? = null): ParallaxEmbed {
		author = ParallaxEmbedAuthor(name, icon, url)
		return this
	}

	fun setColor(color: Int): ParallaxEmbed {
		this.color = color
		return this
	}

	fun setDescription(description: String): ParallaxEmbed {
		this.description = description
		return this
	}

	@JvmOverloads
	fun setFooter(text: String, icon: String? = null): ParallaxEmbed {
		this.footer = ParallaxEmbedFooter(text, icon)
		return this
	}

	fun setImage(url: String): ParallaxEmbed {
		this.image = ParallaxEmbedImage(url)
		return this
	}

	fun setThumbnail(url: String): ParallaxEmbed {
		this.thumbnail = ParallaxEmbedImage(url)
		return this
	}

	// TODO: setTimestamp

	fun setTitle(title: String): ParallaxEmbed {
		this.title = title
		return this
	}

	fun setURL(url: String): ParallaxEmbed {
		this.url = url
		return this
	}

	class ParallaxEmbedAuthor(
			var name: String?,
			var url: String?,
			@SerializedName("icon_url")
			var iconUrl: String?
	)

	class ParallaxEmbedImage(
			var url: String?
	)

	class ParallaxEmbedFooter(
			var text: String?,
			@SerializedName("icon_url")
			var iconUrl: String?
	)

	class ParallaxEmbedField(
			var name: String?,
			var value: String?,
			var inline: Boolean = false
	)
}