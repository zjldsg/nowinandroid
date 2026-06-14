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

package com.google.samples.apps.nowinandroid.feature.bookmarks.impl

import com.google.samples.apps.nowinandroid.core.data.repository.UserNewsResourceRepository
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [BookmarksViewModel] testing the complete flow
 * from user interactions through ViewModel to Repository layer.
 *
 * Uses backgroundScope + collect pattern because BookmarksViewModel uses
 * stateIn(WhileSubscribed) which cancels upstream on subscriber loss.
 */
class BookmarksViewModelIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val userDataRepository = TestUserDataRepository()
    private val newsRepository = TestNewsRepository()
    private val userNewsResourceRepository = TestUserNewsResourceRepository(
        newsRepository,
        userDataRepository,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows loading`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        // Read initial value before collector starts
        val state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Loading>(state)
    }

    @Test
    fun `empty bookmarks shows empty feed`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        // Ensure no bookmarks
        userDataRepository.setUserData(emptyUserData)
        newsRepository.sendNewsResources(sampleNewsResources)
        advanceUntilIdle()

        val state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        assertTrue(state.feed.isEmpty())
    }

    @Test
    fun `bookmarked news appears in feed`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)

        val newsToBookmark = sampleNewsResources.first()
        userDataRepository.setNewsResourceBookmarked(newsToBookmark.id, true)
        advanceUntilIdle()

        val state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        assertEquals(1, state.feed.size)
        assertEquals(newsToBookmark.id, state.feed.first().id)
    }

    @Test
    fun `removing bookmark removes from feed`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)
        val newsToBookmark = sampleNewsResources.first()
        userDataRepository.setNewsResourceBookmarked(newsToBookmark.id, true)
        advanceUntilIdle()

        var state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        assertEquals(1, state.feed.size)

        viewModel.removeFromSavedResources(newsToBookmark.id)
        advanceUntilIdle()

        state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        assertTrue(state.feed.isEmpty() || state.feed.none { it.id == newsToBookmark.id })
    }

    @Test
    fun `undo bookmark restoration works`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)
        val newsToBookmark = sampleNewsResources.first()

        var userData = userDataRepository.userData.first()
        assertFalse(newsToBookmark.id in userData.bookmarkedNewsResources)

        viewModel.removeFromSavedResources(newsToBookmark.id)
        advanceUntilIdle()

        assertTrue(viewModel.shouldDisplayUndoBookmark)
        assertEquals(newsToBookmark.id, viewModel.lastRemovedBookmarkId)

        viewModel.undoBookmarkRemoval()
        advanceUntilIdle()

        assertFalse(viewModel.shouldDisplayUndoBookmark)
        assertNull(viewModel.lastRemovedBookmarkId)

        userData = userDataRepository.userData.first()
        assertTrue(newsToBookmark.id in userData.bookmarkedNewsResources)
    }

    @Test
    fun `multiple bookmarks shows all in feed`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)

        val bookmarkedIds = setOf(
            sampleNewsResources[0].id,
            sampleNewsResources[1].id,
            sampleNewsResources[2].id,
        )
        bookmarkedIds.forEach { id ->
            userDataRepository.setNewsResourceBookmarked(id, true)
        }
        advanceUntilIdle()

        val state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        assertEquals(3, state.feed.size)
        assertTrue(state.feed.all { it.id in bookmarkedIds })
    }

    @Test
    fun `clearing undo state works`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)
        val newsToBookmark = sampleNewsResources.first()
        userDataRepository.setNewsResourceBookmarked(newsToBookmark.id, true)

        viewModel.removeFromSavedResources(newsToBookmark.id)
        assertTrue(viewModel.shouldDisplayUndoBookmark)

        viewModel.clearUndoState()
        assertFalse(viewModel.shouldDisplayUndoBookmark)
        assertNull(viewModel.lastRemovedBookmarkId)
    }

    @Test
    fun `mark news as viewed updates viewed status`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)
        val newsToBookmark = sampleNewsResources.first()
        userDataRepository.setNewsResourceBookmarked(newsToBookmark.id, true)
        advanceUntilIdle()

        viewModel.setNewsResourceViewed(newsToBookmark.id, true)
        advanceUntilIdle()

        val userData = userDataRepository.userData.first()
        assertTrue(newsToBookmark.id in userData.viewedNewsResources)

        val state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        val newsItem = state.feed.find { it.id == newsToBookmark.id }
        assertNotNull(newsItem)
        assertTrue(newsItem.hasBeenViewed)
    }

    @Test
    fun `complete bookmark journey works end to end`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)

        val bookmarkedIds = setOf(sampleNewsResources[0].id, sampleNewsResources[1].id)
        bookmarkedIds.forEach { id ->
            userDataRepository.setNewsResourceBookmarked(id, true)
        }
        advanceUntilIdle()

        var state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        assertEquals(2, state.feed.size)

        val toRemove = sampleNewsResources[0].id
        viewModel.removeFromSavedResources(toRemove)
        advanceUntilIdle()

        state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        assertEquals(1, state.feed.size)
        assertEquals(sampleNewsResources[1].id, state.feed.first().id)

        viewModel.undoBookmarkRemoval()
        advanceUntilIdle()

        state = viewModel.feedUiState.value
        assertIs<NewsFeedUiState.Success>(state)
        assertEquals(2, state.feed.size)
    }

    @Test
    fun `bookmark removal shows snackbar state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)
        val newsToBookmark = sampleNewsResources.first()
        userDataRepository.setNewsResourceBookmarked(newsToBookmark.id, true)
        advanceUntilIdle()

        assertFalse(viewModel.shouldDisplayUndoBookmark)
        assertNull(viewModel.lastRemovedBookmarkId)

        viewModel.removeFromSavedResources(newsToBookmark.id)
        assertTrue(viewModel.shouldDisplayUndoBookmark)
        assertEquals(newsToBookmark.id, viewModel.lastRemovedBookmarkId)

        viewModel.clearUndoState()
        assertFalse(viewModel.shouldDisplayUndoBookmark)
        assertNull(viewModel.lastRemovedBookmarkId)
    }

    private fun createViewModel(): BookmarksViewModel {
        return BookmarksViewModel(
            userDataRepository = userDataRepository,
            userNewsResourceRepository = userNewsResourceRepository,
        )
    }
}

// Test doubles
private class TestNewsRepository : com.google.samples.apps.nowinandroid.core.data.repository.NewsRepository {
    private val _newsResources = MutableStateFlow<List<NewsResource>>(emptyList())

    override fun getNewsResources(query: com.google.samples.apps.nowinandroid.core.data.repository.NewsResourceQuery): Flow<List<NewsResource>> = _newsResources

    fun sendNewsResources(news: List<NewsResource>) {
        _newsResources.value = news
    }

    override suspend fun syncWith(synchronizer: com.google.samples.apps.nowinandroid.core.data.Synchronizer) = true
}

private class TestUserNewsResourceRepository(
    private val newsRepository: TestNewsRepository,
    private val userDataRepository: TestUserDataRepository,
) : UserNewsResourceRepository {
    override fun observeAll(query: com.google.samples.apps.nowinandroid.core.data.repository.NewsResourceQuery): Flow<List<UserNewsResource>> =
        observeAllBookmarked()

    override fun observeAllForFollowedTopics(): Flow<List<UserNewsResource>> =
        observeAllBookmarked()

    override fun observeAllBookmarked(): Flow<List<UserNewsResource>> =
        combine(
            newsRepository.getNewsResources(com.google.samples.apps.nowinandroid.core.data.repository.NewsResourceQuery()),
            userDataRepository.userData,
        ) { newsResources, userData ->
            newsResources
                .filter { it.id in userData.bookmarkedNewsResources }
                .mapToUserNewsResources(userData)
        }
}

// Sample data
private val sampleTopics = listOf(
    Topic(
        id = "topic1",
        name = "Android",
        shortDescription = "Android topics",
        longDescription = "Android development",
        url = "https://example.com",
        imageUrl = "https://example.com/image.jpg",
    ),
)

private val emptyUserData = UserData(
    bookmarkedNewsResources = emptySet(),
    viewedNewsResources = emptySet(),
    followedTopics = emptySet(),
    themeBrand = ThemeBrand.DEFAULT,
    darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    useDynamicColor = false,
    shouldHideOnboarding = false,
)

private val sampleNewsResources = listOf(
    NewsResource(
        id = "news1",
        title = "Android Basics",
        content = "Learn Android basics",
        url = "https://example.com/news/1",
        headerImageUrl = "https://example.com/news/1.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00.000Z"),
        type = "Article",
        topics = sampleTopics,
    ),
    NewsResource(
        id = "news2",
        title = "Jetpack Compose",
        content = "Modern Android UI",
        url = "https://example.com/news/2",
        headerImageUrl = "https://example.com/news/2.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-02T00:00:00.000Z"),
        type = "Video",
        topics = sampleTopics,
    ),
    NewsResource(
        id = "news3",
        title = "Coroutines",
        content = "Async programming in Kotlin",
        url = "https://example.com/news/3",
        headerImageUrl = "https://example.com/news/3.jpg",
        publishDate = kotlinx.datetime.Instant.parse("2024-01-03T00:00:00.000Z"),
        type = "Article",
        topics = sampleTopics,
    ),
)
