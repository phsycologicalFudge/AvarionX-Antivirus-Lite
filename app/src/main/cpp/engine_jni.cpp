#include <jni.h>
#include <dlfcn.h>
#include <cstddef>
#include <cstring>
#include <string>

namespace {

struct Api {
    int (*ready)();
    int (*prep)(const char*);
    int (*init)(const char*, const char*);
    int (*reload)(const char*, const char*);
    int (*release)();
    char* (*scan)(const char*);
    void (*setDataDir)(const char*);
    void (*setCloud)(int);
    int (*cloudBegin)();
    char* (*cloudFinish)(int);
    void (*setScanLimits)(size_t, size_t);
    void (*freeStr)(char*);
};

Api api;

template <typename T>
bool bind(void* handle, const char* name, T& slot) {
    slot = reinterpret_cast<T>(dlsym(handle, name));
    return slot != nullptr;
}

bool bindAll() {
    void* handle = dlopen("libcolourswift_av.so", RTLD_NOW);
    if (handle == nullptr) return false;
    return bind(handle, "av_ready", api.ready)
        && bind(handle, "av_prep", api.prep)
        && bind(handle, "av_init", api.init)
        && bind(handle, "av_reload", api.reload)
        && bind(handle, "av_free", api.release)
        && bind(handle, "av_scan", api.scan)
        && bind(handle, "av_set_data_dir", api.setDataDir)
        && bind(handle, "av_set_cloud", api.setCloud)
        && bind(handle, "av_cloud_begin", api.cloudBegin)
        && bind(handle, "av_cloud_finish", api.cloudFinish)
        && bind(handle, "set_scan_limits", api.setScanLimits)
        && bind(handle, "free_str", api.freeStr);
}

std::string toString(JNIEnv* env, jbyteArray bytes) {
    if (bytes == nullptr) return std::string();
    const jsize length = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(length), '\0');
    if (length > 0) env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(&out[0]));
    return out;
}

jbyteArray toBytes(JNIEnv* env, const char* text) {
    const jsize length = static_cast<jsize>(std::strlen(text));
    jbyteArray out = env->NewByteArray(length);
    if (out != nullptr && length > 0) env->SetByteArrayRegion(out, 0, length, reinterpret_cast<const jbyte*>(text));
    return out;
}

jint nativeReady(JNIEnv*, jclass) {
    return api.ready();
}

jint nativePrep(JNIEnv* env, jclass, jbyteArray defs) {
    return api.prep(toString(env, defs).c_str());
}

jint nativeInitialise(JNIEnv* env, jclass, jbyteArray defs) {
    return api.init(toString(env, defs).c_str(), nullptr);
}

jint nativeReload(JNIEnv* env, jclass, jbyteArray defs) {
    return api.reload(toString(env, defs).c_str(), nullptr);
}

void nativeSetDataDir(JNIEnv* env, jclass, jbyteArray path) {
    api.setDataDir(toString(env, path).c_str());
}

void nativeSetCloud(JNIEnv*, jclass, jboolean enabled) {
    api.setCloud(enabled == JNI_TRUE ? 1 : 0);
}

jint nativeCloudBegin(JNIEnv*, jclass) {
    return api.cloudBegin();
}

jbyteArray nativeCloudFinish(JNIEnv* env, jclass, jint timeoutMs) {
    char* raw = api.cloudFinish(timeoutMs);
    if (raw == nullptr) return nullptr;
    jbyteArray out = toBytes(env, raw);
    api.freeStr(raw);
    return out;
}

jbyteArray nativeScan(JNIEnv* env, jclass, jbyteArray path) {
    char* raw = api.scan(toString(env, path).c_str());
    if (raw == nullptr) return nullptr;
    jbyteArray out = toBytes(env, raw);
    api.release();
    return out;
}

void nativeSetScanLimits(JNIEnv*, jclass, jint maxConcurrent, jint maxThreads) {
    api.setScanLimits(static_cast<size_t>(maxConcurrent < 1 ? 1 : maxConcurrent), static_cast<size_t>(maxThreads < 0 ? 0 : maxThreads));
}

const JNINativeMethod kMethods[] = {
    {"nativeReady", "()I", reinterpret_cast<void*>(nativeReady)},
    {"nativePrep", "([B)I", reinterpret_cast<void*>(nativePrep)},
    {"nativeInitialise", "([B)I", reinterpret_cast<void*>(nativeInitialise)},
    {"nativeReload", "([B)I", reinterpret_cast<void*>(nativeReload)},
    {"nativeSetDataDir", "([B)V", reinterpret_cast<void*>(nativeSetDataDir)},
    {"nativeSetCloud", "(Z)V", reinterpret_cast<void*>(nativeSetCloud)},
    {"nativeCloudBegin", "()I", reinterpret_cast<void*>(nativeCloudBegin)},
    {"nativeCloudFinish", "(I)[B", reinterpret_cast<void*>(nativeCloudFinish)},
    {"nativeScan", "([B)[B", reinterpret_cast<void*>(nativeScan)},
    {"nativeSetScanLimits", "(II)V", reinterpret_cast<void*>(nativeSetScanLimits)},
};

}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (!bindAll()) return JNI_ERR;
    jclass engine = env->FindClass("com/colourswift/avarionxlite/engine/Engine");
    if (engine == nullptr) return JNI_ERR;
    if (env->RegisterNatives(engine, kMethods, static_cast<jint>(sizeof(kMethods) / sizeof(kMethods[0]))) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
