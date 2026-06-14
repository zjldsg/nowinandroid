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

package com.google.samples.apps.nowinandroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.samples.apps.nowinandroid.feature.bookmarks.api.R as BookmarksR
import com.google.samples.apps.nowinandroid.feature.foryou.api.R as ForYouR
import com.google.samples.apps.nowinandroid.feature.interests.api.R as InterestsR
import com.google.samples.apps.nowinandroid.feature.search.api.R as SearchR
import com.google.samples.apps.nowinandroid.feature.settings.impl.R as SettingsR
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end navigation integration tests for the Now in Android app.
 * Tests the complete user flow across multiple screens.
 */
class EndToEndNavigationIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Test: Navigation to Interests screen via bottom nav
     *
     * Flow: For You -> Navigate to Interests -> View topics list
     */
    @Test
    fun navigateToInterestsScreen() {
        // Navigate to Interests using bottom nav label "Interests"
        val interestsLabel = composeTestRule.activity.getString(
            InterestsR.string.feature_interests_api_title,
        )

        composeTestRule
            .onNodeWithContentDescription(interestsLabel)
            .assertIsDisplayed()
            .performClick()

        // Verify Interests screen shows topics section
        val selectInterest = composeTestRule.activity.getString(
            InterestsR.string.feature_interests_api_select_an_interest,
        )

        composeTestRule
            .onNodeWithText(selectInterest)
            .assertIsDisplayed()
    }

    /**
     * Test: Navigation to Bookmarks screen via bottom nav
     *
     * Flow: For You -> Navigate to Bookmarks -> View bookmarked items
     */
    @Test
    fun navigateToBookmarksScreen() {
        // Navigate to Bookmarks using bottom nav label "Saved"
        val savedLabel = composeTestRule.activity.getString(
            BookmarksR.string.feature_bookmarks_api_title,
        )

        composeTestRule
            .onNodeWithContentDescription(savedLabel)
            .assertIsDisplayed()
            .performClick()

        // Verify Bookmarks screen content - shows loading or empty state
        val bookmarksTitle = composeTestRule.activity.getString(
            BookmarksR.string.feature_bookmarks_api_title,
        )

        composeTestRule
            .onNodeWithText(bookmarksTitle)
            .assertIsDisplayed()
    }

    /**
     * Test: Navigation to Search screen via bottom nav
     *
     * Flow: For You -> Navigate to Search -> View search screen
     */
    @Test
    fun navigateToSearchScreen() {
        // Navigate to Search using bottom nav label "Search"
        val searchLabel = composeTestRule.activity.getString(
            SearchR.string.feature_search_api_title,
        )

        composeTestRule
            .onNodeWithContentDescription(searchLabel)
            .assertIsDisplayed()
            .performClick()

        // Verify Search screen is visible - shows search title
        val searchTitle = composeTestRule.activity.getString(
            SearchR.string.feature_search_api_title,
        )

        composeTestRule
            .onNodeWithText(searchTitle)
            .assertIsDisplayed()
    }

    /**
     * Test: Navigation to Settings dialog
     *
     * Flow: For You -> Open Settings dialog
     */
    @Test
    fun navigateToSettingsDialog() {
        // Open Settings via top app bar action
        val settingsLabel = composeTestRule.activity.getString(
            SettingsR.string.feature_settings_impl_top_app_bar_action_icon_description,
        )

        composeTestRule
            .onNodeWithContentDescription(settingsLabel)
            .assertIsDisplayed()
            .performClick()

        // Verify Settings dialog title is shown
        val settingsTitle = composeTestRule.activity.getString(
            SettingsR.string.feature_settings_impl_title,
        )

        composeTestRule
            .onNodeWithText(settingsTitle)
            .assertIsDisplayed()
    }
}
