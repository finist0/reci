import XCTest
@testable import ReciCore

final class CrashStacksTests: XCTestCase {
    // A thread in MetricKit's format: the top frame first, each caller in "subFrames".
    func thread(crashed: Bool, binary: String, uuid: String, depth: Int) -> String {
        var frame = ""
        for i in (0..<depth).reversed() {
            let sub = frame.isEmpty ? "" : #","subFrames":[\#(frame)]"#
            frame = #"{"binaryName":"\#(binary)","binaryUUID":"\#(uuid)","address":\#(0x1_0400_0000 + i * 16),"offsetIntoBinaryTextSegment":\#(i * 16),"sampleCount":1\#(sub)}"#
        }
        return #"{"threadAttributed":\#(crashed),"callStackRootFrames":[\#(frame)]}"#
    }

    func tree(_ threads: [String]) -> Data {
        Data(#"{"callStackPerThread":true,"callStacks":[\#(threads.joined(separator: ","))]}"#.utf8)
    }

    func testTheCrashedThreadComesFirstAndEachBinaryNamesItsUUIDOnce() {
        let t = CrashStacks.text(tree([thread(crashed: false, binary: "libsystem_kernel.dylib", uuid: "K", depth: 2),
                                       thread(crashed: true, binary: "Reci", uuid: "R", depth: 3)]), limit: 11000)
        XCTAssertEqual(t, """
            Thread 1, crashed:
              0 Reci 0x104000000 +0x0 R
              1 Reci 0x104000010 +0x10
              2 Reci 0x104000020 +0x20
            Thread 0:
              0 libsystem_kernel.dylib 0x104000000 +0x0 K
              1 libsystem_kernel.dylib 0x104000010 +0x10
            """)
    }

    func testThreadsThatDoNotFitAreLeftOutAndCounted() {
        let t = CrashStacks.text(tree([thread(crashed: false, binary: "Big", uuid: "B", depth: 200),
                                       thread(crashed: true, binary: "Reci", uuid: "R", depth: 50),
                                       thread(crashed: false, binary: "Small", uuid: "S", depth: 2)]), limit: 3000)
        XCTAssertLessThanOrEqual(t.count, 3000)
        XCTAssertTrue(t.hasPrefix("Thread 1, crashed:"))
        XCTAssertTrue(t.contains("  49 Reci "))
        XCTAssertTrue(t.contains("Thread 2:\n  0 Small 0x104000000 +0x0 S"))
        XCTAssertFalse(t.contains("Thread 0:"))
        XCTAssertTrue(t.hasSuffix("(1 more threads left out)"))
    }

    func testATooLongCrashedThreadKeepsItsTopFrames() {
        let t = CrashStacks.text(tree([thread(crashed: true, binary: "Reci", uuid: "R", depth: 200)]), limit: 1000)
        XCTAssertLessThanOrEqual(t.count, 1000)
        XCTAssertTrue(t.contains("  0 Reci 0x104000000 +0x0 R\n  1 Reci"))
        XCTAssertTrue(t.hasSuffix("\n  ..."))
    }

    func testAStackTreeInAnUnknownFormatIsKeptAsItIs() {
        XCTAssertEqual(CrashStacks.text(Data(#"{"new":"format"}"#.utf8), limit: 8), #"{"new":""#)
    }
}
