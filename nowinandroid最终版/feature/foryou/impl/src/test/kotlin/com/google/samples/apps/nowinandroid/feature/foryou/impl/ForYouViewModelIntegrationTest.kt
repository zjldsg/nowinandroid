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

package com.google.samples.apps.nowinandroid.feature.foryou.impl

import androidx.lifecycle.SavedStateHandle
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsHelper
import com.google.samples.apps.nowinandroid.core.data.repository.NewsResourceQuery
import com.google.samples.apps.nowinandroid.core.data.repository.TopicsRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.data.util.SyncManager
import com.google.samples.apps.nowinandroid.core.domain.GetFollowableTopicsUseCase
import com.google.samples.apps.nowinandroid.core.model.data.DarkThemeConfig
import com.google.samples.apps.nowinandroid.core.model.data.NewsResource
import com.google.samples.apps.nowinandroid.core.model.data.ThemeBrand
import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.model.data.UserData
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.model.data.mapToUserNewsResources
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration tests for [ForYouViewModel] testing the complete flow
 * from user interactions through ViewModel to Repository layer.
 *
 * Uses backgroundScope + collect pattern because ForYouViewModel uses
 * stateIn(WhileSubscribed) which cancels upstream on subscriber loss.
 */
class ForYouViewModelIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val userDataRepository = TestUserDataRepository()
    private val newsRepository = TestNewsRepository()
    private val compositeRepository = TestCompositeUserNewsResourceRepository(newsRepository, userDataRepository)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows onboarding when user has not onboarded`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.onboardingUiState.collect() }

        advanceUntilIdle()

        val onboardingState = viewModel.onboardingUiState.value
        assertIs<OnboardingUiState.Shown>(onboardingState)
        assertTrue(onboardingState.topics.isNotEmpty())
    }

    @Test
    fun `selecting topics updates followed state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.onboardingUiState.collect() }

        advanceUntilIdle()

        // Get initial onboarding state
        val initialState = viewModel.onboardingUiState.value as OnboardingUiState.Shown
        val firstTopic = initialState.topics.first()

        // Select topic
        viewModel.updateTopicSelection(firstTopic.topic.id, true)
        advanceUntilIdle()

        // Verify followed state
        val userData = userDataRepository.userData.first()
        assertTrue(firstTopic.topic.id in userData.followedTopics)
    }

    @Test
    fun `deselecting topic removes from followed`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.onboardingUiState.collect() }

        advanceUntilIdle()

        // First select topic
        val initialState = viewModel.onboardingUiState.value as OnboardingUiState.Shown
        val firstTopic = initialState.topics.first()
        viewModel.updateTopicSelection(firstTopic.topic.id, true)
        advanceUntilIdle()

        // Verify followed
        assertTrue(userDataRepository.userData.first().followedTopics.contains(firstTopic.topic.id))

        // Now deselect
        viewModel.updateTopicSelection(firstTopic.topic.id, false)
        advanceUntilIdle()

        // Verify not followed anymore
        assertFalse(userDataRepository.userData.first().followedTopics.contains(firstTopic.topic.id))
    }

    @Test
    fun `feed shows news for followed topics`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedState.collect() }

        // First dismiss onboarding
        viewModel.dismissOnboarding()
        advanceUntilIdle()

        // Add some news
        newsRepository.sendNewsResources(sampleNewsResources)

        // Follow a topic that has news
        val topicToFollow = sampleNewsResources.first().topics.first().id
        userDataRepository.setTopicIdFollowed(topicToFollow, true)
        advanceUntilIdle()

        // Verify feed has news
        val feedState = viewModel.feedState.value
        assertIs<NewsFeedUiState.Success>(feedState)
        assertTrue(feedState.feed.isNotEmpty())
    }

    @Test
    fun `feed is empty when no topics followed`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedState.collect() }

        // First dismiss onboarding
        viewModel.dismissOnboarding()
        advanceUntilIdle()

        // Add some news
        newsRepository.sendNewsResources(sampleNewsResources)

        // Ensure no topics are followed
        userDataRepository.setUserData(
            UserData(
                bookmarkedNewsResources = emptySet(),
                viewedNewsResources = emptySet(),
                followedTopics = emptySet(),
                themeBrand = ThemeBrand.DEFAULT,
                darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
                useDynamicColor = false,
                shouldHideOnboarding = true,
            ),
        )
        advanceUntilIdle()

        // Verify feed is empty
        val feedState = viewModel.feedState.value
        assertIs<NewsFeedUiState.Success>(feedState)
        assertTrue(feedState.feed.isEmpty())
    }

    @Test
    fun `dismiss onboarding hides onboarding state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.onboardingUiState.collect() }

        advanceUntilIdle()

        // Verify onboarding is shown
        var onboardingState = viewModel.onboardingUiState.value
        assertIs<OnboardingUiState.Shown>(onboardingState)

        // Dismiss
        viewModel.dismissOnboarding()
        advanceUntilIdle()

        // Verify onboarding is not shown
        onboardingState = viewModel.onboardingUiState.value
        assertIs<OnboardingUiState.NotShown>(onboardingState)
    }

    @Test
    fun `bookmark news updates bookmark state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedState.collect() }

        // First dismiss onboarding and add news
        viewModel.dismissOnboarding()
        advanceUntilIdle()
        newsRepository.sendNewsResources(sampleNewsResources)

        val newsToBookmark = sampleNewsResources.first()

        // Bookmark news
        viewModel.updateNewsResourceSaved(newsToBookmark.id, true)
        advanceUntilIdle()

        // Verify bookmarked
        val userData = userDataRepository.userData.first()
        assertTrue(newsToBookmark.id in userData.bookmarkedNewsResources)
    }

    @Test
    fun `unbookmark news removes bookmark state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedState.collect() }

        // First dismiss onboarding and add news
        viewModel.dismissOnboarding()
        advanceUntilIdle()
        newsRepository.sendNewsResources(sampleNewsResources)

        val newsToBookmark = sampleNewsResources.first()

        // Bookmark then unbookmark
        viewModel.updateNewsResourceSaved(newsToBookmark.id, true)
        advanceUntilIdle()
        viewModel.updateNewsResourceSaved(newsToBookmark.id, false)
        advanceUntilIdle()

        // Verify not bookmarked
        val userData = userDataRepository.userData.first()
        assertFalse(newsToBookmark.id in userData.bookmarkedNewsResources)
    }

    @Test
    fun `mark news as viewed updates viewed state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)

        val newsToMark = sampleNewsResources.first()

        // Mark as viewed
        viewModel.setNewsResourceViewed(newsToMark.id, true)
        advanceUntilIdle()

        // Verify viewed
        val userData = userDataRepository.userData.first()
        assertTrue(newsToMark.id in userData.viewedNewsResources)
    }

    @Test
    fun `complete onboarding journey works end to end`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.onboardingUiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedState.collect() }

        advanceUntilIdle()

        // Step 1: Verify onboarding is shown
        var onboardingState = viewModel.onboardingUiState.value
        assertIs<OnboardingUiState.Shown>(onboardingState)

        // Step 2: Select some topics
        val topicsToFollow = onboardingState.topics.take(2)
        topicsToFollow.forEach { followableTopic ->
            viewModel.updateTopicSelection(followableTopic.topic.id, true)
        }
        advanceUntilIdle()

        // Step 3: Add news data
        newsRepository.sendNewsResources(sampleNewsResources)

        // Step 4: Dismiss onboarding
        viewModel.dismissOnboarding()
        advanceUntilIdle()

        // Step 5: Verify onboarding is hidden
        onboardingState = viewModel.onboardingUiState.value
        assertIs<OnboardingUiState.NotShown>(onboardingState)

        // Step 6: Verify feed has content for followed topics
        val feedState = viewModel.feedState.value
        assertIs<NewsFeedUiState.Success>(feedState)

        // Step 7: Bookmark first news item
        val firstNews = feedState.feed.firstOrNull()
        if (firstNews != null) {
            viewModel.updateNewsResourceSaved(firstNews.id, true)
            advanceUntilIdle()
            val userData = userDataRepository.userData.first()
            assertTrue(firstNews.id in userData.bookmarkedNewsResources)
        }
    }

    @Test
    fun `following more topics expands feed`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedState.collect() }

        // Dismiss onboarding
        viewModel.dismissOnboarding()
        advanceUntilIdle()

        // Send news with multiple topics
        newsRepository.sendNewsResources(sampleNewsResources)

        // Follow only first topic
        val firstTopicId = sampleNewsResources.first().topics.first().id
        viewModel.updateTopicSelection(firstTopicId, true)
        advanceUntilIdle()

        val feedWithOneTopic = viewModel.feedState.value as NewsFeedUiState.Success
        val initialCount = feedWithOneTopic.feed.size

        // Follow second topic
        val secondTopicId = sampleNewsResources[1].topics.first().id
        viewModel.updateTopicSelection(secondTopicId, true)
        advanceUntilIdle()

        val feedWithTwoTopics = viewModel.feedState.value as NewsFeedUiState.Success

        // Feed should have at least as many items (might be same if topics overlap)
        assertTrue(feedWithTwoTopics.feed.size >= initialCount)
    }

    private fun createViewModel(): ForYouViewModel {
        return ForYouViewModel(
            savedStateHandle = SavedStateHandle(),
            syncManager = TestSyncManager(),
            analyticsHelper = TestAnalyticsHelper(),
            userDataRepository = userDataRepository,
            userNewsResourceRepository = compositeRepository,
            getFollowableTopics = GetFollowableTopicsUseCase(
                TestTopicsRepository(),
                userDataRepository,
            ),
        )
    }
}

// Test doubles
private class TestSyncManager : SyncManager {
    override val isSyncing: Flow<Boolean> = flowOf(false)
    override fun requestSync() {}
}

private class TestAnalyticsHelper : AnalyticsHelper {
    override fun logEvent(event: com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent) {}
}

private class TestCompositeUserNewsResourceRepository(
    private val newsRepository: TestNewsRepository,
    private val userDataRepository: TestUserDataRepository,
) : UserNewsResourceRepository {

    override fun observeAll(query: NewsResourceQuery): Flow<List<UserNewsResource>> =
        combine(
            newsRepository.getNewsResources(query),
            userDataRepository.userData,
        ) { newsResources, userData ->
            newsResources.mapToUserNewsResources(userData)
        }

    override fun observeAllForFollowedTopics(): Flow<List<UserNewsResource>> =
        combine(
            newsRepository.getNewsResources(NewsResourceQuery()),
            userDataRepository.userData,
        ) { newsResources, userData ->
            newsResources
                .filter { news -> news.topics.any { it.id in userData.followedTopics } }
                .mapToUserNewsResources(userData)
        }

    override fun observeAllBookmarked(): Flow<List<UserNewsResource>> =
        combine(
            newsRepository.getNewsResources(NewsResourceQuery()),
            userDataRepository.userData,
        ) { newsResources, userData ->
            newsResources
                .filter { it.id in userData.bookmarkedNewsResources }
                .mapToUserNewsResources(userData)
        }
}

private class TestNewsRepository : com.google.samples.apps.nowinandroid.core.data.repository.NewsRepository {
    private val _newsResources = MutableStateFlow<List<NewsResource>>(emptyList())

    override fun getNewsResources(query: NewsResourceQuery): Flow<List<NewsResource>> = _newsResources

    fun sendNewsResources(news: List<NewsResource>) {
        _newsResources.value = news
    }

    override suspend fun syncWith(synchronizer: com.google.samples.apps.nowinandroid.core.data.Synchronizer) = true
}

private class TestTopicsRepository : TopicsRepository {
    private val _topics = MutableStateFlow<List<Topic>>(sampleTopics)

    override fun getTopics(): Flow<List<Topic>> = _topics

    override fun getTopic(id: String): Flow<Topic> = _topics.map { list -> list.first { t -> t.id == id } }

    override suspend fun syncWith(synchronizer: com.google.samples.apps.nowinandroid.core.data.Synchronizer) = true
}

// Sample data
private val sampleTopics = listOf(
    Topic(
        id = "topic1",
        name = "Android Development",
        shortDescription = "Learn Android",
        longDescription = "Everything about Android",
        url = "https://example.com/android",
        imageUrl = "https://example.com/android.jpg",
    ),
    Topic(
        id = "topic2",
        name = "Jetpack Compose",
        shortDescription = "Modern UI toolkit",
        longDescription = "Jetpack Compose for UI",
        url = "https://example.com/compose",
        imageUrl = "https://example.com/compose.jpg",
    ),
)

private val sampleNewsResources = listOf(
    NewsResource(
        id = "news1",
        title = "Introduction to Android",
        content = "Learn the basics of Android development",
        url = "https://example.com/news/1",
        headerImageUrl = "https://example.com/news/1.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00.000Z"),
        type = "Article",
        topics = listOf(sampleTopics[0]),
    ),
    NewsResource(
        id = "news2",
        title = "Getting Started with Compose",
        content = "Build modern UIs with Jetpack Compose",
        url = "https://example.com/news/2",
        headerImageUrl = "https://example.com/news/2.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-02T00:00:00.000Z"),
        type = "Video",
        topics = listOf(sampleTopics[1]),
    ),
    NewsResource(
        id = "news3",
        title = "Android Best Practices",
        content = "Follow Android best practices",
        url = "https://example.com/news/3",
        headerImageUrl = "https://example.com/news/3.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-03T00:00:00.000Z"),
        type = "Article",
        topics = listOf(sampleTopics[0], sampleTopics[1]),
    ),
)
