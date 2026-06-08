import Foundation
import Testing
@testable import Muxy

struct ProjectDecodingTests {
    private func decodeProject(_ json: String) throws -> Project {
        try JSONDecoder().decode(Project.self, from: Data(json.utf8))
    }

    @Test func decodesProjectWithAllFields() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "muxy",
          "path": "/Users/example/project",
          "sortOrder": 3,
          "createdAt": "2026-04-19T10:00:00Z",
          "icon": "hammer",
          "logo": "a1b2c3d4",
          "iconColor": "#7C3AED",
          "preferredWorktreeParentPath": "/Users/example"
        }
        """

        let project = try decodeProject(json)

        #expect(project.name == "muxy")
        #expect(project.sortOrder == 3)
        #expect(project.icon == "hammer")
        #expect(project.iconColor == "#7C3AED")
        #expect(project.preferredWorktreeParentPath == "/Users/example")
    }

    @Test func decodesProjectWithOptionalsOmitted() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "muxy",
          "path": "/Users/example/project",
          "sortOrder": 0,
          "createdAt": "2026-04-19T10:00:00Z"
        }
        """

        let project = try decodeProject(json)

        #expect(project.icon == nil)
        #expect(project.logo == nil)
        #expect(project.iconColor == nil)
        #expect(project.preferredWorktreeParentPath == nil)
    }

    @Test func projectsResultDecodesWrappedObject() throws {
        let json = """
        { "projects": [
          { "id": "11111111-1111-1111-1111-111111111111", "name": "a", "path": "/a", "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" }
        ] }
        """

        let result = try JSONDecoder().decode(ProjectsResult.self, from: Data(json.utf8))

        #expect(result.projects.count == 1)
        #expect(result.projects[0].name == "a")
    }

    @Test func projectsResultDecodesBareArray() throws {
        let json = """
        [
          { "id": "11111111-1111-1111-1111-111111111111", "name": "a", "path": "/a", "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" },
          { "id": "22222222-2222-2222-2222-222222222222", "name": "b", "path": "/b", "sortOrder": 1, "createdAt": "2026-04-19T10:00:00Z" }
        ]
        """

        let result = try JSONDecoder().decode(ProjectsResult.self, from: Data(json.utf8))

        #expect(result.projects.count == 2)
        #expect(result.projects[1].name == "b")
    }
}
