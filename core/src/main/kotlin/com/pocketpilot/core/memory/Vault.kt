package com.pocketpilot.core.memory

/**
 * The vault: logins, cards, API keys the user saved.
 *
 * HARD RULE (carried over from the phone-agent pattern): a secret value
 * NEVER enters the LLM context, NEVER appears in a tool result, and is
 * NEVER logged. The only operation is `type(label)`, which the app module
 * implements by typing the value locally into the focused field.
 * The storage itself (AES/GCM, Keystore key) lives in the app module.
 */
interface Vault {
    /** Labels the user saved, e.g. ["gmail", "bank"]. Values are never listed. */
    fun labels(): List<String>

    /** True when a secret exists under this label. */
    fun has(label: String): Boolean

    /** Store a secret. Implementations must encrypt it before persisting. */
    fun put(label: String, secret: String)

    /** Delete a secret. */
    fun remove(label: String)

    /**
     * Type the secret into the currently focused field. Implemented in the
     * app module via the accessibility service. Returns false when the
     * label does not exist or no field is focused. The secret is never
     * returned to the caller.
     */
    fun typeIntoFocusedField(label: String): Boolean
}

/** In-memory vault for tests and the JVM demo. NOT for production use. */
class InMemoryVault : Vault {
    private val store = mutableMapOf<String, String>()
    override fun labels(): List<String> = store.keys.toList()
    override fun has(label: String): Boolean = store.containsKey(label)
    override fun put(label: String, secret: String) { store[label] = secret }
    override fun remove(label: String) { store.remove(label) }
    override fun typeIntoFocusedField(label: String): Boolean = store.containsKey(label)
}
