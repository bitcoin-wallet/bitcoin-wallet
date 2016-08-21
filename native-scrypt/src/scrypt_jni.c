// Copyright (C) 2011 - Will Glozer.  All rights reserved.

#include <errno.h>
#include <stdlib.h>
#include <inttypes.h>

#include <jni.h>
#include "crypto_scrypt.h"

jbyteArray JNICALL scryptN(JNIEnv *env, jclass cls, jbyteArray passwd, jbyteArray salt,
    jint N, jint r, jint p, jint dkLen)
{
    jint Plen = (*env)->GetArrayLength(env, passwd);
    jint Slen = (*env)->GetArrayLength(env, salt);
    jbyte *P = (*env)->GetByteArrayElements(env, passwd, NULL);
    jbyte *S = (*env)->GetByteArrayElements(env, salt,   NULL);
    uint8_t *buf = malloc(sizeof(uint8_t) * dkLen);
    jbyteArray DK = NULL;

    if (P == NULL || S == NULL || buf == NULL) goto cleanup;

    if (crypto_scrypt((uint8_t *) P, Plen, (uint8_t *) S, Slen, N, r, p, buf, dkLen)) {
        jclass e = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        char *msg;
        switch (errno) {
            case EINVAL:
                msg = "N must be a power of 2 greater than 1";
                break;
            case EFBIG:
            case ENOMEM:
                msg = "Insufficient memory available";
                break;
            default:
                msg = "Memory allocation failed";
        }
        (*env)->ThrowNew(env, e, msg);
        goto cleanup;
    }

    DK = (*env)->NewByteArray(env, dkLen);
    if (DK == NULL) goto cleanup;

    (*env)->SetByteArrayRegion(env, DK, 0, dkLen, (jbyte *) buf);

  cleanup:

    if (P) (*env)->ReleaseByteArrayElements(env, passwd, P, JNI_ABORT);
    if (S) (*env)->ReleaseByteArrayElements(env, salt,   S, JNI_ABORT);
    if (buf) free(buf);

    return DK;
}

static const JNINativeMethod methods[] = {
    { "scryptN", "([B[BIIII)[B", (void *) scryptN }
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    jclass cls = (*env)->FindClass(env, "com/lambdaworks/crypto/SCrypt");
    int r = (*env)->RegisterNatives(env, cls, methods, 1);

    return (r == JNI_OK) ? JNI_VERSION_1_6 : -1;
}
