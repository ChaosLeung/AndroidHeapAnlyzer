# AndroidHeapAnlyzer

一个分析 Android Hprof 文件的工具

## HRPOF FOMAT

* [JAVA PROFILE 1.0.2](http://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html)
* [Android Studio Hprof Parser](https://android.googlesource.com/platform/tools/base/+/studio-master-dev/perflib/src/main/java/com/android/tools/perflib/heap/HprofParser.java)
* [Art Hprof Generator](https://android.googlesource.com/platform/art/+/master/runtime/hprof/hprof.cc)

没找到 1.0.3 的官方文档，只能直接看 AS 里面解析的源码或生成的代码，相对 1.0.2，新增了如下 TAG：

* HEAP_DUMP_INFO
  * u1: Tag value (0xFE)
  * u4: heap ID
  * ID: heap name string ID
* ROOT_INTERNED_STRING
  * u1: Tag value（0x89）
  * ID: object id
* ROOT_FINALIZING
  * u1: Tag value（0x8a）
  * ID: object id
* ROOT_DEBUGGER
  * u1: Tag value（0x8b）
  * ID: object id
* ROOT_REFERENCE_CLEANUP
  * u1: Tag value（0x8c）
  * ID: object id
* ROOT_VM_INTERNAL
  * u1: Tag value（0x8d）
  * ID: object id
* ROOT_JNI_MONITOR
  * u1: Tag value（0x8e）
  * ID: object id
  * u4: thread serial number
  * u4: stack depth
* ROOT_UNREACHABLE
  * u1: Tag value（0x90）
  * ID: object id
