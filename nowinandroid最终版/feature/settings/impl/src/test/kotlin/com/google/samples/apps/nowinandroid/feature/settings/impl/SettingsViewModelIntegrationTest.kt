/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.nowinandroid.feature.settings.impl

import com.google.samples.apps.nowinandroid.core.model.data.DarkThemeConfig
import com.google.samples.apps.nowinandroid.core.model.data.ThemeBrand
import com.google.samples.apps.nowinandroid.core.model.data.UserData
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [SettingsViewModel] testing theme settings
 * changes and their effect on user preferences.
 *
 * Uses backgroundScope + collect pattern because SettingsViewModel uses
 * stateIn(WhileSubscribed) which cancels upstream on subscriber loss.
 */
class SettingsViewModelIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val userDataRepository = TestUserDataRepository()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows current user theme settings`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }

        advanceUntilIdle()

        val state = (viewModel.settingsUiState.value as SettingsUiState.Success).settings
        assertEquals(ThemeBrand.DEFAULT, state.brand)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, state.darkThemeConfig)
        assertFalse(state.useDynamicColor)
    }

    @Test
    fun `changing theme brand updates user preferences`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }

        viewModel.updateThemeBrand(ThemeBrand.DEFAULT)
        advanceUntilIdle()

        var userData = userDataRepository.userData.first()
        assertEquals(ThemeBrand.DEFAULT, userData.themeBrand)

        viewModel.updateThemeBrand(ThemeBrand.ANDROID)
        advanceUntilIdle()

        userData = userDataRepository.userData.first()
        assertEquals(ThemeBrand.ANDROID, userData.themeBrand)
    }

    @Test
    fun `changing dark theme config updates user preferences`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }

        viewModel.updateDarkThemeConfig(DarkThemeConfig.LIGHT)
        advanceUntilIdle()

        var userData = userDataRepository.userData.first()
        assertEquals(DarkThemeConfig.LIGHT, userData.darkThemeConfig)

        viewModel.updateDarkThemeConfig(DarkThemeConfig.DARK)
        advanceUntilIdle()

        userData = userDataRepository.userData.first()
        assertEquals(DarkThemeConfig.DARK, userData.darkThemeConfig)

        viewModel.updateDarkThemeConfig(DarkThemeConfig.FOLLOW_SYSTEM)
        advanceUntilIdle()

        userData = userDataRepository.userData.first()
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, userData.darkThemeConfig)
    }

    @Test
    fun `toggling dynamic color updates user preferences`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }

        viewModel.updateDynamicColorPreference(true)
        advanceUntilIdle()

        var userData = userDataRepository.userData.first()
        assertTrue(userData.useDynamicColor)

        viewModel.updateDynamicColorPreference(false)
        advanceUntilIdle()

        userData = userDataRepository.userData.first()
        assertFalse(userData.useDynamicColor)
    }

    @Test
    fun `complete theme customization journey works end to end`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }

        advanceUntilIdle()

        var state = (viewModel.settingsUiState.value as SettingsUiState.Success).settings
        assertEquals(ThemeBrand.DEFAULT, state.brand)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, state.darkThemeConfig)
        assertFalse(state.useDynamicColor)

        viewModel.updateThemeBrand(ThemeBrand.ANDROID)
        advanceUntilIdle()

        state = (viewModel.settingsUiState.value as SettingsUiState.Success).settings
        assertEquals(ThemeBrand.ANDROID, state.brand)

        viewModel.updateDarkThemeConfig(DarkThemeConfig.DARK)
        advanceUntilIdle()

        state = (viewModel.settingsUiState.value as SettingsUiState.Success).settings
        assertEquals(DarkThemeConfig.DARK, state.darkThemeConfig)

        viewModel.updateDynamicColorPreference(true)
        advanceUntilIdle()

        state = (viewModel.settingsUiState.value as SettingsUiState.Success).settings
        assertTrue(state.useDynamicColor)

        val userData = userDataRepository.userData.first()
        assertEquals(ThemeBrand.ANDROID, userData.themeBrand)
        assertEquals(DarkThemeConfig.DARK, userData.darkThemeConfig)
        assertTrue(userData.useDynamicColor)
    }

    @Test
    fun `ui state reflects user data changes`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }

        advanceUntilIdle()

        val initialUserData = userDataRepository.userData.first()
        var state = (viewModel.settingsUiState.value as SettingsUiState.Success).settings

        assertEquals(initialUserData.themeBrand, state.brand)
        assertEquals(initialUserData.darkThemeConfig, state.darkThemeConfig)
        assertEquals(initialUserData.useDynamicColor, state.useDynamicColor)

        val newUserData = UserData(
            bookmarkedNewsResources = emptySet(),
            viewedNewsResources = emptySet(),
            followedTopics = emptySet(),
            themeBrand = ThemeBrand.ANDROID,
            darkThemeConfig = DarkThemeConfig.DARK,
            useDynamicColor = true,
            shouldHideOnboarding = true,
        )
        userDataRepository.setUserData(newUserData)
        advanceUntilIdle()

        state = (viewModel.settingsUiState.value as SettingsUiState.Success).settings
        assertEquals(ThemeBrand.ANDROID, state.brand)
        assertEquals(DarkThemeConfig.DARK, state.darkThemeConfig)
        assertTrue(state.useDynamicColor)
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            userDataRepository = userDataRepository,
        )
    }
}
