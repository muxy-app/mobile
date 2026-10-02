import Foundation
import MuxyMobile

nonisolated extension RemoteFileEntry {
    init(_ entry: FileEntry) {
        self.init(name: entry.name, path: entry.path, isDirectory: entry.isDirectory, isIgnored: entry.isIgnored)
    }
}

nonisolated extension RemoteFileStat {
    init(_ info: FileInfo) {
        self.init(name: info.name, path: info.path, isDirectory: info.isDirectory, size: Int(clamping: info.size))
    }
}

extension FileLocation {
    init(serverProject project: ServerProject, serverName: String) {
        self.init(
            name: project.name,
            path: project.directory,
            icon: ServerProjectListing.icon(for: project),
            host: .computer(serverName)
        )
    }
}
