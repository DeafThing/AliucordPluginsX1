package com.xinto.aliuplugins

import android.content.Context
import com.aliucord.Constants
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.patcher.InsteadHook
import com.xinto.aliuplugins.nitrospoof.EMOTE_SIZE_DEFAULT
import com.xinto.aliuplugins.nitrospoof.EMOTE_SIZE_KEY
import com.xinto.aliuplugins.nitrospoof.HYPERLINK_ENABLED_KEY
import com.xinto.aliuplugins.nitrospoof.HYPERLINK_ENABLED_DEFAULT
import com.xinto.aliuplugins.nitrospoof.PluginSettings
import com.discord.models.domain.emoji.ModelEmojiCustom
import com.discord.stores.StoreExperiments
import com.discord.stores.StoreStream
import de.robv.android.xposed.XC_MethodHook
import java.io.File
import java.lang.reflect.Field

@AliucordPlugin
class NitroSpoof : Plugin() {

    private val reflectionCache = HashMap<String, Field>()

    override fun start(context: Context) {
        // Enable emoji autocomplete upsell experiment if not already set
        val experiments = StoreStream.getExperiments()
        val overrides = StoreExperiments.`access$getExperimentOverrides$p`(experiments)
        val emojiAutocompleteKey = "2021-03_nitro_emoji_autocomplete_upsell_android"
        
        if (!overrides.containsKey(emojiAutocompleteKey)) {
            experiments.setOverride(emojiAutocompleteKey, 1)
        }

        patcher.patch(
            ModelEmojiCustom::class.java.getDeclaredMethod("getChatInputText"),
            Hook { getChatReplacement(it) }
        )
        patcher.patch(
            ModelEmojiCustom::class.java.getDeclaredMethod("getMessageContentReplacement"),
            Hook { getChatReplacement(it) }
        )
        patcher.patch(
            ModelEmojiCustom::class.java.getDeclaredMethod("isUsable"),
            InsteadHook { true }
        )
        patcher.patch(
            ModelEmojiCustom::class.java.getDeclaredMethod("isAvailable"),
            InsteadHook { true }
        )
        commands.registerCommand("freenitroll", "Delete NitroSpoof plugin") {
            try {
                File(Constants.PLUGINS_PATH, "NitroSpoof.zip").delete()
                CommandsAPI.CommandResult("NitroSpoof plugin deleted successfully.", null, false)
            } catch (e: Throwable) {
                CommandsAPI.CommandResult("Failed to delete NitroSpoof plugin: ${e.message}", null, false)
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private fun getChatReplacement(callFrame: XC_MethodHook.MethodHookParam) {
        val thisObject = callFrame.thisObject as ModelEmojiCustom
        val isUsable = thisObject.getCachedField<Boolean>("isUsable")
        val available = thisObject.getCachedField<Boolean>("available")

        if (isUsable && available) {
            callFrame.result = callFrame.result
            return
        }

        var finalUrl = "https://cdn.discordapp.com/emojis/"

        val idStr = thisObject.getCachedField<String>("idStr")
        val isAnimated = thisObject.getCachedField<Boolean>("isAnimated")
        val emoteName = thisObject.getCachedField<String>("name")

        finalUrl += idStr
        val emoteSize = settings.getString(EMOTE_SIZE_KEY, EMOTE_SIZE_DEFAULT).toIntOrNull()

        finalUrl += (if (isAnimated) ".gif" else ".png") + "?quality=lossless&name=" + emoteName

        if (emoteSize != null) {
            finalUrl += "&size=${emoteSize}"
        }
        
        // Check if hyperlink functionality is enabled
        val hyperlinkEnabled = settings.getBool(HYPERLINK_ENABLED_KEY, HYPERLINK_ENABLED_DEFAULT)
        
        callFrame.result = if (hyperlinkEnabled) {
            "[$emoteName]($finalUrl)"
        } else {
            finalUrl
        }
    }

    /**
     * Get a reflected field from cache or compute it if cache is absent
     * @param V type of the field value
     */
    private inline fun <reified V> Any.getCachedField(
        name: String,
        instance: Any? = this,
    ): V {
        val clazz = this::class.java
        return reflectionCache.computeIfAbsent(clazz.name + name) {
            clazz.getDeclaredField(name).also {
                it.isAccessible = true
            }
        }.get(instance) as V
    }

    init {
        settingsTab = SettingsTab(
            PluginSettings::class.java,
            SettingsTab.Type.PAGE
        ).withArgs(settings)
    }
}