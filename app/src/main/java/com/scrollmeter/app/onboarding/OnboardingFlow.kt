package com.scrollmeter.app.onboarding

/**
 * The onboarding (spec §31, PLAN Phase 7, ADR-032): five steps, then the optional time-in-app step
 * (D19). Pure Kotlin — the screen asks it what may happen, so the rules run as JVM tests.
 */
enum class OnboardingStep { WELCOME, HOW_IT_WORKS, DISCLOSURE, ENABLE_SERVICE, CALIBRATION, USAGE_ACCESS }

/** What the onboarding knows when a step is to be left forward. */
data class OnboardingState(val disclosureAccepted: Boolean, val serviceEnabled: Boolean)

object OnboardingFlow {
    val steps: List<OnboardingStep> = OnboardingStep.entries

    /**
     * PLAN Phase 7 DoD: nothing past the prominent disclosure without "Rozumím a chci pokračovat"
     * (spec §30), nothing past the setup while the service is switched off. Calibration and time in
     * app always let the user go on — the onboarding finishes without Usage access.
     */
    fun canLeave(step: OnboardingStep, state: OnboardingState): Boolean = when (step) {
        OnboardingStep.DISCLOSURE -> state.disclosureAccepted
        OnboardingStep.ENABLE_SERVICE -> state.serviceEnabled
        else -> true
    }

    /** The step after [step], or null when [step] is the last one (the onboarding is done). */
    fun next(step: OnboardingStep): OnboardingStep? = steps.getOrNull(step.ordinal + 1)

    /** The step "Zpět" returns to, or null on the first one (leaving the app). */
    fun previous(step: OnboardingStep): OnboardingStep? = steps.getOrNull(step.ordinal - 1)

    /** "Krok 3 z 6". */
    fun number(step: OnboardingStep): Int = step.ordinal + 1
}

/**
 * Spec §30, Google Play User Data policy: Android's accessibility settings open only after the
 * prominent disclosure was accepted — from the onboarding, the dashboard banner or Nastavení alike.
 */
object AccessibilityGate {
    enum class Route { OPEN_SETTINGS, SHOW_DISCLOSURE }

    fun route(disclosureAccepted: Boolean): Route = if (disclosureAccepted) Route.OPEN_SETTINGS else Route.SHOW_DISCLOSURE
}
