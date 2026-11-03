import Foundation

// MetricKit's call stacks of a crash as text: the crashed thread first, then every other thread
// that still fits. A frame is read with the dSYM of the same build:
//   atos -o <dSYM> -l <address - offset> <address>
// The UUID that picks the dSYM is given at the first frame of each binary.
public enum CrashStacks {
    struct Tree: Decodable {
        let callStacks: [Stack]
    }

    struct Stack: Decodable {
        let threadAttributed: Bool?
        let callStackRootFrames: [Frame]
    }

    struct Frame: Decodable {
        let binaryName: String?
        let binaryUUID: String?
        let address: UInt64?
        let offsetIntoBinaryTextSegment: UInt64?
        let subFrames: [Frame]?
    }

    public static func text(_ json: Data, limit: Int) -> String {
        guard let tree = try? JSONDecoder().decode(Tree.self, from: json) else {
            return String(String(decoding: json, as: UTF8.self).prefix(limit))
        }
        let threads = Array(tree.callStacks.enumerated())
        let order = threads.filter { $0.element.threadAttributed == true } + threads.filter { $0.element.threadAttributed != true }
        // Room for the line that tells how many threads were left out.
        let room = limit - 40
        var out: [String] = []
        var used = 0
        var seen = Set<String>()
        var left = 0
        for (n, stack) in order {
            var known = seen
            var lines = [stack.threadAttributed == true ? "Thread \(n), crashed:" : "Thread \(n):"]
            var i = 0
            func add(_ f: Frame) {
                var line = "  \(i) \(f.binaryName ?? "?") 0x\(String(f.address ?? 0, radix: 16)) +0x\(String(f.offsetIntoBinaryTextSegment ?? 0, radix: 16))"
                if let u = f.binaryUUID, known.insert(u).inserted { line += " \(u)" }
                lines.append(line)
                i += 1
                f.subFrames?.forEach(add)
            }
            stack.callStackRootFrames.forEach(add)
            let size = lines.reduce(0) { $0 + $1.count + 1 }
            if used + size <= room {
                out += lines
                used += size
                seen = known
            } else if out.isEmpty {
                // The crashed thread alone is too long: keep its top frames.
                for l in lines {
                    if used + l.count + 1 > room { break }
                    out.append(l)
                    used += l.count + 1
                }
                out.append("  ...")
            } else {
                left += 1
            }
        }
        if left > 0 { out.append("(\(left) more threads left out)") }
        return out.joined(separator: "\n")
    }
}
