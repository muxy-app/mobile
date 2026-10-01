package com.muxy.app.networking.muxy1

import com.muxy.app.models.FileEncoding
import com.muxy.app.models.VcsFileStatus
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.models.VcsStatus
import com.muxy.app.networking.muxy1.protocol.FileChangedEvent
import com.muxy.app.networking.muxy1.protocol.FileWriteParams
import com.muxy.app.networking.muxy1.protocol.IncomingFrame
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.RequestEnvelope
import com.muxy.app.networking.muxy1.protocol.SelectWorktreeParams
import com.muxy.app.networking.muxy1.protocol.VcsGetDiffParams
import com.muxy.app.networking.muxy1.protocol.VcsMergePullRequestParams
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GitProtocolTest {
    @Test
    fun statusDecodesFromTheMuxy1TaggedResponse() {
        val frame =
            IncomingFrame.parse(
                """
            {"type":"response","payload":{"id":"1","result":{"type":"vcsStatus","value":{
              "branch":"main","aheadCount":1,"behindCount":2,"hasUpstream":true,
              "stagedFiles":[{"path":"a.swift","status":"added","isUntracked":false}],
              "changedFiles":[{"path":"b.swift","status":"modified","isUntracked":false}],
              "defaultBranch":"main","pullRequest":{"url":"https://github.com/muxy-app/demo/pull/42",
              "number":42,"state":"OPEN","isDraft":false,"baseBranch":"main","mergeable":true,
              "mergeStateStatus":"CLEAN","checks":{"status":"success","passing":3,"failing":0,"pending":0,"total":3}}
            }}}}
            """,
            ) as IncomingFrame.Response
        val status = frame.envelope.result!!.decode<VcsStatus>()
        assertEquals("main", status.branch)
        assertEquals(1L, status.aheadCount)
        assertEquals(VcsFileStatus.ADDED, status.stagedFiles.single().status)
        assertEquals("b.swift", status.changedFiles.single().path)
        assertEquals("CLEAN", status.pullRequest?.mergeStateStatus)
        assertEquals(3L, status.pullRequest?.checks?.passing)
    }

    @Test
    fun diffUsesTheMethodTagAndExactParameterNames() {
        val params = ProtocolJson.encodeToJsonElement(VcsGetDiffParams("project", "a.swift", true))
        val payload =
            ProtocolJson
                .parseToJsonElement(RequestEnvelope.encode("1", Method.VCS_GET_DIFF, params))
                .jsonObject
                .getValue("payload")
                .jsonObject
        assertEquals("vcsGetDiff", payload.getValue("method").jsonPrimitive.content)
        val tagged = payload.getValue("params").jsonObject
        assertEquals("vcsGetDiff", tagged.getValue("type").jsonPrimitive.content)
        val value = tagged.getValue("value").jsonObject
        assertEquals("project", value.getValue("projectID").jsonPrimitive.content)
        assertEquals("a.swift", value.getValue("filePath").jsonPrimitive.content)
        assertTrue(value.getValue("forceFull").jsonPrimitive.boolean)
    }

    @Test
    fun worktreeAndMergeFieldsMatchTheWireProtocol() {
        val worktree = ProtocolJson.encodeToJsonElement(SelectWorktreeParams("p", "w")).jsonObject
        assertEquals(setOf("projectID", "worktreeID"), worktree.keys)
        val merge = ProtocolJson.encodeToJsonElement(VcsMergePullRequestParams("p", 42, VcsMergeMethod.SQUASH, true)).jsonObject
        assertEquals("squash", merge.getValue("method").jsonPrimitive.content)
        assertTrue(merge.getValue("deleteBranch").jsonPrimitive.boolean)
        assertEquals(setOf("projectID", "number", "method", "deleteBranch"), merge.keys)
    }

    @Test
    fun fileWritesAndChangeEventsPreserveEncodingAndScope() {
        val write = ProtocolJson.encodeToJsonElement(FileWriteParams("p", "a", "hello", FileEncoding.UTF8)).jsonObject
        assertEquals(setOf("projectID", "path", "contents", "encoding"), write.keys)
        assertEquals("utf8", write.getValue("encoding").jsonPrimitive.content)
        val project = UUID.randomUUID()
        val worktree = UUID.randomUUID()
        val event =
            ProtocolJson.decodeFromString<FileChangedEvent>(
                """{"projectID":"$project","worktreeID":"$worktree","paths":["a"],"truncated":true}""",
            )
        assertEquals(project, event.projectId)
        assertEquals(worktree, event.worktreeId)
        assertTrue(event.truncated)
    }
}
