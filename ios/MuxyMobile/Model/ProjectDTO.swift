import Foundation

struct ProjectDTO: Identifiable, Codable, Hashable {
    let id: UUID
    var name: String
    var path: String
    var sortOrder: Int
    var createdAt: Date
    var icon: String?
    var logo: String?
    var iconColor: String?

    init(
        id: UUID,
        name: String,
        path: String,
        sortOrder: Int,
        createdAt: Date,
        icon: String? = nil,
        logo: String? = nil,
        iconColor: String? = nil
    ) {
        self.id = id
        self.name = name
        self.path = path
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.icon = icon
        self.logo = logo
        self.iconColor = iconColor
    }
}
