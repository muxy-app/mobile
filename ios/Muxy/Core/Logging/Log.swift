import OSLog

nonisolated enum Log {
    private static let subsystem = "com.muxy.app"

    static let transport = Logger(subsystem: subsystem, category: "transport")
    static let client = Logger(subsystem: subsystem, category: "client")
    static let pairing = Logger(subsystem: subsystem, category: "pairing")
    static let connection = Logger(subsystem: subsystem, category: "connection")
    static let discovery = Logger(subsystem: subsystem, category: "discovery")
    static let persistence = Logger(subsystem: subsystem, category: "persistence")
    static let terminal = Logger(subsystem: subsystem, category: "terminal")
}
