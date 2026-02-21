package com.ericho.telegram.fetcher

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.time.LocalDate
import java.util.Scanner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private class TelegramFetcherCli : CliktCommand(name = "telegram-fetcher") {
    private val channel by option("--channel", help = "Target Telegram channel username, e.g. @xxx")
        .required()

    private val out by option("--out", help = "Output directory for exported data")
        .path(canBeFile = false, canBeDir = true)
        .required()

    private val tdlib by option("--tdlib", help = "TDLib session/cache directory")
        .path(canBeFile = false, canBeDir = true)
        .required()

    private val apiId by option("--api-id", help = "Telegram API ID")
        .int()
        .required()

    private val apiHash by option("--api-hash", help = "Telegram API hash")
        .required()

    private val phone by option("--phone", help = "Phone number used for Telegram login (E.164 format)")

    private val authTimeoutSec by option("--auth-timeout-sec", help = "Timeout in seconds to finish login")
        .int()
        .default(300)

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

        val authenticated = TdLibAuthenticator(
            tdlibDir = tdlib.toString(),
            apiId = apiId,
            apiHash = apiHash,
            phoneNumber = phone,
        ).authenticate(timeoutSec = authTimeoutSec)

        if (!authenticated) {
            fail("TDLib authentication was not completed")
        }

        echo("TDLib auth completed, ready to continue with history fetching.")
    }
}

private class TdLibAuthenticator(
    private val tdlibDir: String,
    private val apiId: Int,
    private val apiHash: String,
    private val phoneNumber: String?,
) {
    private val input = Scanner(System.`in`)
    private val authDone = CountDownLatch(1)
    private val authSuccess = AtomicBoolean(false)
    private lateinit var client: Client

    private val updateHandler = Client.ResultHandler { update ->
        if (update is TdApi.UpdateAuthorizationState) {
            onAuthorizationStateUpdated(update.authorizationState)
        }
    }

    private val defaultHandler = Client.ResultHandler { result ->
        if (result is TdApi.Error) {
            println("TDLib error ${result.code}: ${result.message}")
        }
    }

    fun authenticate(timeoutSec: Int): Boolean {
        client = Client.create(
            updateHandler,
            Client.ExceptionHandler { throwable -> throwable.printStackTrace() },
            Client.ExceptionHandler { throwable -> throwable.printStackTrace() },
        )

        Client.execute(TdApi.SetLogVerbosityLevel(1))
        client.send(TdApi.GetAuthorizationState(), defaultHandler)

        val finished = authDone.await(timeoutSec.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            println("Timed out waiting for TDLib authorization flow to complete")
        }
        client.send(TdApi.Close(), defaultHandler)
        return finished && authSuccess.get()
    }

    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val parameters = TdApi.SetTdlibParameters().apply {
                    databaseDirectory = "$tdlibDir/db"
                    filesDirectory = "$tdlibDir/files"
                    useMessageDatabase = true
                    useSecretChats = false
                    useChatInfoDatabase = true
                    useFileDatabase = true
                    apiId = this@TdLibAuthenticator.apiId
                    apiHash = this@TdLibAuthenticator.apiHash
                    systemLanguageCode = "en"
                    deviceModel = "telegram-kt-cli"
                    applicationVersion = "0.1.0"
                    enableStorageOptimizer = true
                }
                client.send(parameters, defaultHandler)
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                val number = phoneNumber ?: prompt("Enter your phone number (+<country_code><number>): ")
                client.send(TdApi.SetAuthenticationPhoneNumber(number, null), defaultHandler)
            }

            is TdApi.AuthorizationStateWaitCode -> {
                val code = prompt("Enter the login code: ")
                client.send(TdApi.CheckAuthenticationCode(code), defaultHandler)
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                val password = prompt("Enter your 2FA password: ")
                client.send(TdApi.CheckAuthenticationPassword(password), defaultHandler)
            }

            is TdApi.AuthorizationStateReady -> {
                authSuccess.set(true)
                authDone.countDown()
            }

            is TdApi.AuthorizationStateClosed -> {
                authDone.countDown()
            }

            else -> Unit
        }
    }

    private fun prompt(message: String): String {
        print(message)
        return input.nextLine().trim()
    }
}

fun main(args: Array<String>) = TelegramFetcherCli().main(args)
