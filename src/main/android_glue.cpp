// Besprited | Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

// Glue between the Android app (android/app, BespritedActivity.java) and
// Besprited. Only built for Android.

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <cstdlib>
#include <string>

#ifdef __ANDROID__
#include <SDL3/SDL.h>
#include <jni.h>
#endif

// Used by base/fs_unix.h:
//  - _AndroidDataDir: folder holding the "data" folder (the app's private
//    files), i.e. what get_app_path() reports.
//  - _AndroidStorageDir: default folder for the user's documents.
std::string _AndroidDataDir = "./";
std::string _AndroidStorageDir = "./";

// Called first thing in app_main(). BespritedActivity sets these variables
// before the native code starts (it also sets HOME, XDG_CONFIG_HOME and
// TMPDIR so preferences and temporary files go to the app's storage).
void android_init()
{
  if (const char* dir = std::getenv("BESPRITED_DATA_DIR"))
    _AndroidDataDir = dir;
  if (const char* dir = std::getenv("BESPRITED_STORAGE_DIR"))
    _AndroidStorageDir = dir;
}

// File > Share: hands the file to Android's share sheet.
void _AndroidShareFile(const std::string& path)
{
#ifdef __ANDROID__
  auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
  auto activity = static_cast<jobject>(SDL_GetAndroidActivity());
  if (!env || !activity)
    return;

  jclass cls = env->GetObjectClass(activity);
  jmethodID shareFile = env->GetMethodID(cls, "shareFile", "(Ljava/lang/String;)V");
  if (shareFile)
  {
    jstring jpath = env->NewStringUTF(path.c_str());
    env->CallVoidMethod(activity, shareFile, jpath);
    env->DeleteLocalRef(jpath);
  }
  if (env->ExceptionCheck())
  {
    env->ExceptionDescribe();
    env->ExceptionClear();
  }
  env->DeleteLocalRef(cls);
  env->DeleteLocalRef(activity);
#else
  (void)path;
#endif
}
