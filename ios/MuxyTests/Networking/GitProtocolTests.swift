import Foundation
import Testing
@testable import Muxy

struct GitProtocolTests {
    private func requestMethod(from frame: String) -> String? {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any]
        else { return nil }
        return payload["method"] as? String
    }

    private func requestParams(from frame: String) -> [String: Any]? {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any],
            let params = payload["params"] as? [String: Any]
        else { return nil }
        return params
    }

    @Test func vcsStatusDecodesFromTaggedResult() throws {
        let json = """
        { "type": "response", "payload": { "id": "1", "result": { "type": "vcsStatus", "value": {
          "branch": "main",
          "aheadCount": 1,
          "behindCount": 2,
          "hasUpstream": true,
          "stagedFiles": [{ "path": "a.swift", "status": "added", "isUntracked": false }],
          "changedFiles": [{ "path": "b.swift", "status": "modified", "isUntracked": false }],
          "defaultBranch": "main",
          "pullRequest": {
            "url": "https://github.com/muxy-app/demo/pull/42",
            "number": 42,
            "state": "OPEN",
            "isDraft": false,
            "baseBranch": "main",
            "mergeable": true,
            "mergeStateStatus": "CLEAN",
            "checks": { "status": "success", "passing": 3, "failing": 0, "pending": 0, "total": 3 }
          }
        } } } }
        """
        let frame = try IncomingFrame(json: Data(json.utf8))
        guard case let .response(response) = frame else {
            Issue.record("Expected response")
            return
        }

        let status = try #require(response.result).decode(VCSStatus.self)

        #expect(status.branch == "main")
        #expect(status.aheadCount == 1)
        #expect(status.stagedFiles.first?.status == .added)
        #expect(status.changedFiles.first?.path == "b.swift")
        #expect(status.pullRequest?.mergeStateStatus == .clean)
        #expect(status.pullRequest?.checks?.passing == 3)
    }

    @Test func vcsGetDiffRequestUsesTaggedParams() async throws {
        let transport = MockTransport { frame in
            guard
                let data = frame.data(using: .utf8),
                let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                let payload = object["payload"] as? [String: Any],
                let id = payload["id"] as? String
            else { return [] }
            return ["""
            { "type": "response", "payload": { "id": "\(id)", "result": { "type": "vcsDiff", "value": {
              "filePath": "a.swift",
              "rows": [],
              "additions": 0,
              "deletions": 0,
              "truncated": false,
              "isBinary": false
            } } } }
            """]
        }
        let client = MuxyClient(transport: transport)
        await client.start()

        let result = try await client.request(.vcsGetDiff, params: VCSGetDiffParams(projectID: "p", filePath: "a.swift", forceFull: true))
        let frames = await transport.sentFrames
        let frame = try #require(frames.first)
        let params = try #require(requestParams(from: frame))
        let value = try #require(params["value"] as? [String: Any])

        #expect(result.type == ResultType.vcsDiff)
        #expect(requestMethod(from: frame) == Method.vcsGetDiff.rawValue)
        #expect(params["type"] as? String == Method.vcsGetDiff.rawValue)
        #expect(value["projectID"] as? String == "p")
        #expect(value["filePath"] as? String == "a.swift")
        #expect(value["forceFull"] as? Bool == true)

        await client.stop()
    }
}
