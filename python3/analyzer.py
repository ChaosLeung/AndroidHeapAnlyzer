#!/usr/local/bin/python3
import sys
import os
from datetime import datetime

# Record TAG
TAG_STRING = 0x01
TAG_LOAD_CLASS = 0x02
TAG_UNLOAD_CLASS = 0x03
TAG_STACK_FRAME = 0x04
TAG_STACK_TRACE = 0x05
TAG_ALLOC_SITES = 0x06
TAG_HEAP_SUMMARY = 0x07
TAG_START_THREAD = 0x0A
TAG_END_THREAD = 0x0B
TAG_HEAP_DUMP = 0x0C
TAG_HEAP_SEGMENT = 0x1C
TAG_HEAP_DUMP_END = 0x2C
TAG_CPU_SAMPLES = 0x0D
TAG_CONTROL_SETTINGS = 0x0E

# HEAP DUMP TAGS
HEAP_TAG_ROOT_UNKNOWN = 0xFF
HEAP_TAG_ROOT_JNI_GLOBAL = 0x01
HEAP_TAG_ROOT_JNI_LOCAL = 0x02
HEAP_TAG_ROOT_JAVA_FRAME = 0x03
HEAP_TAG_ROOT_NATIVE_STACK = 0x04
HEAP_TAG_ROOT_STICKY_CLASS = 0x05
HEAP_TAG_ROOT_THREAD_BLOCK = 0x06
HEAP_TAG_ROOT_MONITOR_USED = 0x07
HEAP_TAG_ROOT_THREAD_OBJECT = 0x08
HEAP_TAG_CLASS_DUMP = 0x20
HEAP_TAG_INSTANCE_DUMP = 0x21
HEAP_TAG_OBJECT_ARRAY_DUMP = 0x22
HEAP_TAG_PRIMITIVE_ARRAY_DUMP = 0x23
# Android Special
HEAP_TAG_ROOT_INTERNED_STRING = 0x89
HEAP_TAG_ROOT_FINALIZING = 0x8A
HEAP_TAG_ROOT_DEBUGGER = 0x8B
HEAP_TAG_ROOT_REFERENCE_CLEANUP = 0x8C
HEAP_TAG_ROOT_VM_INTERNAL = 0x8D
HEAP_TAG_ROOT_JNI_MONITOR = 0x8E
HEAP_TAG_ROOT_UNREACHABLE = 0x90  # unused
HEAP_TAG_ROOT_PRIMITIVE_ARRAY_NODATA = 0xC3  # unused
HEAP_TAG_HEAP_DUMP_INFO = 0xFE

TYPE_OBJECT = 2
TYPE_BOOLEAN = 4
TYPE_CHAR = 5
TYPE_FLOAT = 6
TYPE_DOUBLE = 7
TYPE_BYTE = 8
TYPE_SHORT = 9
TYPE_INT = 10
TYPE_LONG = 11

# type: size
BASIC_TYPES = {
    TYPE_BOOLEAN: 1,
    TYPE_CHAR: 2,
    TYPE_FLOAT: 4,
    TYPE_DOUBLE: 8,
    TYPE_BYTE: 1,
    TYPE_SHORT: 2,
    TYPE_INT: 4,
    TYPE_LONG: 8,
}

# 记录每个 TAG 的出现次数
counter = {}
heap_counter = {}

hprof = None
size_of_identifier = 4


def readRecords():
    file_length = os.path.getsize(hprof.name)
    while hprof.tell() < file_length:
        tag = readInt(1)
        time = readInt(4)
        length = readInt(4)

        if tag == TAG_STRING:
            readString(length)
            continue
        elif tag == TAG_LOAD_CLASS:
            COUNTER('TAG_LOAD_CLASS')
        elif tag == TAG_UNLOAD_CLASS:
            COUNTER('TAG_UNLOAD_CLASS')
        elif tag == TAG_STACK_FRAME:
            COUNTER('TAG_STACK_FRAME')
        elif tag == TAG_STACK_TRACE:
            readStackTrace()
            continue
        elif tag == TAG_ALLOC_SITES:
            COUNTER('TAG_ALLOC_SITES')
        elif tag == TAG_HEAP_SUMMARY:
            COUNTER('TAG_HEAP_SUMMARY')
        elif tag == TAG_START_THREAD:
            COUNTER('TAG_START_THREAD')
        elif tag == TAG_HEAP_DUMP:
            readHeapDump(length)
            continue
        elif tag == TAG_HEAP_SEGMENT:
            readHeapSegmentDump(length)
            continue
        elif tag == TAG_HEAP_DUMP_END:
            COUNTER('HEAP_DUMP_END')
        elif tag == TAG_CPU_SAMPLES:
            COUNTER('TAG_CPU_SAMPLES')
        elif tag == TAG_CONTROL_SETTINGS:
            COUNTER('TAG_CONTROL_SETTINGS')
        else:
            raise Exception('Not supported tag: %d, position: %d' %
                            (tag, hprof.tell()))
        seek(length)


def readHead():
    version_bytes = []
    while True:
        b = read(1)
        if not b or b == b'\x00':
            break
        version_bytes.append(b)
    version = b''.join(version_bytes).decode('utf-8')
    global size_of_identifier
    size_of_identifier = readInt(4)
    timestamp = readInt(8) / 1000

    LOGGER("Version: %s" % (version))
    LOGGER("Identifier Size: %d" % (size_of_identifier))
    LOGGER("Created Time: %s" % (datetime.fromtimestamp(timestamp)))

    BASIC_TYPES[TYPE_OBJECT] = size_of_identifier


def readString(length):
    COUNTER('STRING')
    LOGGER("STRING(0x01)")
    length = length - size_of_identifier
    LOGGER("    id: %s" % (hex(readId())))
    LOGGER("    content: %s" % (read(length).decode('utf-8')))


def readStackTrace():
    COUNTER('STACK_TRACE')
    LOGGER("STACK_TRACE(0x05)")
    LOGGER("    serial number: %s" % (hex(readInt(4))))
    LOGGER("    thread serial number: %s" % (hex(readInt(4))))
    frame_count = readInt(4)
    LOGGER("    number of frames: %d" % (frame_count))
    ids = []
    for i in range(frame_count):
        frame_id = read(size_of_identifier)
        if not frame_id:
            break
        ids.append("%s" % hex(frame_id))
    LOGGER("    series of stack frame ID's: %s" % (ids))


def readHeapDump(length):
    COUNTER('HEAP_DUMP')
    LOGGER("HEAP_DUMP(0x0C)")
    readHeapDumpInternal(length)


def readHeapDumpSegment(length):
    COUNTER('HEAP_DUMP_SEGMENT')
    LOGGER("HEAP_DUMP_SEGMENT(0x1C)")
    readHeapDumpInternal(length)


def readHeapDumpInternal(length):
    available = length
    while available > 0:
        start = hprof.tell()

        tag = readInt(1)

        if tag == HEAP_TAG_ROOT_UNKNOWN:
            readRootUnknown()
        elif tag == HEAP_TAG_ROOT_JNI_GLOBAL:
            readRootJniGlobal()
        elif tag == HEAP_TAG_ROOT_JNI_LOCAL:
            readRootJniLocal()
        elif tag == HEAP_TAG_ROOT_JAVA_FRAME:
            readRootJavaFrame()
        elif tag == HEAP_TAG_ROOT_NATIVE_STACK:
            readRootNativeStack()
        elif tag == HEAP_TAG_ROOT_STICKY_CLASS:
            readRootStickyClass()
        elif tag == HEAP_TAG_ROOT_THREAD_BLOCK:
            readRootThreadBlock()
        elif tag == HEAP_TAG_ROOT_MONITOR_USED:
            readRootMonitorUsed()
        elif tag == HEAP_TAG_ROOT_THREAD_OBJECT:
            readRootThreadObject()
        elif tag == HEAP_TAG_CLASS_DUMP:
            readClassDump()
        elif tag == HEAP_TAG_INSTANCE_DUMP:
            readInstanceDump()
        elif tag == HEAP_TAG_OBJECT_ARRAY_DUMP:
            readObjectArrayDump()
        elif tag == HEAP_TAG_PRIMITIVE_ARRAY_DUMP:
            readPrimitiveArrayDump()
        # Android Special
        elif tag == HEAP_TAG_ROOT_INTERNED_STRING:
            readRootInternedString()
        elif tag == HEAP_TAG_ROOT_FINALIZING:
            readRootFinalizing()
        elif tag == HEAP_TAG_ROOT_DEBUGGER:
            readRootDebugger()
        elif tag == HEAP_TAG_ROOT_REFERENCE_CLEANUP:
            readRootReferenceCleanup()
        elif tag == HEAP_TAG_ROOT_VM_INTERNAL:
            readRootVmInternal()
        elif tag == HEAP_TAG_ROOT_JNI_MONITOR:
            readRootJniMonitor()
        elif tag == HEAP_TAG_HEAP_DUMP_INFO:
            readHeapDumpInfo()
        else:
            raise Exception(
                'Not supported heap dump tag: %d, position: %d' % (tag, hprof.tell()))

        end = hprof.tell()
        available -= end - start


def readRootUnknown():
    COUNTER_HEAP('ROOT_UNKNOWN')
    LOGGER("    ROOT_UNKNOWN(0xFF)")
    LOGGER("        id: %s" % (hex(readId())))


def readRootJniGlobal():
    COUNTER_HEAP('ROOT_JNI_GLOBAL')
    LOGGER("    ROOT_JNI_GLOBAL(0x01)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        JNI global ref id: %s" %
           (hex(readId())))


def readRootJniLocal():
    COUNTER_HEAP('ROOT_JNI_LOCAL')
    LOGGER("    ROOT_JNI_LOCAL(0x02)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        thread serial number: %s" % (hex(readInt(4))))
    LOGGER("        frame number: %d" % (readInt(4)))


def readRootJavaFrame():
    COUNTER_HEAP('ROOT_JAVA_FRAME')
    LOGGER("    ROOT_JNI_FRAME(0x03)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        thread serial number: %s" % (hex(readInt(4))))
    LOGGER("        frame number: %d" % (readInt(4)))


def readRootNativeStack():
    COUNTER_HEAP('ROOT_NATIVE_STACK')
    LOGGER("    ROOT_NATIVE_STACK(0x04)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        thread serial number: %s" % (hex(readInt(4))))


def readRootStickyClass():
    COUNTER_HEAP('ROOT_STICKY_CLASS')
    LOGGER("    ROOT_STICKY_CLASS(0x05)")
    LOGGER("        id: %s" % (hex(readId())))


def readRootThreadBlock():
    COUNTER_HEAP('ROOT_THREAD_BLOCK')
    LOGGER("    ROOT_THREAD_BLOCK(0x06)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        thread serial number: %s" % (hex(readInt(4))))


def readRootMonitorUsed():
    COUNTER_HEAP('ROOT_MONITOR_USED')
    LOGGER("    ROOT_MONITOR_USED(0x07)")
    LOGGER("        id: %s" % (hex(readId())))


def readRootThreadObject():
    COUNTER_HEAP('ROOT_THREAD_OBJECT')
    LOGGER("    ROOT_THREAD_OBJECT(0x08)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        thread serial number: %s" % (hex(readInt(4))))
    LOGGER("        stack trace serial number: %s" % (hex(readInt(4))))


def readClassDump():
    COUNTER_HEAP('CLASS_DUMP')
    LOGGER("    CLASS_DUMP(0x20)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        stack trace serial number: %s" % (hex(readInt(4))))
    LOGGER("        super class id: %s" %
           (hex(readId())))
    LOGGER("        class loader id: %s" %
           (hex(readId())))
    LOGGER("        signers id: %s" % (hex(readId())))
    LOGGER("        protection domain id: %s" %
           (hex(readId())))
    seek(size_of_identifier * 2)
    LOGGER("        instance size: %d" % (readInt(4)))
    readClassConstantFields()
    readClassStaticFields()
    readInstanceFields()


def readClassConstantFields():
    count = readInt(2)
    LOGGER("        number of constant fields: %d" % (count))
    while count > 0:
        count -= 1

        seek(2)

        type = readInt(1)

        size = BASIC_TYPES[type]
        if not size:
            raise Exception(
                'readClassConstantFields() not supported type ' % type)
        else:
            seek(size)


def readClassStaticFields():
    count = readInt(2)
    LOGGER("        number of static fields: %d" % (count))
    while count > 0:
        count -= 1

        seek(size_of_identifier)

        type = readInt(1)

        size = BASIC_TYPES[type]
        if not size:
            raise Exception(
                'readClassStaticFields() not supported type ' % type)
        else:
            seek(size)


def readInstanceFields():
    count = readInt(2)
    LOGGER("        number of instance fields: %d" % (count))
    seek(count * (size_of_identifier + 1))


def readInstanceDump():
    COUNTER_HEAP('INSTANCE_DUMP')
    LOGGER("    INSTANCE_DUMP(0x21)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        stack trace serial number: %s" % (hex(readInt(4))))
    LOGGER("        class id: %s" % (hex(readId())))
    count = readInt(4)
    LOGGER("        fields byte size: %d" % (count))
    seek(count)


def readObjectArrayDump():
    COUNTER_HEAP('OBJECT_ARRAY_DUMP')
    LOGGER("    OBJECT_ARRAY_DUMP(0x22)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        stack trace serial number: %s" % (hex(readInt(4))))
    length = readInt(4)
    LOGGER("        length: %d" % (length))
    LOGGER("        array class id: %s" %
           (hex(readId())))
    seek(size_of_identifier * length)


def readPrimitiveArrayDump():
    COUNTER_HEAP('PRIMITIVE_ARRAY_DUMP')
    LOGGER("    PRIMITIVE_ARRAY_DUMP(0x23)")
    LOGGER("        id: %s" % (hex(readId())))
    LOGGER("        stack trace serial number: %s" % (hex(readInt(4))))
    length = readInt(4)
    LOGGER("        length: %d" % (length))
    type = readInt(1)
    LOGGER("        primitive type: %d" % (type))

    size = BASIC_TYPES[type]
    if not size:
        raise Exception('readPrimitiveArrayDump() not supported type ' % type)
    else:
        seek(size * length)


def readRootInternedString():
    COUNTER_HEAP('ROOT_INTERNED_STRING')
    LOGGER("    ROOT_INTERNED_STRING(0x89)")
    LOGGER("        id: %s" % (hex(readId())))


def readRootFinalizing():
    COUNTER_HEAP('ROOT_FINALIZING')
    LOGGER("    ROOT_FINALIZING(0x8A)")
    LOGGER("        id: %s" % (hex(readId())))


def readRootDebugger():
    COUNTER_HEAP('ROOT_DEBUGGER')
    LOGGER("    ROOT_FINALIZING(0x8B)")
    LOGGER("        id: %s" % (hex(readId())))


def readRootReferenceCleanup():
    COUNTER_HEAP('ROOT_REFERENCE_CLEANUP')
    LOGGER("    ROOT_REFERENCE_CLEANUP(0x8C)")
    LOGGER("        id: %s" % (hex(readId())))


def readRootVmInternal():
    COUNTER_HEAP('ROOT_VM_INTERNAL')
    LOGGER("    ROOT_VM_INTERNAL(0x8D)")
    LOGGER("        id: %s" % (hex(readId())))


def readRootJniMonitor():
    COUNTER_HEAP('ROOT_JNI_MONITOR')
    LOGGER("    ROOT_JNI_MONITOR(0x8E)")
    LOGGER("        string id: %s" % (hex(readId())))
    LOGGER("        thread serial number: %s" % (hex(readInt(4))))
    LOGGER("        stack trace depth: %d" % (readInt(4)))


def readHeapDumpInfo():
    COUNTER_HEAP('HEAP_DUMP_INFO')
    LOGGER("    HEAP_DUMP_INFO(0xFE)")
    LOGGER("        heap id: %s" % (hex(readInt(4))))
    LOGGER("        heap name id: %s" % (hex(readId())))


def openHprof(file):
    global hprof
    LOGGER("Hprof: %s\n" % (file))
    hprof = open(file, 'rb')


def readInt(length):
    return int.from_bytes(read(length), byteorder='big', signed=False)


def readId():
    return readInt(size_of_identifier)


def read(length):
    return hprof.read(length)


def seek(length):
    hprof.seek(length, 1)


def COUNTER(key):
    global counter
    counter.update({key: 1 + counter.get(key, 0)})


def COUNTER_HEAP(key):
    global heap_counter
    heap_counter.update({key: 1 + heap_counter.get(key, 0)})


def LOGGER(msg):
    print(msg)


def printTagCounts():
    LOGGER("\nTag Counts")
    for k, v in counter.items():
        LOGGER('    {key}: {value}'.format(key=k, value=v))


def printHeapTagCounts():
    LOGGER("\nHeap Tag Counts")
    for k, v in heap_counter.items():
        LOGGER('    {key}: {value}'.format(key=k, value=v))


if __name__ == '__main__':
    openHprof(sys.argv[1])
    readHead()
    readRecords()
    printTagCounts()
    printHeapTagCounts()
