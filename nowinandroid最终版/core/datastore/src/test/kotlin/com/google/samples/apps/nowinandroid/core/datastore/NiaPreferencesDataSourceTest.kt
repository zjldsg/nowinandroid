/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.samples.apps.nowinandroid.core.datastore

import com.google.samples.apps.nowinandroid.core.datastore.test.InMemoryDataStore
import com.google.samples.apps.nowinandroid.core.model.data.DarkThemeConfig
import com.google.samples.apps.nowinandroid.core.model.data.ThemeBrand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NiaPreferencesDataSourceTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var subject: NiaPreferencesDataSource

    @Before
    fun setup() {
        subject = NiaPreferencesDataSource(InMemoryDataStore(UserPreferences.getDefaultInstance()))
    }

    @Test
    fun shouldHideOnboardingIsFalseByDefault() = testScope.runTest {
        assertFalse(subject.userData.first().shouldHideOnboarding)
    }

    @Test
    fun userShouldHideOnboardingIsTrueWhenSet() = testScope.runTest {
        subject.setShouldHideOnboarding(true)
        assertTrue(subject.userData.first().shouldHideOnboarding)
    }

    @Test
    fun userShouldHideOnboarding_unfollowsLastTopic_shouldHideOnboardingIsFalse() =
        testScope.runTest {
            // Given: user completes onboarding by selecting a single topic.
            subject.setTopicIdFollowed("1", true)
            subject.setShouldHideOnboarding(true)

            // When: they unfollow that topic.
            subject.setTopicIdFollowed("1", false)

            // Then: onboarding should be shown again
            assertFalse(subject.userData.first().shouldHideOnboarding)
        }

    @Test
    fun userShouldHideOnboarding_unfollowsAllTopics_shouldHideOnboardingIsFalse() =
        testScope.runTest {
            // Given: user completes onboarding by selecting several topics.
            subject.setFollowedTopicIds(setOf("1", "2"))
            subject.setShouldHideOnboarding(true)

            // When: they unfollow those topics.
            subject.setFollowedTopicIds(emptySet())

            // Then: onboarding should be shown again
            assertFalse(subject.userData.first().shouldHideOnboarding)
        }

    @Test
    fun shouldUseDynamicColorFalseByDefault() = testScope.runTest {
        assertFalse(subject.userData.first().useDynamicColor)
    }

    @Test
    fun userShouldUseDynamicColorIsTrueWhenSet() = testScope.runTest {
        subject.setDynamicColorPreference(true)
        assertTrue(subject.userData.first().useDynamicColor)
    }

    @Test
    fun themeBrand_defaultByDefault() = testScope.runTest {
        assertEquals(ThemeBrand.DEFAULT, subject.userData.first().themeBrand)
    }

    @Test
    fun themeBrand_updatesToAndroid() = testScope.runTest {
        subject.setThemeBrand(ThemeBrand.ANDROID)
        assertEquals(ThemeBrand.ANDROID, subject.userData.first().themeBrand)
    }

    @Test
    fun themeBrand_switchesBackToDefault() = testScope.runTest {
        subject.setThemeBrand(ThemeBrand.ANDROID)
        subject.setThemeBrand(ThemeBrand.DEFAULT)
        assertEquals(ThemeBrand.DEFAULT, subject.userData.first().themeBrand)
    }

    @Test
    fun darkThemeConfig_defaultIsFollowSystem() = testScope.runTest {
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, subject.userData.first().darkThemeConfig)
    }

    @Test
    fun darkThemeConfig_updatesToLight() = testScope.runTest {
        subject.setDarkThemeConfig(DarkThemeConfig.LIGHT)
        assertEquals(DarkThemeConfig.LIGHT, subject.userData.first().darkThemeConfig)
    }

    @Test
    fun darkThemeConfig_updatesToDark() = testScope.runTest {
        subject.setDarkThemeConfig(DarkThemeConfig.DARK)
        assertEquals(DarkThemeConfig.DARK, subject.userData.first().darkThemeConfig)
    }

    @Test
    fun setNewsResourceBookmarked_addsBookmark() = testScope.runTest {
        subject.setNewsResourceBookmarked("news-1", bookmarked = true)
        assertTrue("news-1" in subject.userData.first().bookmarkedNewsResources)
    }

    @Test
    fun setNewsResourceBookmarked_removesBookmark() = testScope.runTest {
        subject.setNewsResourceBookmarked("news-1", bookmarked = true)
        subject.setNewsResourceBookmarked("news-1", bookmarked = false)
        assertTrue("news-1" !in subject.userData.first().bookmarkedNewsResources)
    }

    @Test
    fun setNewsResourceViewed_marksAsViewed() = testScope.runTest {
        subject.setNewsResourceViewed("news-42", viewed = true)
        assertTrue("news-42" in subject.userData.first().viewedNewsResources)
    }

    @Test
    fun setNewsResourceViewed_unmarksAsViewed() = testScope.runTest {
        subject.setNewsResourceViewed("news-42", viewed = true)
        subject.setNewsResourceViewed("news-42", viewed = false)
        assertTrue("news-42" !in subject.userData.first().viewedNewsResources)
    }

    @Test
    fun getChangeListVersions_defaultIsZero() = testScope.runTest {
        val versions = subject.getChangeListVersions()
        assertEquals(0, versions.topicVersion)
        assertEquals(0, versions.newsResourceVersion)
    }

    @Test
    fun updateChangeListVersion_updatesTopicVersion() = testScope.runTest {
        subject.updateChangeListVersion { copy(topicVersion = 5) }
        val versions = subject.getChangeListVersions()
        assertEquals(5, versions.topicVersion)
        assertEquals(0, versions.newsResourceVersion)
    }

    @Test
    fun updateChangeListVersion_updatesNewsResourceVersion() = testScope.runTest {
        subject.updateChangeListVersion { copy(newsResourceVersion = 10) }
        val versions = subject.getChangeListVersions()
        assertEquals(0, versions.topicVersion)
        assertEquals(10, versions.newsResourceVersion)
    }

    @Test
    fun updateChangeListVersion_updatesBothVersions() = testScope.runTest {
        subject.updateChangeListVersion { copy(topicVersion = 3, newsResourceVersion = 7) }
        val versions = subject.getChangeListVersions()
        assertEquals(3, versions.topicVersion)
        assertEquals(7, versions.newsResourceVersion)
    }

    @Test
    fun setFollowedTopicIds_bulkUpdate() = testScope.runTest {
        subject.setFollowedTopicIds(setOf("t1", "t2", "t3"))
        val followedTopics = subject.userData.first().followedTopics
        assertEquals(setOf("t1", "t2", "t3"), followedTopics)
    }

    @Test
    fun setFollowedTopicIds_followingTopicHidesOnboarding() = testScope.runTest {
        subject.setFollowedTopicIds(setOf("t1"))
        subject.setShouldHideOnboarding(true)
        assertTrue(subject.userData.first().shouldHideOnboarding)
    }
}
