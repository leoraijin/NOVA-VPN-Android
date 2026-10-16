/* SPDX-License-Identifier: GPL-3.0-or-later
 * NOVA Android bridge. The service serializes start/stop. rc_stop joins callbacks
 * before the Java global reference is freed. Never print config or native errors.
 */
#include <jni.h>
#include <stdint.h>
#include <unistd.h>
#include <pthread.h>
#include "reality.h"

extern int rc_validate(const char *, const char *);
extern int rc_is_alive(RcCore *);
static JavaVM *vm;
static jobject callback;
static jmethodID protect_method, warning_method;
static pthread_mutex_t callback_lock = PTHREAD_MUTEX_INITIALIZER;
static uintptr_t generation;

/* A callback may arrive after an old runtime's shutdown timeout. Its generation
 * cannot access the replacement service. A local reference survives teardown. */
static jobject acquire_owner(JNIEnv *env, void *user) {
    pthread_mutex_lock(&callback_lock);
    jobject owner = callback && (uintptr_t)user == generation
        ? (*env)->NewLocalRef(env, callback) : NULL;
    pthread_mutex_unlock(&callback_lock);
    return owner;
}

static void release_owner(JNIEnv *env) {
    pthread_mutex_lock(&callback_lock);
    if (callback) (*env)->DeleteGlobalRef(env, callback);
    callback = NULL;
    pthread_mutex_unlock(&callback_lock);
}

static JNIEnv *environment(int *attached) {
    JNIEnv *env = NULL;
    *attached = 0;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) return NULL;
        *attached = 1;
    }
    return env;
}

static int protect_socket(int64_t fd, void *user) {
    int attached;
    JNIEnv *env = environment(&attached);
    if (!env) return 0;
    jobject owner = acquire_owner(env, user);
    jboolean result = owner ? (*env)->CallBooleanMethod(env, owner, protect_method, (jint)fd) : JNI_FALSE;
    if (owner) (*env)->DeleteLocalRef(env, owner);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); result = JNI_FALSE; }
    if (attached) (*vm)->DetachCurrentThread(vm);
    return result == JNI_TRUE;
}

static void warning(const char *json, void *user) {
    int attached;
    JNIEnv *env = environment(&attached);
    if (!env) return;
    /* Native messages are consumed by the bounded classifier, never exported raw. */
    jobject owner = acquire_owner(env, user);
    jstring message = owner ? (*env)->NewStringUTF(env, json) : NULL;
    if (message) {
        (*env)->CallVoidMethod(env, owner, warning_method, message);
        (*env)->DeleteLocalRef(env, message);
    }
    if (owner) (*env)->DeleteLocalRef(env, owner);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (attached) (*vm)->DetachCurrentThread(vm);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *machine, void *reserved) {
    (void)reserved; vm = machine; return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL Java_com_nova_vpn_android_RealityCoreBridge_validate(
    JNIEnv *env, jobject self, jstring config, jstring dir) {
    (void)self;
    const char *c = (*env)->GetStringUTFChars(env, config, NULL);
    if (!c) return JNI_FALSE;
    const char *d = (*env)->GetStringUTFChars(env, dir, NULL);
    if (!d) { (*env)->ReleaseStringUTFChars(env, config, c); return JNI_FALSE; }
    int ok = rc_validate(c, d);
    (*env)->ReleaseStringUTFChars(env, dir, d);
    (*env)->ReleaseStringUTFChars(env, config, c);
    return ok == 1;
}

JNIEXPORT jlong JNICALL Java_com_nova_vpn_android_RealityCoreBridge_start(
    JNIEnv *env, jobject self, jstring config, jstring dir, jint fd, jobject owner) {
    (void)self;
    /* A second start must never overwrite the process-wide protection callback. */
    if (callback) { close(fd); return 0; }
    jclass type = (*env)->GetObjectClass(env, owner);
    protect_method = (*env)->GetMethodID(env, type, "protectSocket", "(I)Z");
    warning_method = (*env)->GetMethodID(env, type, "coreWarning", "(Ljava/lang/String;)V");
    (*env)->DeleteLocalRef(env, type);
    if (!protect_method || !warning_method) { close(fd); return 0; }
    pthread_mutex_lock(&callback_lock);
    callback = (*env)->NewGlobalRef(env, owner);
    generation++;
    if (!generation) generation++;
    uintptr_t current = generation;
    pthread_mutex_unlock(&callback_lock);
    const char *c = (*env)->GetStringUTFChars(env, config, NULL);
    const char *d = c ? (*env)->GetStringUTFChars(env, dir, NULL) : NULL;
    if (!callback || !c || !d) {
        close(fd);
        if (c) (*env)->ReleaseStringUTFChars(env, config, c);
        if (d) (*env)->ReleaseStringUTFChars(env, dir, d);
        release_owner(env); return 0;
    }
    rc_set_protect(protect_socket, (void *)current);
    char *error = NULL;
    /* rc_start takes ownership of fd even on failure. */
    RcCore *core = rc_start(c, d, fd, &error);
    rc_free_string(error);
    (*env)->ReleaseStringUTFChars(env, dir, d);
    (*env)->ReleaseStringUTFChars(env, config, c);
    if (core) rc_set_log_callback(core, "warning", warning, (void *)current);
    else {
        rc_set_protect(NULL, NULL);
        release_owner(env);
    }
    return (jlong)(intptr_t)core;
}

JNIEXPORT void JNICALL Java_com_nova_vpn_android_RealityCoreBridge_stop(
    JNIEnv *env, jobject self, jlong handle) {
    (void)self;
    rc_stop((RcCore *)(intptr_t)handle);
    rc_set_protect(NULL, NULL);
    release_owner(env);
}

JNIEXPORT jboolean JNICALL Java_com_nova_vpn_android_RealityCoreBridge_isAlive(
    JNIEnv *env, jobject self, jlong handle) {
    (void)env; (void)self;
    return rc_is_alive((RcCore *)(intptr_t)handle) == 1;
}

JNIEXPORT jstring JNICALL Java_com_nova_vpn_android_RealityCoreBridge_version(JNIEnv *env, jobject self) {
    (void)self;
    return (*env)->NewStringUTF(env, rc_version());
}
