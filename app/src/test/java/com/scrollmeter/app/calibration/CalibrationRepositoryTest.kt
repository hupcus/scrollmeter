package com.scrollmeter.app.calibration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.measurement.TestPhone
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** DataStore Preferences on the JVM (a real file in a temp folder): spec §17, §65, ADR-024. */
class CalibrationRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "calibration.preferences_pb") })
    }
    private val repository by lazy { CalibrationRepository(dataStore) }
    private val card = CardCalibration.create(1360, TestPhone.snapshot, nowMs = 1_790_000_000_000L)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun startsWithoutCalibrationAtVersionZero() = runBlocking {
        assertThat(repository.state.first()).isEqualTo(CalibrationState.NONE)
    }

    @Test
    fun everySaveRoundTripsAllFieldsAndBumpsTheVersion() = runBlocking {
        repository.saveManual(card)
        assertThat(repository.state.first()).isEqualTo(CalibrationState(1, card))

        val again = CardCalibration.create(1352, TestPhone.snapshot, nowMs = 1_790_000_100_000L)
        repository.saveManual(again)
        assertThat(repository.state.first()).isEqualTo(CalibrationState(2, again))
    }

    @Test
    fun useAutomaticForgetsTheCardAndCountsAsOneChange() = runBlocking {
        repository.useAutomatic()
        assertThat(repository.state.first()).isEqualTo(CalibrationState.NONE)

        repository.saveManual(card)
        repository.useAutomatic()
        assertThat(repository.state.first()).isEqualTo(CalibrationState(2, null))

        repository.useAutomatic()
        assertThat(repository.state.first().version).isEqualTo(2)
    }

    @Test
    fun anImplausibleStoredScaleReadsAsNoCalibration() = runBlocking {
        repository.saveManual(card)
        dataStore.edit { it[CalibrationRepository.MM_PER_PX_Y] = 5.0 }
        assertThat(repository.state.first()).isEqualTo(CalibrationState(1, null))
    }

    @Test
    fun aMissingFieldReadsAsNoCalibration() = runBlocking {
        repository.saveManual(card)
        dataStore.edit { it.remove(CalibrationRepository.MODEL) }
        assertThat(repository.state.first()).isEqualTo(CalibrationState(1, null))
    }
}
