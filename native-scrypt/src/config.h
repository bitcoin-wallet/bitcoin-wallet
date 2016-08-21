#define HAVE_DECL_BE64ENC 0
#define HAVE_MMAP 1

#ifndef __ANDROID__
#define HAVE_POSIX_MEMALIGN 1
#endif

#ifdef __ANDROID__
#include <sys/limits.h>
#endif
