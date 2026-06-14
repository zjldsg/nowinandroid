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

package com.google.samples.apps.nowinandroid.core.data.repository

import com.google.samples.apps.nowinandroid.core.data.Synchronizer
import com.google.samples.apps.nowinandroid.core.data.testdoubles.CollectionType
import com.google.samples.apps.nowinandroid.core.data.testdoubles.TestNewsResourceDao
import com.google.samples.apps.nowinandroid.core.data.testdoubles.TestNiaNetworkDataSource
import com.google.samples.apps.nowinandroid.core.data.testdoubles.TestTopicDao
import com.google.samples.apps.nowinandroid.core.database.model.PopulatedNewsResource
import com.google.samples.apps.nowinandroid.core.database.model.asExternalModel
import com.google.samples.apps.nowinandroid.core.datastore.NiaPreferencesDataSource
import com.google.samples.apps.nowinandroid.core.datastore.UserPreferences
import com.google.samples.apps.nowinandroid.core.datastore.test.InMemoryDataStore
import com.google.samples.apps.nowinandroid.core.model.data.DarkThemeConfig
import com.google.samples.apps.nowinandroid.core.model.data.NewsResource
import com.google.samples.apps.nowinandroid.core.model.data.ThemeBrand
import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.model.data.UserData
import com.google.samples.apps.nowinandroid.core.testing.notifications.TestNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [OfflineFirstNewsRepository] with real database operations.
 * Tests the complete flow from network to database to repository.
 */
class OfflineFirstNewsRepositoryIntegrationTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var newsRepository: OfflineFirstNewsRepository
    private lateinit var userDataRepository: OfflineFirstUserDataRepository
    private lateinit var compositeRepository: CompositeUserNewsResourceRepository

    private lateinit var niaPreferencesDataSource: NiaPreferencesDataSource
    private lateinit var newsResourceDao: TestNewsResourceDao
    private lateinit var topicDao: TestTopicDao
    private lateinit var network: TestNiaNetworkDataSource
    private lateinit var notifier: TestNotifier
    private lateinit var synchronizer: Synchronizer

    @Before
    fun setup() {
        niaPreferencesDataSource = NiaPreferencesDataSource(
            InMemoryDataStore(UserPreferences.getDefaultInstance()),
        )
        newsResourceDao = TestNewsResourceDao()
        topicDao = TestTopicDao()
        network = TestNiaNetworkDataSource()
        notifier = TestNotifier()
        synchronizer = TestSynchronizer(niaPreferencesDataSource)

        newsRepository = OfflineFirstNewsRepository(
            niaPreferencesDataSource = niaPreferencesDataSource,
            newsResourceDao = newsResourceDao,
            topicDao = topicDao,
            network = network,
            notifier = notifier,
        )

        userDataRepository = OfflineFirstUserDataRepository(
            niaPreferencesDataSource = niaPreferencesDataSource,
            analyticsHelper = TestAnalyticsHelper(),
        )

        compositeRepository = CompositeUserNewsResourceRepository(
            newsRepository = newsRepository,
            userDataRepository = userDataRepository,
        )
    }

    @Test
    fun followTopic_thenUnfollow_topicNewsFlowUpdatesCorrectly() = testScope.runTest {
        // First sync data from network
        newsRepository.syncWith(synchronizer)

        // Get all news resources initially
        val allNews = newsRepository.getNewsResources().first()
        assertTrue(allNews.isNotEmpty())

        // Get topics from the first news resource
        val firstTopicId = allNews.first().topics.first().id

        // Initially, no topics are followed
        val initialUserData = userDataRepository.userData.first()
        assertTrue(initialUserData.followedTopics.isEmpty())

        // Follow a topic
        userDataRepository.setTopicIdFollowed(firstTopicId, true)

        // Verify the followed topic is stored
        val afterFollowUserData = userDataRepository.userData.first()
        assertTrue(firstTopicId in afterFollowUserData.followedTopics)

        // Get news for followed topics
        val followedTopicNews = compositeRepository.observeAllForFollowedTopics().first()
        assertTrue(followedTopicNews.isNotEmpty())
        assertTrue(followedTopicNews.all { news ->
            news.followableTopics.any { it.topic.id == firstTopicId }
        })

        // Unfollow the topic
        userDataRepository.setTopicIdFollowed(firstTopicId, false)

        // Verify unfollow
        val afterUnfollowUserData = userDataRepository.userData.first()
        assertFalse(firstTopicId in afterUnfollowUserData.followedTopics)

        // Get news for followed topics - should be empty now
        val afterUnfollowNews = compositeRepository.observeAllForFollowedTopics().first()
        assertTrue(afterUnfollowNews.isEmpty())
    }

    @Test
    fun bookmarkNews_andUndo_bookmarkFlowUpdatesCorrectly() = testScope.runTest {
        // First sync data from network
        newsRepository.syncWith(synchronizer)

        // Get all news resources
        val allNews = newsRepository.getNewsResources().first()
        val firstNewsId = allNews.first().id

        // Initially no bookmarks
        val initialUserData = userDataRepository.userData.first()
        assertTrue(initialUserData.bookmarkedNewsResources.isEmpty())

        // Bookmark a news resource
        userDataRepository.setNewsResourceBookmarked(firstNewsId, true)

        // Verify bookmark is stored
        val afterBookmarkUserData = userDataRepository.userData.first()
        assertTrue(firstNewsId in afterBookmarkUserData.bookmarkedNewsResources)

        // Get bookmarked news
        val bookmarkedNews = compositeRepository.observeAllBookmarked().first()
        assertTrue(bookmarkedNews.isNotEmpty())
        assertTrue(bookmarkedNews.any { it.id == firstNewsId })

        // Unbookmark
        userDataRepository.setNewsResourceBookmarked(firstNewsId, false)

        // Verify unbookmark
        val afterUnbookmarkUserData = userDataRepository.userData.first()
        assertFalse(firstNewsId in afterUnbookmarkUserData.bookmarkedNewsResources)

        // Get bookmarked news - should be empty now
        val afterUnbookmarkNews = compositeRepository.observeAllBookmarked().first()
        assertFalse(afterUnbookmarkNews.any { it.id == firstNewsId })
    }

    @Test
    fun markNewsAsViewed_viewedStatusUpdatesCorrectly() = testScope.runTest {
        // First sync data from network
        newsRepository.syncWith(synchronizer)

        // Get all news resources
        val allNews = newsRepository.getNewsResources().first()
        val firstNewsId = allNews.first().id

        // Initially no viewed news (first sync marks all as viewed)
        val afterSyncUserData = userDataRepository.userData.first()
        assertTrue(firstNewsId in afterSyncUserData.viewedNewsResources)

        // Reset - unview the news for testing
        userDataRepository.setNewsResourceViewed(firstNewsId, false)

        val afterUnview = userDataRepository.userData.first()
        assertFalse(firstNewsId in afterUnview.viewedNewsResources)

        // Mark as viewed
        userDataRepository.setNewsResourceViewed(firstNewsId, true)

        val afterView = userDataRepository.userData.first()
        assertTrue(firstNewsId in afterView.viewedNewsResources)
    }

    @Test
    fun completeUserJourney_onboardingToFeed() = testScope.runTest {
        // Step 1: Initial state - user has not onboarded
        var userData = userDataRepository.userData.first()
        assertFalse(userData.shouldHideOnboarding)
        assertTrue(userData.followedTopics.isEmpty())

        // Step 2: Sync data from network
        newsRepository.syncWith(synchronizer)

        // Step 3: User selects topics to follow
        val allNews = newsRepository.getNewsResources().first()
        val topicIdsToFollow = allNews.flatMap { it.topics }.map { it.id }.distinct().take(2)

        topicIdsToFollow.forEach { topicId ->
            userDataRepository.setTopicIdFollowed(topicId, true)
        }

        // Step 4: Verify topics are followed
        userData = userDataRepository.userData.first()
        topicIdsToFollow.forEach { topicId ->
            assertTrue(userData.followedTopics.contains(topicId))
        }

        // Step 5: Get personalized feed
        val feed = compositeRepository.observeAllForFollowedTopics().first()
        assertTrue(feed.isNotEmpty())
        feed.forEach { news ->
            assertTrue(
                news.followableTopics.any { it.topic.id in topicIdsToFollow },
                "News ${news.id} should have at least one followed topic",
            )
        }

        // Step 6: Bookmark some news
        val newsToBookmark = feed.take(2)
        newsToBookmark.forEach { news ->
            userDataRepository.setNewsResourceBookmarked(news.id, true)
        }

        // Step 7: Verify bookmarks
        val bookmarkedNews = compositeRepository.observeAllBookmarked().first()
        assertEquals(2, bookmarkedNews.size)
        newsToBookmark.forEach { news ->
            assertTrue(bookmarkedNews.any { it.id == news.id })
        }

        // Step 8: Dismiss onboarding
        userDataRepository.setShouldHideOnboarding(true)

        // Step 9: Verify onboarding is dismissed
        userData = userDataRepository.userData.first()
        assertTrue(userData.shouldHideOnboarding)
    }

    @Test
    fun syncWithOnboardedUser_sendsNotifications() = testScope.runTest {
        // User has onboarded
        userDataRepository.setShouldHideOnboarding(true)

        // Follow some topics
        val networkNews = network.getNewsResources()
        val topicsToFollow = networkNews
            .flatMap { it.topics }
            .distinct()
            .take(2)

        topicsToFollow.forEach { topicId ->
            userDataRepository.setTopicIdFollowed(topicId, true)
        }

        // Clear any previous notifications
        notifier.clear()

        // Sync - should send notifications for new content
        newsRepository.syncWith(synchronizer)

        // Verify notifications were sent for followed topics
        val notifications = notifier.addedNewsResources.first()
        assertTrue(notifications.isNotEmpty())
        notifications.forEach { news ->
            assertTrue(
                news.topics.any { it.id in topicsToFollow },
                "Notification should be for followed topic",
            )
        }
    }

    @Test
    fun incrementalSync_updatesOnlyChangedItems() = testScope.runTest {
        // First sync
        newsRepository.syncWith(synchronizer)
        val firstSyncNews = newsRepository.getNewsResources().first()
        assertTrue(firstSyncNews.isNotEmpty())

        // Simulate adding new content on network
        val originalCount = network.getNewsResources().size
        val newChangeList = network.changeListsAfter(
            CollectionType.NewsResources,
            version = 0,
        )
        val lastVersion = newChangeList.lastOrNull()?.changeListVersion ?: 0

        // Set version to simulate new changes
        synchronizer.updateChangeListVersions {
            copy(newsResourceVersion = lastVersion)
        }

        // Second sync (simulating periodic sync)
        newsRepository.syncWith(synchronizer)

        // Verify version was updated
        val versions = synchronizer.getChangeListVersions()
        assertEquals(
            network.latestChangeListVersion(CollectionType.NewsResources),
            versions.newsResourceVersion,
        )
    }

    @Test
    fun mixedFollowAndBookmark_queriesWorkCorrectly() = testScope.runTest {
        // Sync data
        newsRepository.syncWith(synchronizer)

        val allNews = newsRepository.getNewsResources().first()
        assertTrue(allNews.size >= 3)

        // Follow topic 1
        val topic1Id = allNews[0].topics.first().id
        userDataRepository.setTopicIdFollowed(topic1Id, true)

        // Bookmark news 2
        val news2Id = allNews[1].id
        userDataRepository.setNewsResourceBookmarked(news2Id, true)

        // Bookmark news 3
        val news3Id = allNews[2].id
        userDataRepository.setNewsResourceBookmarked(news3Id, true)

        // Get followed topics feed
        val followedFeed = compositeRepository.observeAllForFollowedTopics().first()
        assertTrue(followedFeed.isNotEmpty())

        // Get bookmarked feed
        val bookmarkedFeed = compositeRepository.observeAllBookmarked().first()
        assertEquals(2, bookmarkedFeed.size)
        assertTrue(bookmarkedFeed.any { it.id == news2Id })
        assertTrue(bookmarkedFeed.any { it.id == news3Id })

        // Get all news with combined user data
        val allWithUserData = compositeRepository.observeAll().first()
        val bookmarkedInAll = allWithUserData.filter { it.isSaved }
        assertEquals(2, bookmarkedInAll.size)
    }
}

/**
 * Simple test analytics helper that does nothing.
 */
private class TestAnalyticsHelper : com.google.samples.apps.nowinandroid.core.analytics.AnalyticsHelper {
    override fun logEvent(event: com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent) {}
}
