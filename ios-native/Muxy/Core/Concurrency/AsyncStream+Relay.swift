import Foundation

nonisolated extension AsyncStream where Element: Sendable {
    static func relaying<Source: Sendable>(
        _ source: AsyncStream<Source>,
        _ transform: @escaping @Sendable (Source) -> Element?
    ) -> AsyncStream<Element> {
        let (stream, continuation) = AsyncStream.makeStream(of: Element.self)
        let relay = Task {
            for await value in source {
                guard let element = transform(value) else { continue }
                continuation.yield(element)
            }
            continuation.finish()
        }
        continuation.onTermination = { _ in relay.cancel() }
        return stream
    }
}
