#include <jni.h>
#include <GLES2/gl2.h>
#include <string>
#include <thread>
#include "orbit_math.h"

extern "C" JNIEXPORT void JNICALL
Java_br_com_observatorioespacial_NativeBridge_computePlanetPositions(
        JNIEnv *env, jobject /* instance */, jfloat seconds, jfloatArray output) {
    if (output == nullptr || env->GetArrayLength(output) < 15) return;
    jfloat positions[15] = {};
    observatorio_compute_orbits(seconds, positions, 15);
    env->SetFloatArrayRegion(output, 0, 15, positions);
}

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_observatorioespacial_NativeBridge_getGpuRenderer(
        JNIEnv *env, jobject /* instance */) {
    const GLubyte *renderer = glGetString(GL_RENDERER);
    const GLubyte *vendor = glGetString(GL_VENDOR);
    std::string description = "GPU: ";
    description += renderer ? reinterpret_cast<const char *>(renderer) : "indisponível";
    if (vendor) {
        description += " (";
        description += reinterpret_cast<const char *>(vendor);
        description += ")";
    }
    return env->NewStringUTF(description.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_br_com_observatorioespacial_NativeBridge_getCpuCoreCount(
        JNIEnv * /* env */, jobject /* instance */) {
    return static_cast<jint>(std::thread::hardware_concurrency());
}
