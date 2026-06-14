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

package com.google.samples.apps.nowinandroid.core.network.model

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun networkChangeList_deserialization_parsesCorrectly() {
        val jsonStr = """
            {
                "id": "42",
                "changeListVersion": 5,
                "isDelete": false
            }
        """.trimIndent()

        val result = json.decodeFromString<NetworkChangeList>(jsonStr)

        assertEquals("42", result.id)
        assertEquals(5, result.changeListVersion)
        assertFalse(result.isDelete)
    }

    @Test
    fun networkChangeList_deserialization_deleteFlag() {
        val jsonStr = """
            {
                "id": "99",
                "changeListVersion": 10,
                "isDelete": true
            }
        """.trimIndent()

        val result = json.decodeFromString<NetworkChangeList>(jsonStr)

        assertEquals("99", result.id)
        assertEquals(10, result.changeListVersion)
        assertTrue(result.isDelete)
    }

    @Test
    fun networkChangeList_serialization_producesCorrectJson() {
        val changeList = NetworkChangeList(
            id = "7",
            changeListVersion = 3,
            isDelete = true,
        )

        val serialized = json.encodeToString(NetworkChangeList.serializer(), changeList)

        val deserialized = json.decodeFromString<NetworkChangeList>(serialized)
        assertEquals(changeList, deserialized)
    }

    @Test
    fun networkTopic_deserialization_parsesAllFields() {
        val jsonStr = """
            {
                "id": "topic-1",
                "name": "Kotlin",
                "shortDescription": "Kotlin news",
                "longDescription": "All about Kotlin programming language",
                "url": "https://example.com/kotlin",
                "imageUrl": "https://example.com/kotlin.png",
                "followed": true
            }
        """.trimIndent()

        val result = json.decodeFromString<NetworkTopic>(jsonStr)

        assertEquals("topic-1", result.id)
        assertEquals("Kotlin", result.name)
        assertEquals("Kotlin news", result.shortDescription)
        assertEquals("All about Kotlin programming language", result.longDescription)
        assertEquals("https://example.com/kotlin", result.url)
        assertEquals("https://example.com/kotlin.png", result.imageUrl)
        assertTrue(result.followed)
    }

    @Test
    fun networkTopic_deserialization_withDefaultValues() {
        val jsonStr = """{"id": "minimal-topic"}"""

        val result = json.decodeFromString<NetworkTopic>(jsonStr)

        assertEquals("minimal-topic", result.id)
        assertEquals("", result.name)
        assertEquals("", result.shortDescription)
        assertEquals("", result.longDescription)
        assertEquals("", result.url)
        assertEquals("", result.imageUrl)
        assertFalse(result.followed)
    }

    @Test
    fun networkTopic_asExternalModel_mapsCorrectly() {
        val networkTopic = NetworkTopic(
            id = "topic-2",
            name = "Compose",
            shortDescription = "Jetpack Compose",
            longDescription = "Modern UI toolkit for Android",
            url = "https://compose.example.com",
            imageUrl = "https://compose.example.com/img.png",
            followed = true,
        )

        val topic = networkTopic.asExternalModel()

        assertEquals("topic-2", topic.id)
        assertEquals("Compose", topic.name)
        assertEquals("Jetpack Compose", topic.shortDescription)
        assertEquals("Modern UI toolkit for Android", topic.longDescription)
        assertEquals("https://compose.example.com", topic.url)
        assertEquals("https://compose.example.com/img.png", topic.imageUrl)
    }

    @Test
    fun networkNewsResource_deserialization_parsesAllFields() {
        val jsonStr = """
            {
                "id": "news-1",
                "title": "Android Dev Summit 2024",
                "content": "Join us for the biggest Android event of the year.",
                "url": "https://example.com/summit",
                "headerImageUrl": "https://example.com/summit.png",
                "publishDate": "2024-05-15T10:00:00Z",
                "type": "Article",
                "topics": ["topic-1", "topic-2"]
            }
        """.trimIndent()

        val result = json.decodeFromString<NetworkNewsResource>(jsonStr)

        assertEquals("news-1", result.id)
        assertEquals("Android Dev Summit 2024", result.title)
        assertEquals("Join us for the biggest Android event of the year.", result.content)
        assertEquals("https://example.com/summit", result.url)
        assertEquals("https://example.com/summit.png", result.headerImageUrl)
        assertEquals("Article", result.type)
        assertEquals(listOf("topic-1", "topic-2"), result.topics)
        assertEquals(
            Instant.parse("2024-05-15T10:00:00Z"),
            result.publishDate,
        )
    }

    @Test
    fun networkNewsResource_deserialization_withEmptyTopicList() {
        val jsonStr = """
            {
                "id": "news-minimal",
                "title": "Minimal",
                "content": "Minimal content",
                "url": "https://example.com",
                "headerImageUrl": "",
                "publishDate": "2024-01-01T00:00:00Z",
                "type": "Blog"
            }
        """.trimIndent()

        val result = json.decodeFromString<NetworkNewsResource>(jsonStr)

        assertEquals("news-minimal", result.id)
        assertEquals(emptyList(), result.topics)
    }

    @Test
    fun networkNewsResource_serialization_roundTrips() {
        val original = NetworkNewsResource(
            id = "n-10",
            title = "Serialization Test",
            content = "Testing round-trip serialization",
            url = "https://test.example.com",
            headerImageUrl = "https://test.example.com/img.png",
            publishDate = Instant.parse("2024-06-01T12:00:00Z"),
            type = "Codelab",
            topics = listOf("t1", "t2", "t3"),
        )

        val serialized = json.encodeToString(NetworkNewsResource.serializer(), original)
        val deserialized = json.decodeFromString<NetworkNewsResource>(serialized)

        assertEquals(original, deserialized)
    }
}
