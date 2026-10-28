package com.vyoma.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiModelEndpointResolverTest {
    @Test
    fun resolvesFullChatCompletionsEndpoint() {
        assertEquals(
            "https://api.example.com/v1/models",
            AiModelEndpointResolver.resolve("https://api.example.com/v1/chat/completions")
        )
    }

    @Test
    fun resolvesVersionBaseEndpoint() {
        assertEquals(
            "https://api.example.com/v1/models",
            AiModelEndpointResolver.resolve("https://api.example.com/v1")
        )
    }

    @Test
    fun resolvesRootEndpoint() {
        assertEquals(
            "https://api.example.com/v1/models",
            AiModelEndpointResolver.resolve("https://api.example.com")
        )
    }

    @Test
    fun rejectsPlainHttpEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {
            AiModelEndpointResolver.resolve("http://api.example.com/v1/chat/completions")
        }
    }
}
