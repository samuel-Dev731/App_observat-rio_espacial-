#include <math.h>
#include <stddef.h>

/* CPU-side orbital demonstration. Values are illustrative, not ephemerides. */
void observatorio_compute_orbits(float seconds, float *output, size_t capacity) {
    static const float radii[5] = {2.2f, 3.3f, 4.6f, 6.2f, 8.2f};
    static const float speeds[5] = {1.20f, 0.82f, 0.62f, 0.38f, 0.21f};
    if (output == NULL || capacity < 15) return;

    for (int i = 0; i < 5; ++i) {
        const float angle = seconds * speeds[i] + (float)i * 1.13f;
        output[i * 3] = cosf(angle) * radii[i];
        output[i * 3 + 1] = (i % 2 == 0) ? 0.12f : -0.16f;
        output[i * 3 + 2] = sinf(angle) * radii[i];
    }
}
