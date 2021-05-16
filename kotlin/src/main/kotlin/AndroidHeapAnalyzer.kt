import org.jetbrains.annotations.NotNull
import java.io.File
import java.io.RandomAccessFile
import java.lang.Exception
import java.util.*
import kotlin.collections.HashMap

class AndroidHeapAnalyzer(val stream: RandomAccessFile, @NotNull val logger: Logger = DefaultLogger()) {

    private var identifierSize = 4
    private val typeSizeMap = BASIC_TYPES.toMutableMap()

    private val tagCounter = HashMap<String, Int>()
    private val heapTagCounter = HashMap<String, Int>()

    constructor(hprof: File) : this(RandomAccessFile(hprof, "r"))

    private fun readHead() {
        val versionBytes = mutableListOf<Byte>()
        while (true) {
            val b = stream.readByte()
            if (b == 0.toByte()) {
                break
            }
            versionBytes.add(b)
        }
        val version = String(versionBytes.toByteArray())
        identifierSize = stream.readInt()
        val timestamp = stream.readLong() / 1000

        typeSizeMap[TYPE_OBJECT] = identifierSize

        log("Version: $version")
        log("Identifier Size: $identifierSize")
        log("Created Time: ${Date(timestamp)}")
    }

    private fun readRecords() {
        val fileLength = stream.length()
        while (stream.filePointer < fileLength) {
            val tag = stream.read()
            val time = stream.readInt()
            val length = stream.readInt()

            when (tag) {
                TAG_STRING -> {
                    readString(length)
                    continue
                }
                TAG_LOAD_CLASS -> counter("TAG_LOAD_CLASS")
                TAG_UNLOAD_CLASS -> counter("TAG_UNLOAD_CLASS")
                TAG_STACK_FRAME -> counter("TAG_STACK_FRAME")
                TAG_STACK_TRACE -> {
                    readStackTrace()
                    continue
                }
                TAG_ALLOC_SITES -> counter("TAG_ALLOC_SITES")
                TAG_HEAP_SUMMARY -> counter("TAG_HEAP_SUMMARY")
                TAG_START_THREAD -> counter("TAG_START_THREAD")
                TAG_END_THREAD -> counter("TAG_END_THREAD")
                TAG_HEAP_DUMP -> {
                    readHeapDump(length)
                    continue
                }
                TAG_HEAP_SEGMENT -> {
                    readHeapSegmentDump(length)
                    continue
                }
                TAG_HEAP_DUMP_END -> counter("TAG_HEAP_DUMP_END")
                TAG_CPU_SAMPLES -> counter("TAG_CPU_SAMPLES")
                TAG_CONTROL_SETTINGS -> counter("TAG_CONTROL_SETTINGS")
                else -> throw Exception("Not supported tag: $tag, position: ${stream.filePointer}")
            }

            seek(length)
        }
    }

    private fun readString(length: Int) {
        counter("STRING")
        log("STRING(0x01)")
        val remaining = length - identifierSize
        log("    id: ${readId()}")
        val bytes = ByteArray(remaining)
        stream.readFully(bytes)
        log("    content: ${String(bytes)}")
    }

    private fun readStackTrace() {
        counter("STACK_TRACE")
        log("STACK_TRACE(0x05)")
        log("    serial number: ${stream.readInt()}")
        log("    thread serial number: ${stream.readInt()}")
        val frameCount = stream.readInt()
        log("    number of frames: $frameCount")
        val ids = mutableListOf<Long>()
        for (i in 0 until frameCount) {
            ids.add(readId())
        }
        log("    series of stack frame ID's: $ids")
    }

    private fun readHeapDump(length: Int) {
        counter("HEAP_DUMP")
        log("HEAP_DUMP(0x0C)")
        readHeapDumpInternal(length)
    }

    private fun readHeapSegmentDump(length: Int) {
        counter("HEAP_DUMP_SEGMENT")
        log("HEAP_DUMP_SEGMENT(0x1C)")
        readHeapDumpInternal(length)
    }

    private fun readHeapDumpInternal(length: Int) {
        var available = length.toLong()
        while (available > 0) {
            val start = stream.filePointer

            val tag = stream.read()

            when (tag) {
                HEAP_TAG_ROOT_UNKNOWN -> readRootUnknown()
                HEAP_TAG_ROOT_JNI_GLOBAL -> readRootJniGlobal()
                HEAP_TAG_ROOT_JNI_LOCAL -> readRootJniLocal()
                HEAP_TAG_ROOT_JAVA_FRAME -> readRootJavaFrame()
                HEAP_TAG_ROOT_NATIVE_STACK -> readRootNativeStack()
                HEAP_TAG_ROOT_STICKY_CLASS -> readRootStickyClass()
                HEAP_TAG_ROOT_THREAD_BLOCK -> readRootThreadBlock()
                HEAP_TAG_ROOT_MONITOR_USED -> readRootMonitorUsed()
                HEAP_TAG_ROOT_THREAD_OBJECT -> readRootThreadObject()
                HEAP_TAG_CLASS_DUMP -> readClassDump()
                HEAP_TAG_INSTANCE_DUMP -> readInstanceDump()
                HEAP_TAG_OBJECT_ARRAY_DUMP -> readObjectArrayDump()
                HEAP_TAG_PRIMITIVE_ARRAY_DUMP -> readPrimitiveArrayDump()
                // Android Special
                HEAP_TAG_ROOT_INTERNED_STRING -> readRootInternedString()
                HEAP_TAG_ROOT_FINALIZING -> readRootFinalizing()
                HEAP_TAG_ROOT_DEBUGGER -> readRootDebugger()
                HEAP_TAG_ROOT_REFERENCE_CLEANUP -> readRootReferenceCleanup()
                HEAP_TAG_ROOT_VM_INTERNAL -> readRootVmInternal()
                HEAP_TAG_ROOT_JNI_MONITOR -> readRootJniMonitor()
                HEAP_TAG_HEAP_DUMP_INFO -> readHeapDumpInfo()
                else -> throw Exception("Not supported heap dump tag: $tag, position: ${stream.filePointer}")
            }

            val end = stream.filePointer
            available -= end - start
        }
    }

    private fun readRootUnknown() {
        counterHeap("ROOT_UNKNOWN")
        log("    ROOT_UNKNOWN(0xFF)")
        log("        id: ${readId()}")
    }

    private fun readRootJniGlobal() {
        counterHeap("ROOT_JNI_GLOBAL")
        log("    ROOT_JNI_GLOBAL(0x01)")
        log("        id: ${readId()}")
        log("        JNI global ref id: ${readId()}")
    }

    private fun readRootJniLocal() {
        counterHeap("ROOT_JNI_LOCAL")
        log("    ROOT_JNI_LOCAL(0x02)")
        log("        id: ${readId()}")
        log("        thread serial number: ${stream.readInt()}")
        log("        frame number: ${stream.readInt()}")
    }

    private fun readRootJavaFrame() {
        counterHeap("ROOT_JAVA_FRAME")
        log("    ROOT_JNI_FRAME(0x03)")
        log("        id: ${readId()}")
        log("        thread serial number: ${stream.readInt()}")
        log("        frame number: ${stream.readInt()}")
    }

    private fun readRootNativeStack() {
        counterHeap("ROOT_NATIVE_STACK")
        log("    ROOT_NATIVE_STACK(0x04)")
        log("        id: ${readId()}")
        log("        thread serial number: ${stream.readInt()}")
    }

    private fun readRootStickyClass() {
        counterHeap("ROOT_STICKY_CLASS")
        log("    ROOT_STICKY_CLASS(0x05)")
        log("        id: ${readId()}")
    }

    private fun readRootThreadBlock() {
        counterHeap("ROOT_THREAD_BLOCK")
        log("    ROOT_THREAD_BLOCK(0x06)")
        log("        id: ${readId()}")
        log("        thread serial number: ${stream.readInt()}")
    }

    private fun readRootMonitorUsed() {
        counterHeap("ROOT_MONITOR_USED")
        log("    ROOT_MONITOR_USED(0x07)")
        log("        id: ${readId()}")
    }

    private fun readRootThreadObject() {
        counterHeap("ROOT_THREAD_OBJECT")
        log("    ROOT_THREAD_OBJECT(0x08)")
        log("        id: ${readId()}")
        log("        thread serial number: ${stream.readInt()}")
        log("        stack trace serial number: ${stream.readInt()}")
    }

    private fun readClassDump() {
        counterHeap("CLASS_DUMP")
        log("    CLASS_DUMP(0x20)")
        log("        id: ${readId()}")
        log("        stack trace serial number: ${stream.readInt()}")
        log("        super class id: ${readId()}")
        log("        class loader id: ${readId()}")
        log("        signers id: ${readId()}")
        log("        protection domain id: ${readId()}")
        seek((identifierSize * 2))
        log("        instance size: ${stream.readInt()}")
        readClassConstantFields()
        readClassStaticFields()
        readInstanceFields()
    }

    private fun readClassConstantFields() {
        var count = stream.readShort().toInt()
        log("        number of constant fields: $count")
        while (count > 0) {
            count -= 1

            seek(2)

            val type = stream.read()

            val size = BASIC_TYPES[type]
            if (size == null) {
                throw Exception("readClassConstantFields() not supported type: $type")
            } else {
                seek(size)
            }
        }
    }

    private fun readClassStaticFields() {
        var count = stream.readShort().toInt()
        log("        number of static fields:: $count")
        while (count > 0) {
            count -= 1

            seek(identifierSize)

            val type = stream.read()

            val size = BASIC_TYPES[type]
            if (size == null) {
                throw Exception("readClassStaticFields() not supported type: $type")
            } else {
                seek(size)
            }
        }
    }

    private fun readInstanceFields() {
        val count = stream.readShort().toInt()
        log("        number of instance fields: $count")
        seek((count * (identifierSize + 1)))
    }

    private fun readInstanceDump() {
        counterHeap("INSTANCE_DUMP")
        log("    INSTANCE_DUMP(0x21)")
        log("        id: ${readId()}")
        log("        stack trace serial number: ${stream.readInt()}")
        log("        class id: ${readId()}")
        val count = stream.readInt()
        log("        fields byte size: $count")
        seek(count)
    }

    private fun readObjectArrayDump() {
        counterHeap("OBJECT_ARRAY_DUMP")
        log("    OBJECT_ARRAY_DUMP(0x22)")
        log("        id: ${readId()}")
        log("        stack trace serial number: ${stream.readInt()}")
        val length = stream.readInt()
        log("        length: $length")
        log("        array class id: ${readId()}")
        seek((identifierSize * length))
    }

    private fun readPrimitiveArrayDump() {
        counterHeap("PRIMITIVE_ARRAY_DUMP")
        log("    PRIMITIVE_ARRAY_DUMP(0x23)")
        log("        id: ${readId()}")
        log("        stack trace serial number: ${stream.readInt()}")
        val length = stream.readInt()
        log("        length: $length")
        val type = stream.read()
        log("        primitive type: $type")

        val size = BASIC_TYPES[type]
        if (size == null) {
            throw Exception("readPrimitiveArrayDump() not supported type: $type")
        } else {
            seek((size * length))
        }
    }

    private fun readRootInternedString() {
        counterHeap("ROOT_INTERNED_STRING")
        log("    ROOT_INTERNED_STRING(0x89)")
        log("        id: ${readId()}")
    }

    private fun readRootFinalizing() {
        counterHeap("ROOT_FINALIZING")
        log("    ROOT_FINALIZING(0x8A)")
        log("        id: ${readId()}")
    }

    private fun readRootDebugger() {
        counterHeap("ROOT_DEBUGGER")
        log("    ROOT_FINALIZING(0x8B)")
        log("        id: ${readId()}")
    }

    private fun readRootReferenceCleanup() {
        counterHeap("ROOT_REFERENCE_CLEANUP")
        log("    ROOT_REFERENCE_CLEANUP(0x8C)")
        log("        id: ${readId()}")
    }

    private fun readRootVmInternal() {
        counterHeap("ROOT_VM_INTERNAL")
        log("    ROOT_VM_INTERNAL(0x8D)")
        log("        id: ${readId()}")
    }

    private fun readRootJniMonitor() {
        counterHeap("ROOT_JNI_MONITOR")
        log("    ROOT_JNI_MONITOR(0x8E)")
        log("        string id: ${readId()}")
        log("        thread serial number: ${stream.readInt()}")
        log("        stack trace depth: ${stream.readInt()}")
    }

    private fun readHeapDumpInfo() {
        counterHeap("HEAP_DUMP_INFO")
        log("    HEAP_DUMP_INFO(0xFE)")
        log("        heap id: ${stream.readInt()}")
        log("        heap name id: ${readId()}")
    }

    private fun readId(): Long {
        return if (identifierSize == 4) stream.readInt().toLong() else stream.readLong()
    }

    private fun seek(length: Int) {
        stream.seek(stream.filePointer + length)
    }

    private fun counter(tag: String) {
        tagCounter[tag] = tagCounter.getOrDefault(tag, 0) + 1
    }

    private fun counterHeap(tag: String) {
        heapTagCounter[tag] = heapTagCounter.getOrDefault(tag, 0) + 1
    }

    private fun log(msg: String) {
        logger.log("Analyzer", msg)
    }

    private fun printTagCounts() {
        log("\nTag Counts")
        for ((k, v) in tagCounter.entries) {
            log("    $k : $v")
        }
    }

    private fun printHeapTagCounts() {
        log("\nHeap Tag Counts")
        for ((k, v) in heapTagCounter.entries) {
            log("    $k : $v")
        }
    }

    fun analysis() {
        readHead()
        readRecords()
        printTagCounts()
        printHeapTagCounts()
    }

    companion object {
        // Record TAG
        private const val TAG_STRING = 0x01
        private const val TAG_LOAD_CLASS = 0x02
        private const val TAG_UNLOAD_CLASS = 0x03
        private const val TAG_STACK_FRAME = 0x04
        private const val TAG_STACK_TRACE = 0x05
        private const val TAG_ALLOC_SITES = 0x06
        private const val TAG_HEAP_SUMMARY = 0x07
        private const val TAG_START_THREAD = 0x0A
        private const val TAG_END_THREAD = 0x0B
        private const val TAG_HEAP_DUMP = 0x0C
        private const val TAG_HEAP_SEGMENT = 0x1C
        private const val TAG_HEAP_DUMP_END = 0x2C
        private const val TAG_CPU_SAMPLES = 0x0D
        private const val TAG_CONTROL_SETTINGS = 0x0E

        // HEAP DUMP TAGS
        private const val HEAP_TAG_ROOT_UNKNOWN = 0xFF
        private const val HEAP_TAG_ROOT_JNI_GLOBAL = 0x01
        private const val HEAP_TAG_ROOT_JNI_LOCAL = 0x02
        private const val HEAP_TAG_ROOT_JAVA_FRAME = 0x03
        private const val HEAP_TAG_ROOT_NATIVE_STACK = 0x04
        private const val HEAP_TAG_ROOT_STICKY_CLASS = 0x05
        private const val HEAP_TAG_ROOT_THREAD_BLOCK = 0x06
        private const val HEAP_TAG_ROOT_MONITOR_USED = 0x07
        private const val HEAP_TAG_ROOT_THREAD_OBJECT = 0x08
        private const val HEAP_TAG_CLASS_DUMP = 0x20
        private const val HEAP_TAG_INSTANCE_DUMP = 0x21
        private const val HEAP_TAG_OBJECT_ARRAY_DUMP = 0x22
        private const val HEAP_TAG_PRIMITIVE_ARRAY_DUMP = 0x23

        // Android Special
        private const val HEAP_TAG_ROOT_INTERNED_STRING = 0x89
        private const val HEAP_TAG_ROOT_FINALIZING = 0x8A
        private const val HEAP_TAG_ROOT_DEBUGGER = 0x8B
        private const val HEAP_TAG_ROOT_REFERENCE_CLEANUP = 0x8C
        private const val HEAP_TAG_ROOT_VM_INTERNAL = 0x8D
        private const val HEAP_TAG_ROOT_JNI_MONITOR = 0x8E
        private const val HEAP_TAG_ROOT_UNREACHABLE = 0x90 // unused
        private const val HEAP_TAG_ROOT_PRIMITIVE_ARRAY_NODATA = 0xC3 // unused
        private const val HEAP_TAG_HEAP_DUMP_INFO = 0xFE

        private const val TYPE_OBJECT = 2
        private const val TYPE_BOOLEAN = 4
        private const val TYPE_CHAR = 5
        private const val TYPE_FLOAT = 6
        private const val TYPE_DOUBLE = 7
        private const val TYPE_BYTE = 8
        private const val TYPE_SHORT = 9
        private const val TYPE_INT = 10
        private const val TYPE_LONG = 11

        private val BASIC_TYPES = mutableMapOf(
            TYPE_BOOLEAN to 1,
            TYPE_CHAR to 2,
            TYPE_FLOAT to 4,
            TYPE_DOUBLE to 8,
            TYPE_BYTE to 1,
            TYPE_SHORT to 2,
            TYPE_INT to 4,
            TYPE_LONG to 8,
        )
    }
}

fun main(args: Array<String>) {
    try {
        AndroidHeapAnalyzer(File("/Users/chaos/Workspace/Code/private/python/HprofAnalyzer/large-dump.hprof")).analysis()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}