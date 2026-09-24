package com.scrollmeter.app.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Spec §30, §31, PLAN Phase 7 DoD. */
class OnboardingFlowTest {
    private val nothing = OnboardingState(disclosureAccepted = false, serviceEnabled = false)

    @Test
    fun theStepsAreSpecOrderPlusTheOptionalTimeStep() {
        assertThat(OnboardingFlow.steps).containsExactly(
            OnboardingStep.WELCOME, OnboardingStep.HOW_IT_WORKS, OnboardingStep.DISCLOSURE,
            OnboardingStep.ENABLE_SERVICE, OnboardingStep.CALIBRATION, OnboardingStep.USAGE_ACCESS,
        ).inOrder()
        assertThat(OnboardingFlow.next(OnboardingStep.USAGE_ACCESS)).isNull()
        assertThat(OnboardingFlow.previous(OnboardingStep.WELCOME)).isNull()
        assertThat(OnboardingFlow.number(OnboardingStep.DISCLOSURE)).isEqualTo(3)
    }

    @Test
    fun theDisclosureCannotBeSkipped() {
        assertThat(OnboardingFlow.canLeave(OnboardingStep.DISCLOSURE, nothing)).isFalse()
        assertThat(OnboardingFlow.canLeave(OnboardingStep.DISCLOSURE, nothing.copy(disclosureAccepted = true))).isTrue()
    }

    /** "Onboarding nejde přeskočit před zapnutím služby" — accepted disclosure alone is not enough. */
    @Test
    fun theSetupCannotBeLeftWhileTheServiceIsOff() {
        val accepted = nothing.copy(disclosureAccepted = true)
        assertThat(OnboardingFlow.canLeave(OnboardingStep.ENABLE_SERVICE, accepted)).isFalse()
        assertThat(OnboardingFlow.canLeave(OnboardingStep.ENABLE_SERVICE, accepted.copy(serviceEnabled = true))).isTrue()
    }

    /** The onboarding finishes without Usage access, and calibration can always be skipped. */
    @Test
    fun calibrationAndTimeInAppAreOptional() {
        assertThat(OnboardingFlow.canLeave(OnboardingStep.CALIBRATION, nothing)).isTrue()
        assertThat(OnboardingFlow.canLeave(OnboardingStep.USAGE_ACCESS, nothing)).isTrue()
    }

    @Test
    fun accessibilitySettingsOpenOnlyAfterTheDisclosure() {
        assertThat(AccessibilityGate.route(disclosureAccepted = false)).isEqualTo(AccessibilityGate.Route.SHOW_DISCLOSURE)
        assertThat(AccessibilityGate.route(disclosureAccepted = true)).isEqualTo(AccessibilityGate.Route.OPEN_SETTINGS)
    }
}
