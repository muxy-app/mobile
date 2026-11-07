package com.muxy.app.features.server.terminal

import com.muxy.app.features.terminal.TerminalController
import com.muxy.app.features.terminal.images.TerminalImage
import com.muxy.app.testing.FakeTerminalChannel
import com.muxy.app.testing.FakeTerminalSource
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.MobileException

class SdkTerminalImagesTest {
    private val png = TerminalImage.sentAsIs(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))!!
    private val jpeg = TerminalImage.jpeg(byteArrayOf(1, 2, 3))

    private fun TestScope.attached(channel: FakeTerminalChannel): TerminalController {
        val source = SdkTerminalSource(backgroundScope, serverName = { "Studio" }) {}
        val controller = TerminalController(source, backgroundScope)
        source.attach(channel)
        return controller
    }

    @Test
    fun everyImageIsUploadedThenAllPathsArePastedOnce() =
        runTest {
            val channel = FakeTerminalChannel(1u)
            val controller = attached(channel)
            controller.sendImages { listOf(png, jpeg) }
            runCurrent()
            assertEquals(listOf("pasted-image.png", "pasted-image.jpg"), channel.uploads)
            assertEquals(
                listOf(listOf("/tmp/muxy-uploads/1/1-pasted-image.png", "/tmp/muxy-uploads/1/2-pasted-image.jpg")),
                channel.pastedPaths,
            )
            assertTrue(channel.pastes.isEmpty())
            assertNull(controller.sendingImagesNotice.value)
            assertNull(controller.notice.value)
        }

    @Test
    fun aFailedUploadExplainsWhyAndPastesNothing() =
        runTest {
            val channel = FakeTerminalChannel(1u).apply { uploadFailure = MobileException.Unsupported() }
            val controller = attached(channel)
            controller.sendImages { listOf(png) }
            runCurrent()
            assertTrue(channel.pastedPaths.isEmpty())
            assertEquals("Update Muxy on your phone and computer to use this.", controller.notice.value)
            assertNull(controller.sendingImagesNotice.value)
        }

    @Test
    fun unreadableImagesAreNotSent() =
        runTest {
            val channel = FakeTerminalChannel(1u)
            val controller = attached(channel)
            controller.sendImages { emptyList() }
            runCurrent()
            assertTrue(channel.uploads.isEmpty())
            assertEquals(TerminalController.IMAGE_UNREADABLE, controller.notice.value)
        }

    @Test
    fun imagesOverTheLimitAreNotSent() =
        runTest {
            val channel = FakeTerminalChannel(1u)
            val controller = attached(channel)
            controller.sendImages { listOf(png, TerminalImage.jpeg(ByteArray(TerminalImage.MAXIMUM_BYTES + 1))) }
            runCurrent()
            assertTrue(channel.uploads.isEmpty())
            assertEquals(TerminalController.IMAGE_TOO_LARGE, controller.notice.value)
        }

    @Test
    fun aReleasedTerminalUploadsNothing() =
        runTest {
            val channel = FakeTerminalChannel(1u)
            val source = SdkTerminalSource(backgroundScope, serverName = { "Studio" }) {}
            val controller = TerminalController(source, backgroundScope)
            source.attach(channel)
            source.release()
            controller.sendImages { listOf(png) }
            runCurrent()
            assertTrue(channel.uploads.isEmpty())
            assertTrue(channel.pastedPaths.isEmpty())
        }

    @Test
    fun onlyServerTerminalsAcceptImages() =
        runTest {
            assertTrue(attached(FakeTerminalChannel(1u)).acceptsImages)
            assertFalse(TerminalController(FakeTerminalSource(), backgroundScope).acceptsImages)
        }

    @Test
    fun theSendingNoticeCountsImages() {
        assertEquals("Sending image…", TerminalController.sendingNotice(1))
        assertEquals("Sending 3 images…", TerminalController.sendingNotice(3))
    }
}
