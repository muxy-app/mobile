package com.muxy.app.networking.server.sdk

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SdkToolLanesTest {
    @Test
    fun blockingFilesAndGitDoNotBlockEachOtherOrTerminalInput() =
        runBlocking {
            val lanes = SdkLanes()
            val filesEntered = CountDownLatch(1)
            val gitEntered = CountDownLatch(1)
            val inputSent = CountDownLatch(1)
            val release = CountDownLatch(1)
            val files =
                async {
                    lanes.fileRequest {
                        filesEntered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        "files"
                    }
                }
            val git =
                async {
                    lanes.gitRequest {
                        gitEntered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        "git"
                    }
                }
            kotlinx.coroutines.yield()
            try {
                assertTrue(filesEntered.await(5, TimeUnit.SECONDS))
                assertTrue(gitEntered.await(5, TimeUnit.SECONDS))
                lanes.send { inputSent.countDown() }
                assertTrue(inputSent.await(5, TimeUnit.SECONDS))
                assertEquals("request", lanes.request { "request" })
            } finally {
                release.countDown()
                lanes.close()
            }
            assertEquals("files", files.await())
            assertEquals("git", git.await())
        }
}
