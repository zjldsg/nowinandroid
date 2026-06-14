/*
 * Copyright 2023 The Android Open Source Project
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

package com.google.samples.apps.nowinandroid.feature.search.impl

import androidx.lifecycle.SavedStateHandle
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent
import com.google.samples.apps.nowinandroid.core.domain.GetRecentSearchQueriesUseCase
import com.google.samples.apps.nowinandroid.core.domain.GetSearchContentsUseCase
import com.google.samples.apps.nowinandroid.core.model.data.FollowableTopic
import com.google.samples.apps.nowinandroid.core.model.data.NewsResource
import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.testing.data.newsResourcesTestData
import com.google.samples.apps.nowinandroid.core.testing.data.topicsTestData
import com.google.samples.apps.nowinandroid.core.testing.repository.TestRecentSearchRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestSearchContentsRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.emptyUserData
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import com.google.samples.apps.nowinandroid.core.testing.util.TestAnalyticsHelper
import com.google.samples.apps.nowinandroid.feature.search.impl.RecentSearchQueriesUiState.Success
import com.google.samples.apps.nowinandroid.feature.search.impl.SearchResultUiState.EmptyQuery
import com.google.samples.apps.nowinandroid.feature.search.impl.SearchResultUiState.Loading
import com.google.samples.apps.nowinandroid.feature.search.impl.SearchResultUiState.SearchNotReady
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * To learn more about how this test handles Flows created with stateIn, see
 * https://developer.android.com/kotlin/flow/test#statein
 */
class SearchViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val searchContentsRepository = TestSearchContentsRepository()
    private val getSearchContentsUseCase = GetSearchContentsUseCase(
        searchContentsRepository = searchContentsRepository,
        userDataRepository = userDataRepository,
    )
    private val recentSearchRepository = TestRecentSearchRepository()
    private val getRecentQueryUseCase = GetRecentSearchQueriesUseCase(recentSearchRepository)
    private val analyticsHelper = TestAnalyticsHelper()

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        viewModel = SearchViewModel(
            getSearchContentsUseCase = getSearchContentsUseCase,
            recentSearchQueriesUseCase = getRecentQueryUseCase,
            searchContentsRepository = searchContentsRepository,
            savedStateHandle = SavedStateHandle(),
            recentSearchRepository = recentSearchRepository,
            userDataRepository = userDataRepository,
            analyticsHelper = analyticsHelper,
        )
        userDataRepository.setUserData(emptyUserData)
    }

    @Test
    fun stateIsInitiallyLoading() = runTest {
        assertEquals(Loading, viewModel.searchResultUiState.value)
    }

    @Test
    fun stateIsEmptyQuery_withEmptySearchQuery() = runTest {
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.searchResultUiState.collect() }

        viewModel.onSearchQueryChanged("")

        assertEquals(EmptyQuery, viewModel.searchResultUiState.value)
    }

    @Test
    fun emptyResultIsReturned_withNotMatchingQuery() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.searchResultUiState.collect() }

        viewModel.onSearchQueryChanged("XXX")
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)

        val result = viewModel.searchResultUiState.value
        assertIs<SearchResultUiState.Success>(result)
    }

    @Test
    fun recentSearches_verifyUiStateIsSuccess() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.recentSearchQueriesUiState.collect() }
        viewModel.onSearchTriggered("kotlin")

        val result = viewModel.recentSearchQueriesUiState.value
        assertIs<Success>(result)
    }

    @Test
    fun searchNotReady_withNoFtsTableEntity() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.searchResultUiState.collect() }

        viewModel.onSearchQueryChanged("")

        assertEquals(SearchNotReady, viewModel.searchResultUiState.value)
    }

    @Test
    fun emptySearchText_isNotAddedToRecentSearches() = runTest {
        viewModel.onSearchTriggered("")

        val recentSearchQueriesStream = getRecentQueryUseCase()
        val recentSearchQueries = recentSearchQueriesStream.first()
        val recentSearchQuery = recentSearchQueries.firstOrNull()

        assertNull(recentSearchQuery)
    }

    @Test
    fun searchTextWithThreeSpaces_isEmptyQuery() = runTest {
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchResultUiState.collect() }

        viewModel.onSearchQueryChanged("   ")

        assertIs<EmptyQuery>(viewModel.searchResultUiState.value)

        collectJob.cancel()
    }

    @Test
    fun searchTextWithThreeSpacesAndOneLetter_isEmptyQuery() = runTest {
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchResultUiState.collect() }

        viewModel.onSearchQueryChanged("   a")

        assertIs<EmptyQuery>(viewModel.searchResultUiState.value)

        collectJob.cancel()
    }

    @Test
    fun whenToggleNewsResourceSavedIsCalled_bookmarkStateIsUpdated() = runTest {
        val newsResourceId = "123"
        viewModel.setNewsResourceBookmarked(newsResourceId, true)

        assertEquals(
            expected = setOf(newsResourceId),
            actual = userDataRepository.userData.first().bookmarkedNewsResources,
        )

        viewModel.setNewsResourceBookmarked(newsResourceId, false)

        assertEquals(
            expected = emptySet(),
            actual = userDataRepository.userData.first().bookmarkedNewsResources,
        )
    }

    @Test
    fun search_withMatchingQuery_returnsSuccessWithResults() = runTest {
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.searchResultUiState.collect() }

        // "Android" appears in newsResourcesTestData[0].title
        viewModel.onSearchQueryChanged("Android")

        val result = viewModel.searchResultUiState.value
        assertIs<SearchResultUiState.Success>(result)
        assertTrue(result.newsResources.isNotEmpty(), "Should have matching news resources")
    }

    @Test
    fun search_withMatchingTopicName_returnsTopicInResults() = runTest {
        searchContentsRepository.addTopics(topicsTestData)
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.searchResultUiState.collect() }

        // "Headlines" is the name of topicsTestData[0]
        viewModel.onSearchQueryChanged("Headlines")

        val result = viewModel.searchResultUiState.value
        assertIs<SearchResultUiState.Success>(result)
        assertTrue(result.topics.any { it.topic.name == "Headlines" }, "Should match 'Headlines' topic")
    }

    @Test
    fun successState_withEmptyResults_isEmpty() = runTest {
        val emptySuccess = SearchResultUiState.Success()
        assertTrue(emptySuccess.isEmpty())

        val nonEmptySuccess = SearchResultUiState.Success(
            topics = listOf(
                FollowableTopic(
                    topic = Topic(
                        id = "1",
                        name = "Test",
                        shortDescription = "",
                        longDescription = "",
                        url = "",
                        imageUrl = "",
                    ),
                    isFollowed = false,
                ),
            ),
        )
        assertTrue(!nonEmptySuccess.isEmpty())
    }

    @Test
    fun onSearchTriggered_withValidQuery_addsToRecentSearches() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.recentSearchQueriesUiState.collect() }

        viewModel.onSearchTriggered("kotlin flows")

        val recentQueries = getRecentQueryUseCase().first()
        assertTrue(recentQueries.any { it.query == "kotlin flows" }, "Recent search should contain the query")
    }

    @Test
    fun onSearchTriggered_withValidQuery_logsAnalyticsEvent() = runTest {
        viewModel.onSearchTriggered("dagger hilt")

        val searchEvent = AnalyticsEvent(
            type = "searchQuery",
            extras = listOf(AnalyticsEvent.Param(key = "searchQuery", value = "dagger hilt")),
        )
        assertTrue(analyticsHelper.hasLogged(searchEvent), "Should log search analytics event")
    }

    @Test
    fun onSearchTriggered_withBlankQuery_doesNotSave() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.recentSearchQueriesUiState.collect() }

        viewModel.onSearchTriggered("   ")

        val recentQueries = getRecentQueryUseCase().first()
        assertTrue(recentQueries.none { it.query == "   " }, "Blank query should not be saved")
    }

    @Test
    fun clearRecentSearches_removesAllQueries() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.recentSearchQueriesUiState.collect() }

        viewModel.onSearchTriggered("kotlin")
        viewModel.onSearchTriggered("compose")

        var recentQueries = getRecentQueryUseCase().first()
        assertTrue(recentQueries.size >= 2, "Should have at least 2 recent queries before clearing")

        viewModel.clearRecentSearches()

        recentQueries = getRecentQueryUseCase().first()
        assertTrue(recentQueries.isEmpty(), "Recent searches should be empty after clearing")
    }

    @Test
    fun followTopic_addsTopicToFollowed() = runTest {
        val topicId = "topic-42"
        viewModel.followTopic(topicId, followed = true)

        val followedTopics = userDataRepository.userData.first().followedTopics
        assertTrue(topicId in followedTopics, "Topic should be in followed set")
    }

    @Test
    fun unfollowTopic_removesTopicFromFollowed() = runTest {
        val topicId = "topic-42"
        viewModel.followTopic(topicId, followed = true)
        viewModel.followTopic(topicId, followed = false)

        val followedTopics = userDataRepository.userData.first().followedTopics
        assertTrue(topicId !in followedTopics, "Topic should not be in followed set")
    }

    @Test
    fun setNewsResourceViewed_marksAsViewed() = runTest {
        val newsId = "news-99"
        viewModel.setNewsResourceViewed(newsId, viewed = true)

        val viewedResources = userDataRepository.userData.first().viewedNewsResources
        assertTrue(newsId in viewedResources, "News resource should be marked as viewed")
    }

    @Test
    fun setNewsResourceViewed_unmarksAsViewed() = runTest {
        val newsId = "news-99"
        viewModel.setNewsResourceViewed(newsId, viewed = true)
        viewModel.setNewsResourceViewed(newsId, viewed = false)

        val viewedResources = userDataRepository.userData.first().viewedNewsResources
        assertTrue(newsId !in viewedResources, "News resource should be unmarked as viewed")
    }

    @Test
    fun onSearchQueryChanged_emitsUpdatedQuery() = runTest {
        viewModel.onSearchQueryChanged("jetpack")
        assertEquals("jetpack", viewModel.searchQuery.value)
    }

    @Test
    fun recentSearchQueries_initialState_isLoading() = runTest {
        assertEquals(
            RecentSearchQueriesUiState.Loading,
            viewModel.recentSearchQueriesUiState.value,
        )
    }

    @Test
    fun recentSearchQueries_afterTrigger_changesToSuccess() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.recentSearchQueriesUiState.collect() }
        viewModel.onSearchTriggered("workmanager")

        val result = viewModel.recentSearchQueriesUiState.value
        assertIs<RecentSearchQueriesUiState.Success>(result)
    }
}
