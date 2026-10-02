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

    @Test func decodesFloatingPointSortOrder() throws {
        let json = """
        {
          "id": "00000000-0000-0000-0000-000000000001",
          "name": "Home",
          "path": "/Users/saeed",
          "sortOrder": -9.223372036854776e+18,
          "createdAt": "2026-06-07T20:48:51Z"
        }
        """

        let project = try decodeProject(json)

        #expect(project.name == "Home")
        #expect(project.sortOrder < 0)
    }

    @Test func projectsResultDecodesArrayWithFloatingPointSortOrder() throws {
        let json = """
        [
          { "id": "00000000-0000-0000-0000-000000000001", "name": "Home", "path": "/Users/saeed", "sortOrder": -9.223372036854776e+18, "createdAt": "2026-06-07T20:48:51Z" },
          { "id": "22222222-2222-2222-2222-222222222222", "name": "muxy", "path": "/p", "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" }
        ]
        """

        let result = try JSONDecoder().decode(ProjectsResult.self, from: Data(json.utf8))
        let sorted = result.projects.sorted { $0.sortOrder < $1.sortOrder }

        #expect(result.projects.count == 2)
        #expect(sorted.first?.name == "Home")
    }
}
