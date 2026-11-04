#ifndef OBSERVATORIO_ORBIT_MATH_H
#define OBSERVATORIO_ORBIT_MATH_H
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
void observatorio_compute_orbits(float seconds, float *output, size_t capacity);
#ifdef __cplusplus
}
#endif
#endif
