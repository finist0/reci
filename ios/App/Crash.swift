import Foundation
import MetricKit
import ReciCore

// iOS hands the app the report of its crash at the next start, with the call stacks. The app
// keeps it and asks the user before sending it to the developer.
final class CrashWatcher: NSObject, MXMetricManagerSubscriber {
    private let found: (String) -> Void

    init(found: @escaping (String) -> Void) {
        self.found = found
        super.init()
        MXMetricManager.shared.add(self)
    }

    deinit {
        MXMetricManager.shared.remove(self)
    }

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        for p in payloads {
            for c in p.crashDiagnostics ?? [] { found(Self.report(c, at: p.timeStampEnd)) }
        }
    }

    static func report(_ c: MXCrashDiagnostic, at date: Date) -> String {
        let m = c.metaData
        var lines = ["\(ISO8601DateFormatter().string(from: date)), build \(m.applicationBuildVersion), \(m.osVersion), \(m.deviceType)"]
        if let v = c.exceptionType { lines.append("exception type \(v)") }
        if let v = c.exceptionCode { lines.append("exception code \(v)") }
        if let v = c.signal { lines.append("signal \(v)") }
        if let v = c.terminationReason { lines.append(v) }
        let head = lines.joined(separator: "\n")
        return head + "\n" + CrashStacks.text(c.callStackTree.jsonRepresentation(), limit: CrashReport.maxLength - head.count - 1)
    }
}
