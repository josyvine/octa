package com.example

import com.octastream.extractor.MediaExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `verify clen and user agent resolution`() {
        val sampleVrUrl = "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&clen=18452910&c=ANDROID_VR&itag=137"
        assertEquals(18452910L, MediaExtractor.extractContentLengthFromUrlParam(sampleVrUrl))
        assertTrue(MediaExtractor.resolveUserAgentForUrl(sampleVrUrl).contains("vr.oculus"))

        val sampleIosUrl = "https://rr2---sn-abc.googlevideo.com/videoplayback?clen=9481200&c=IOS&itag=137"
        assertEquals(9481200L, MediaExtractor.extractContentLengthFromUrlParam(sampleIosUrl))
        assertTrue(MediaExtractor.resolveUserAgentForUrl(sampleIosUrl).contains("com.google.ios.youtube"))
    }
}
