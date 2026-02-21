package com.ericho.telegram.fetcher

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import java.time.LocalDate

private class TelegramFetcherCli : CliktCommand(name = "telegram-fetcher") {
    private val channel by option("--channel", help = "Target Telegram channel username, e.g. @xxx")
        .required()

    private val out by option("--out", help = "Output directory for exported data")
        .path(canBeFile = false, canBeDir = true)
        .required()

    private val tdlib by option("--tdlib", help = "TDLib session/cache directory")
        .path(canBeFile = false, canBeDir = true)
        .required()

    private val resume by option("--resume", help = "Resume from previous checkpoint")
        .flag(default = false)

    private val since by option("--since", help = "Fetch messages since date (YYYY-MM-DD)")
        .convert { LocalDate.parse(it) }

    private val maxMessages by option("--max-messages", help = "Maximum messages to fetch")
        .int()
        .default(50_000)

    private val downloadMedia by option("--download-media", help = "Whether to download message media")
        .default("false")
        .convert { it.equals("true", ignoreCase = true) }

    private val maxParallelDownload by option("--max-parallel-download", help = "Maximum concurrent media downloads")
        .int()
        .default(3)

    private val delayMs by option("--delay-ms", help = "Delay in milliseconds between fetch calls")
        .int()
        .default(300)

    override fun run() {
        echo(
            """
            Starting Telegram fetcher with:
            - channel: $channel
            - out: $out
            - tdlib: $tdlib
            - resume: $resume
            - since: ${since ?: "(not set)"}
            - maxMessages: $maxMessages
            - downloadMedia: $downloadMedia
            - maxParallelDownload: $maxParallelDownload
            - delayMs: $delayMs
            """.trimIndent(),
        )
    }
}

fun main(args: Array<String>) = TelegramFetcherCli().main(args)
