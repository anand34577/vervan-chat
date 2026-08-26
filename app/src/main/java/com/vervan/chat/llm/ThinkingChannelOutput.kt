package com.vervan.chat.llm

/**
 * Converts LiteRT-LM's out-of-band response channels back into the tagged stream consumed by
 * [ThinkingParser]. LiteRT-LM emits reasoning chunks in Message.channels instead of Message.content
 * when the model metadata declares a thought channel.
 */
internal class ThinkingChannelOutput {
    private var activeChannel: String? = null

    fun append(channels: Map<String, String>, text: String): String {
        if (channels.isEmpty()) return appendText(text)

        val output = StringBuilder()
        channels.forEach { (channelName, channelText) ->
            if (channelText.isBlank()) return@forEach
            val normalized = channelName.trim().lowercase()
            if (!isThinkingChannel(normalized)) return@forEach
            if (activeChannel != normalized) {
                closeActiveInto(output)
                output.append("<|channel>thought\n")
                activeChannel = normalized
            }
            output.append(channelText)
        }
        if (text.isNotEmpty()) output.append(appendText(text))
        return output.toString()
    }

    fun finish(): String = buildString { closeActiveInto(this) }

    private fun appendText(text: String): String {
        if (text.isEmpty()) return ""
        return buildString {
            closeActiveInto(this)
            append(text)
        }
    }

    private fun closeActiveInto(output: Appendable) {
        if (activeChannel == null) return
        output.append("<channel|>")
        activeChannel = null
    }

    private fun isThinkingChannel(channelName: String): Boolean =
        channelName == "thought" ||
            channelName == "thoughts" ||
            channelName == "think" ||
            channelName == "thinking" ||
            channelName == "analysis" ||
            channelName == "reasoning" ||
            // LiteRT-LM channel names are model-declared. Accept future variants such as
            // `hidden_reasoning` or `chain_of_thought` without adding another model-specific
            // branch; ordinary answer channels do not contain these protocol markers.
            channelName.contains("thought") || channelName.contains("reason")
}
