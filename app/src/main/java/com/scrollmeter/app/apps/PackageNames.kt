package com.scrollmeter.app.apps

/** Android package names: two or more dot-separated segments of letters, digits and "_", each starting with a letter. */
object PackageNames {
    private val PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private const val MAX_LENGTH = 255

    fun isValid(name: String): Boolean = name.length <= MAX_LENGTH && PATTERN.matches(name)
}
