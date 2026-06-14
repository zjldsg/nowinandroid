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

package com.google.samples.apps.nowinandroid.core.data

import com.google.samples.apps.nowinandroid.core.data.repository.CompositeUserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.data.repository.NewsRepository
import com.google.samples.apps.nowinandroid.core.data.repository.NewsResourceQuery
import com.google.samples.apps.nowinandroid.core.data.repository.TopicsRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.model.data.DarkThemeConfig
import com.google.samples.apps.nowinandroid.core.model.data.FollowableTopic
import com.google.samples.apps.nowinandroid.core.model.data.NewsResource
import com.google.samples.apps.nowinandroid.core.model.data.ThemeBrand
import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.model.data.UserData
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.model.data.mapToUserNewsResources
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-module integration tests that test the complete flow across multiple repositories.
 */
class CrossModuleIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    // Test doubles for repositories
    private lateinit var newsRepository: TestNewsRepository
    private lateinit var topicsRepository: TestTopicsRepository
    private lateinit var userDataRepository: TestUserDataRepository
    private lateinit var compositeRepository: CompositeUserNewsResourceRepository

    @Before
    fun setup() {
        newsRepository = TestNewsRepository()
        topicsRepository = TestTopicsRepository()
        userDataRepository = TestUserDataRepository()
        compositeRepository = CompositeUserNewsResourceRepository(
            newsRepository = newsRepository,
            userDataRepository = userDataRepository,
        )
    }

    @Test
    fun followedTopicsFilter_newsResourcesFilteredCorrectly() = runTest(testDispatcher) {
        // Setup: Add test news resources with different topics
        newsRepository.sendNewsResources(completeNewsResources)

        // Initially no followed topics
        var userData = userDataRepository.userData.first()
        assertTrue(userData.followedTopics.isEmpty())

        // Get news for followed topics - should be empty
        var followedNews = compositeRepository.observeAllForFollowedTopics().first()
        assertTrue(followedNews.isEmpty())

        // Follow Android topic
        userDataRepository.setTopicIdFollowed(androidTopic.id, true)
        advanceUntilIdle()

        // Get news for followed topics - should have Android news
        followedNews = compositeRepository.observeAllForFollowedTopics().first()
        assertTrue(followedNews.isNotEmpty())
        assertTrue(
            followedNews.all { news ->
                news.followableTopics.any { it.topic.id == androidTopic.id }
            },
        )

        // Follow Kotlin topic as well
        userDataRepository.setTopicIdFollowed(kotlinTopic.id, true)
        advanceUntilIdle()

        // Get news for followed topics
        followedNews = compositeRepository.observeAllForFollowedTopics().first()
        assertTrue(followedNews.isNotEmpty())
        assertTrue(
            followedNews.all { news ->
                news.followableTopics.any { it.topic.id in setOf(androidTopic.id, kotlinTopic.id) }
            },
        )
    }

    @Test
    fun bookmark_newsResourceMarkedAsSaved() = runTest(testDispatcher) {
        newsRepository.sendNewsResources(completeNewsResources)

        val newsToBookmark = completeNewsResources.first()
        userDataRepository.setNewsResourceBookmarked(newsToBookmark.id, true)
        advanceUntilIdle()

        val bookmarkedNews = compositeRepository.observeAllBookmarked().first()

        assertTrue(bookmarkedNews.isNotEmpty())
        assertTrue(
            bookmarkedNews.any { it.id == newsToBookmark.id },
        )

        val bookmarkedItem = bookmarkedNews.find { it.id == newsToBookmark.id }
        assertNotNull(bookmarkedItem)
        assertTrue(bookmarkedItem.isSaved)
    }

    @Test
    fun viewedStatus_trackedAcrossRepositories() = runTest(testDispatcher) {
        newsRepository.sendNewsResources(completeNewsResources)

        val allNews = compositeRepository.observeAll().first()
        assertTrue(allNews.isNotEmpty())

        val newsToMark = allNews.first()
        userDataRepository.setNewsResourceViewed(newsToMark.id, true)
        advanceUntilIdle()

        val updatedNews = compositeRepository.observeAll().first()
        val updatedItem = updatedNews.find { it.id == newsToMark.id }

        assertNotNull(updatedItem)
        assertTrue(updatedItem.hasBeenViewed)
    }

    @Test
    fun completeUserJourney_fromOnboardingToPersonalizedFeed() = runTest(testDispatcher) {
        // Step 1: Initial state - user is not onboarded
        var userData = userDataRepository.userData.first()
        assertFalse(userData.shouldHideOnboarding)
        assertTrue(userData.followedTopics.isEmpty())
        assertTrue(userData.bookmarkedNewsResources.isEmpty())

        newsRepository.sendNewsResources(completeNewsResources)

        // Step 2: Follow topics
        userDataRepository.setTopicIdFollowed(androidTopic.id, true)
        userDataRepository.setTopicIdFollowed(kotlinTopic.id, true)
        advanceUntilIdle()

        userData = userDataRepository.userData.first()
        assertTrue(androidTopic.id in userData.followedTopics)
        assertTrue(kotlinTopic.id in userData.followedTopics)

        // Step 3: Get personalized feed
        val personalizedFeed = compositeRepository.observeAllForFollowedTopics().first()
        assertTrue(personalizedFeed.isNotEmpty())

        // Step 4: Bookmark news
        val newsToBookmark = personalizedFeed.first()
        userDataRepository.setNewsResourceBookmarked(newsToBookmark.id, true)
        advanceUntilIdle()

        val bookmarkedFeed = compositeRepository.observeAllBookmarked().first()
        assertTrue(bookmarkedFeed.any { it.id == newsToBookmark.id })

        // Step 5: Mark news as viewed
        val newsToMark = personalizedFeed.last()
        userDataRepository.setNewsResourceViewed(newsToMark.id, true)
        advanceUntilIdle()

        // Step 6: Complete onboarding
        userDataRepository.setShouldHideOnboarding(true)
        advanceUntilIdle()

        userData = userDataRepository.userData.first()
        assertTrue(userData.shouldHideOnboarding)
        assertTrue(androidTopic.id in userData.followedTopics)
        assertTrue(newsToBookmark.id in userData.bookmarkedNewsResources)
        assertTrue(newsToMark.id in userData.viewedNewsResources)
    }

    @Test
    fun multipleSequentialChanges_handledCorrectly() = runTest(testDispatcher) {
        newsRepository.sendNewsResources(completeNewsResources)

        userDataRepository.setTopicIdFollowed(androidTopic.id, true)
        advanceUntilIdle()

        userDataRepository.setTopicIdFollowed(kotlinTopic.id, true)
        advanceUntilIdle()

        val news1 = completeNewsResources[0]
        userDataRepository.setNewsResourceBookmarked(news1.id, true)
        advanceUntilIdle()

        val news2 = completeNewsResources[1]
        userDataRepository.setNewsResourceBookmarked(news2.id, true)
        advanceUntilIdle()

        userDataRepository.setNewsResourceViewed(news1.id, true)
        advanceUntilIdle()

        userDataRepository.setTopicIdFollowed(kotlinTopic.id, false)
        advanceUntilIdle()

        val userData = userDataRepository.userData.first()

        assertTrue(androidTopic.id in userData.followedTopics)
        assertFalse(kotlinTopic.id in userData.followedTopics)
        assertTrue(news1.id in userData.bookmarkedNewsResources)
        assertTrue(news2.id in userData.bookmarkedNewsResources)
        assertTrue(news1.id in userData.viewedNewsResources)
    }

    @Test
    fun topicData_consistencyAcrossRepositories() = runTest(testDispatcher) {
        val topics = topicsRepository.getTopics().first()
        assertTrue(topics.isNotEmpty())

        newsRepository.sendNewsResources(completeNewsResources)
        val news = compositeRepository.observeAll().first()

        val newsTopicIds = news.flatMap { it.followableTopics.map { ft -> ft.topic.id } }.toSet()
        val allTopicIds = topics.map { it.id }.toSet()
        assertTrue(newsTopicIds.all { it in allTopicIds })
    }
}

// Test doubles
private class TestNewsRepository : NewsRepository {
    private val _newsResources = MutableStateFlow<List<NewsResource>>(emptyList())

    override fun getNewsResources(query: NewsResourceQuery): Flow<List<NewsResource>> =
        _newsResources.map { news ->
            news.filter { resource ->
                val matchesTopics = query.filterTopicIds == null ||
                    resource.topics.any { topic -> topic.id in query.filterTopicIds }
                val matchesIds = query.filterNewsIds == null ||
                    resource.id in query.filterNewsIds
                matchesTopics && matchesIds
            }
        }

    fun sendNewsResources(news: List<NewsResource>) {
        _newsResources.value = news
    }

    override suspend fun syncWith(synchronizer: Synchronizer) = true
}

private class TestTopicsRepository : TopicsRepository {
    private val _topics = MutableStateFlow(listOf(androidTopic, kotlinTopic, composeTopic))

    override fun getTopics(): Flow<List<Topic>> = _topics

    override fun getTopic(id: String): Flow<Topic> = _topics.map { list -> list.first { t -> t.id == id } }

    override suspend fun syncWith(synchronizer: Synchronizer) = true
}

// Test data
private val androidTopic = Topic(
    id = "topic-android",
    name = "Android",
    shortDescription = "Android topics",
    longDescription = "Everything about Android",
    url = "https://example.com/android",
    imageUrl = "https://example.com/android.jpg",
)

private val kotlinTopic = Topic(
    id = "topic-kotlin",
    name = "Kotlin",
    shortDescription = "Kotlin topics",
    longDescription = "Kotlin programming",
    url = "https://example.com/kotlin",
    imageUrl = "https://example.com/kotlin.jpg",
)

private val composeTopic = Topic(
    id = "topic-compose",
    name = "Compose",
    shortDescription = "Jetpack Compose",
    longDescription = "Modern Android UI toolkit",
    url = "https://example.com/compose",
    imageUrl = "https://example.com/compose.jpg",
)

private val completeNewsResources = listOf(
    NewsResource(
        id = "news-android-basics",
        title = "Android Basics",
        content = "Learn Android basics",
        url = "https://example.com/news/android-basics",
        headerImageUrl = "https://example.com/news/android-basics.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00.000Z"),
        type = "Article",
        topics = listOf(androidTopic),
    ),
    NewsResource(
        id = "news-kotlin-coroutines",
        title = "Kotlin Coroutines",
        content = "Async programming with Kotlin",
        url = "https://example.com/news/kotlin-coroutines",
        headerImageUrl = "https://example.com/news/kotlin-coroutines.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-02T00:00:00.000Z"),
        type = "Video",
        topics = listOf(kotlinTopic),
    ),
    NewsResource(
        id = "news-compose-basics",
        title = "Compose Basics",
        content = "Getting started with Jetpack Compose",
        url = "https://example.com/news/compose-basics",
        headerImageUrl = "https://example.com/news/compose-basics.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-03T00:00:00.000Z"),
        type = "Article",
        topics = listOf(composeTopic),
    ),
    NewsResource(
        id = "news-android-compose",
        title = "Android Compose",
        content = "Using Compose in Android",
        url = "https://example.com/news/android-compose",
        headerImageUrl = "https://example.com/news/android-compose.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-04T00:00:00.000Z"),
        type = "Article",
        topics = listOf(androidTopic, composeTopic),
    ),
)
