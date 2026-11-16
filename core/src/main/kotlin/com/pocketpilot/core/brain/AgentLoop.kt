package com.pocketpilot.core.brain

import com.pocketpilot.core.config.BrainConfig
import com.pocketpilot.core.tools.ToolRegistry
import com.pocketpilot.core.tools.ToolSchemas
import org.json.JSONArray
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Outcome of one user request, for the UI and the run log. */
data class RunOutcome(
    val summary: String,
    val stepsTaken: Int,
    val askedUser: Boolean,
    val error: String? = null,
)

/** A question the loop needs the user to answer (irreversible action, or stuck). */
data class UserQuestion(val question: String)

/**
 * The agent loop: plan -> act -> observe -> repeat, on ONE thread.
 * Tool calls run one at a time, each deadline-bounded; screenshots are
 * rationed; the loop stops on `finish`, on user answer, on step cap, or
 * on repeated failure. Nothing here imports Android — the loop only talks
 * to ModelProvider, ToolRegistry and the callbacks below.
 */
class AgentLoop(
    private val config: BrainConfig,
    private val model: ModelProvider,
    private val tools: ToolRegistry,
    private val pruner: ContextPruner = ContextPruner(),
    /**
     * Called when the agent needs the user (ask_user tool, or irreversible
     * action). Return the user's answer, or null to abort.
     */
    private val askUser: (UserQuestion) -> String? = { null },
    /** Called after every step with a human-readable line, for the UI log. */
    private val onStep: (String) -> Unit = {},
    /** Called with a screenshot's bytes when the model asked for one. */
    private val onScreenshot: (ByteArray) -> Unit = {},
) {
    private val executor = Executors.newSingleThreadExecutor()

    fun run(userRequest: String, systemPrompt: String): RunOutcome {
        val messages = JSONArray()
            .put(Messages.system(systemPrompt))
            .put(Messages.user(userRequest))
        val openAiTools = tools.toOpenAiTools()
        var steps = 0
        var consecutiveFailures = 0

        while (steps < config.maxSteps) {
            steps++
            val reply = try {
                callWithDeadline({ model.complete(pruner.prune(messages), openAiTools) })
            } catch (e: Exception) {
                return RunOutcome("Model error: ${e.message}", steps, askedUser = false, error = e.message)
            }

            messages.put(Messages.assistant(reply.text, reply.toolCalls))
            if (reply.text != null) onStep("Agent: ${reply.text}")

            if (reply.toolCalls.isEmpty()) {
                // No tool call = the model answered in text. Done.
                return RunOutcome(reply.text ?: "Done.", steps, askedUser = false)
            }

            for ((name, args) in reply.toolCalls) {
                if (ToolSchemas.isRefused(name, args)) {
                    messages.put(Messages.toolResult("call_refused", "Refused: tapping there is not allowed."))
                    onStep("Refused unsafe tool call: $name")
                    continue
                }
                val definition = tools.definition(name)
                if (definition == null) {
                    messages.put(Messages.toolResult("call_unknown", "Unknown tool: $name"))
                    continue
                }
                if (name == "finish") {
                    val summary = args.optString("summary", "Done.")
                    return RunOutcome(summary, steps, askedUser = false)
                }
                if (name == "ask_user") {
                    val answer = askUser(UserQuestion(args.optString("question", "Continue?")))
                        ?: return RunOutcome("Stopped — waiting for the user.", steps, askedUser = true)
                    messages.put(Messages.toolResult("call_ask", "User answered: $answer"))
                    continue
                }
                if (definition.irreversible && config.confirmIrreversible) {
                    val answer = askUser(UserQuestion("Do this irreversible action: $name ${args}? (yes/no)"))
                    if (answer?.trim()?.lowercase() !in setOf("yes", "y", "ok")) {
                        messages.put(Messages.toolResult("call_denied", "User declined the irreversible action."))
                        continue
                    }
                }

                val result = try {
                    callWithDeadline({ tools.call(name, args) }, config.toolTimeoutSeconds)
                } catch (e: TimeoutException) {
                    com.pocketpilot.core.tools.ToolResult("Tool $name timed out after ${config.toolTimeoutSeconds}s.", isError = true)
                } catch (e: Exception) {
                    com.pocketpilot.core.tools.ToolResult("Tool $name failed: ${e.message}", isError = true)
                }

                if (result.isError) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        return RunOutcome(
                            "Stopped after 3 failed steps (last: ${result.text}).",
                            steps, askedUser = false, error = result.text,
                        )
                    }
                } else {
                    consecutiveFailures = 0
                }
                result.attachment?.let { onScreenshot(it) }
                messages.put(Messages.toolResult("call_${name}_$steps", result.text))
                onStep("Tool $name -> ${result.text.take(160)}")
            }
        }
        return RunOutcome("Stopped at the step limit (${config.maxSteps}). Task may be unfinished.", steps, askedUser = false)
    }

    private fun <T> callWithDeadline(block: () -> T, timeoutSeconds: Long = 180): T {
        val future = executor.submit(Callable(block))
        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw e
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
