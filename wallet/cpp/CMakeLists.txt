cmake_minimum_required(VERSION 3.6.0)
add_library(scrypt SHARED scrypt_jni.c crypto_scrypt-nosse.c sha256.c)
set_target_properties(scrypt PROPERTIES LINK_FLAGS -Wl,--build-id=none)
include_directories(.)
