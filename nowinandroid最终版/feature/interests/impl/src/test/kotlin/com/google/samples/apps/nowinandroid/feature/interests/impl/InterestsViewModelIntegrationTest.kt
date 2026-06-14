package com.google.samples.apps.nowinandroid.feature.interests.impl

import androidx.lifecycle.SavedStateHandle
import com.google.samples.apps.nowinandroid.core.data.repository.TopicsRepository
import com.google.samples.apps.nowinandroid.core.domain.GetFollowableTopicsUseCase
import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.feature.interests.api.navigation.InterestsNavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [InterestsViewModel] testing the complete flow
 * from user interactions through ViewModel to Repository layer.
 *
 * Uses backgroundScope + collect pattern because InterestsViewModel uses
 * stateIn(WhileSubscribed) which cancels upstream on subscriber loss.
 */
class InterestsViewModelIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val userDataRepository = TestUserDataRepository()
    private val topicsRepository = TestTopicsRepository()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows loading then topics`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        // Read Loading state BEFORE collector starts, otherwise combine emits immediately
        val initialState = viewModel.uiState.value
        assertIs<InterestsUiState.Loading>(initialState)

        // Keep collector alive so WhileSubscribed stays subscribed
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        // Wait for topics to load
        advanceUntilIdle()

        val loadedState = viewModel.uiState.value
        assertIs<InterestsUiState.Interests>(loadedState)
        assertTrue(loadedState.topics.isNotEmpty())
    }

    @Test
    fun `topics are sorted by name`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as InterestsUiState.Interests
        val topics = state.topics

        val topicNames = topics.map { it.topic.name }
        val sortedNames = topicNames.sorted()
        assertEquals(sortedNames, topicNames)
    }

    @Test
    fun `follow topic updates followed state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as InterestsUiState.Interests
        val firstTopic = state.topics.first()

        viewModel.followTopic(firstTopic.topic.id, true)
        advanceUntilIdle()

        val userData = userDataRepository.userData.first()
        assertTrue(firstTopic.topic.id in userData.followedTopics)

        val updatedState = viewModel.uiState.value as InterestsUiState.Interests
        val updatedTopic = updatedState.topics.find { it.topic.id == firstTopic.topic.id }
        assertNotNull(updatedTopic)
        assertTrue(updatedTopic.isFollowed)
    }

    @Test
    fun `unfollow topic removes from followed state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as InterestsUiState.Interests
        val firstTopic = state.topics.first()

        viewModel.followTopic(firstTopic.topic.id, true)
        advanceUntilIdle()
        assertTrue(userDataRepository.userData.first().followedTopics.contains(firstTopic.topic.id))

        viewModel.followTopic(firstTopic.topic.id, false)
        advanceUntilIdle()
        assertFalse(userDataRepository.userData.first().followedTopics.contains(firstTopic.topic.id))

        val updatedState = viewModel.uiState.value as InterestsUiState.Interests
        val updatedTopic = updatedState.topics.find { it.topic.id == firstTopic.topic.id }
        assertNotNull(updatedTopic)
        assertFalse(updatedTopic.isFollowed)
    }

    @Test
    fun `select topic updates selected topic id`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as InterestsUiState.Interests
        val topicToSelect = state.topics.firstOrNull()

        if (topicToSelect != null) {
            viewModel.onTopicClick(topicToSelect.topic.id)
            advanceUntilIdle()

            val updatedState = viewModel.uiState.value as InterestsUiState.Interests
            assertEquals(topicToSelect.topic.id, updatedState.selectedTopicId)
        }
    }

    @Test
    fun `deselect topic clears selected topic id`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as InterestsUiState.Interests
        val topicToSelect = state.topics.firstOrNull()

        if (topicToSelect != null) {
            viewModel.onTopicClick(topicToSelect.topic.id)
            advanceUntilIdle()

            var updatedState = viewModel.uiState.value as InterestsUiState.Interests
            assertEquals(topicToSelect.topic.id, updatedState.selectedTopicId)

            viewModel.onTopicClick(null)
            advanceUntilIdle()

            updatedState = viewModel.uiState.value as InterestsUiState.Interests
            assertNull(updatedState.selectedTopicId)
        }
    }

    @Test
    fun `follow multiple topics works correctly`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as InterestsUiState.Interests
        val topicsToFollow = state.topics.take(3)

        topicsToFollow.forEach { followableTopic ->
            viewModel.followTopic(followableTopic.topic.id, true)
        }
        advanceUntilIdle()

        val userData = userDataRepository.userData.first()
        topicsToFollow.forEach { followableTopic ->
            assertTrue(
                followableTopic.topic.id in userData.followedTopics,
                "Topic ${followableTopic.topic.id} should be followed",
            )
        }

        val updatedState = viewModel.uiState.value as InterestsUiState.Interests
        topicsToFollow.forEach { followableTopic ->
            val updatedTopic = updatedState.topics.find { it.topic.id == followableTopic.topic.id }
            assertNotNull(updatedTopic)
            assertTrue(updatedTopic.isFollowed)
        }
    }

    @Test
    fun `toggle follow topic works correctly`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as InterestsUiState.Interests
        val firstTopic = state.topics.first()

        viewModel.followTopic(firstTopic.topic.id, true)
        advanceUntilIdle()
        assertTrue(userDataRepository.userData.first().followedTopics.contains(firstTopic.topic.id))

        viewModel.followTopic(firstTopic.topic.id, false)
        advanceUntilIdle()
        assertFalse(userDataRepository.userData.first().followedTopics.contains(firstTopic.topic.id))
    }

    @Test
    fun `complete follow journey works end to end`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        advanceUntilIdle()
        var state = viewModel.uiState.value as InterestsUiState.Interests

        val firstTopic = state.topics.first()
        viewModel.onTopicClick(firstTopic.topic.id)
        viewModel.followTopic(firstTopic.topic.id, true)
        advanceUntilIdle()

        state = viewModel.uiState.value as InterestsUiState.Interests
        val followedFirst = state.topics.find { it.topic.id == firstTopic.topic.id }
        assertNotNull(followedFirst)
        assertTrue(followedFirst.isFollowed)
        assertEquals(firstTopic.topic.id, state.selectedTopicId)

        val secondTopic = state.topics.getOrNull(1)
        if (secondTopic != null) {
            viewModel.followTopic(secondTopic.topic.id, true)
            advanceUntilIdle()

            state = viewModel.uiState.value as InterestsUiState.Interests
            val followedSecond = state.topics.find { it.topic.id == secondTopic.topic.id }
            assertNotNull(followedSecond)
            assertTrue(followedSecond.isFollowed)
        }

        viewModel.followTopic(firstTopic.topic.id, false)
        advanceUntilIdle()

        state = viewModel.uiState.value as InterestsUiState.Interests
        val unfollowedFirst = state.topics.find { it.topic.id == firstTopic.topic.id }
        assertNotNull(unfollowedFirst)
        assertFalse(unfollowedFirst.isFollowed)

        val userData = userDataRepository.userData.first()
        if (secondTopic != null) {
            assertTrue(secondTopic.topic.id in userData.followedTopics)
        }
        assertFalse(firstTopic.topic.id in userData.followedTopics)
    }

    private fun createViewModel(): InterestsViewModel {
        return InterestsViewModel(
            savedStateHandle = SavedStateHandle(),
            userDataRepository = userDataRepository,
            getFollowableTopics = GetFollowableTopicsUseCase(topicsRepository, userDataRepository),
            key = InterestsNavKey(initialTopicId = null),
        )
    }
}

private class TestTopicsRepository : TopicsRepository {
    private val _topics = MutableStateFlow<List<Topic>>(
        listOf(
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
            Topic(
                id = "topic3",
                name = "Kotlin",
                shortDescription = "Kotlin language",
                longDescription = "Kotlin programming",
                url = "https://example.com/kotlin",
                imageUrl = "https://example.com/kotlin.jpg",
            ),
        ),
    )

    override fun getTopics(): Flow<List<Topic>> = _topics

    override fun getTopic(id: String): Flow<Topic> = _topics.map { list -> list.first { t -> t.id == id } }

    override suspend fun syncWith(synchronizer: com.google.samples.apps.nowinandroid.core.data.Synchronizer) = true
}
