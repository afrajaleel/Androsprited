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
#include <vector>

#ifdef __ANDROID__
#include <SDL3/SDL.h>
#include <chrono>
#include <condition_variable>
#include <jni.h>
#include <mutex>
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

#ifdef __ANDROID__
// Calls a method of BespritedActivity: void name(), void name(String) or
// void name(boolean).
static void call_activity(const char* name, const std::string* arg, const bool* flag = nullptr)
{
  auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
  auto activity = static_cast<jobject>(SDL_GetAndroidActivity());
  if (!env || !activity)
    return;

  jclass cls = env->GetObjectClass(activity);
  const char* signature = arg ? "(Ljava/lang/String;)V" : flag ? "(Z)V" : "()V";
  jmethodID method = env->GetMethodID(cls, name, signature);
  if (method)
  {
    if (arg)
    {
      jstring jarg = env->NewStringUTF(arg->c_str());
      env->CallVoidMethod(activity, method, jarg);
      env->DeleteLocalRef(jarg);
    }
    else if (flag)
    {
      env->CallVoidMethod(activity, method, static_cast<jboolean>(*flag));
    }
    else
    {
      env->CallVoidMethod(activity, method);
    }
  }
  if (env->ExceptionCheck())
  {
    env->ExceptionDescribe();
    env->ExceptionClear();
  }
  env->DeleteLocalRef(cls);
  env->DeleteLocalRef(activity);
}
#endif

// File > Share: hands the file to Android's share sheet.
void _AndroidShareFile(const std::string& path)
{
#ifdef __ANDROID__
  call_activity("shareFile", &path);
#else
  (void)path;
#endif
}

// ---------------------------------------------------------------------------
// Android's file picker
//
// The native side asks BespritedActivity to show the picker and waits for
// nativeFilesPicked() (called from Java when the user picked something or
// cancelled). The UI doesn't need to keep running meanwhile: the picker
// covers the app.

#ifdef __ANDROID__
static std::mutex g_pickMutex;
static std::condition_variable g_pickDone;
static bool g_picking = false;
static std::vector<std::string> g_picked;

extern "C" JNIEXPORT void JNICALL Java_com_veritaware_besprited_BespritedActivity_nativeFilesPicked(JNIEnv* env, jclass,
                                                                                                    jobjectArray paths)
{
  std::vector<std::string> result;
  const jsize count = paths ? env->GetArrayLength(paths) : 0;
  for (jsize i = 0; i < count; ++i)
  {
    auto jpath = static_cast<jstring>(env->GetObjectArrayElement(paths, i));
    if (!jpath)
      continue;
    if (const char* utf = env->GetStringUTFChars(jpath, nullptr))
    {
      result.emplace_back(utf);
      env->ReleaseStringUTFChars(jpath, utf);
    }
    env->DeleteLocalRef(jpath);
  }
  {
    std::lock_guard<std::mutex> lock(g_pickMutex);
    g_picked = std::move(result);
    g_picking = false;
  }
  g_pickDone.notify_all();
}

static std::vector<std::string> pick_and_wait(const char* method)
{
  {
    std::lock_guard<std::mutex> lock(g_pickMutex);
    g_picked.clear();
    g_picking = true;
  }
  call_activity(method, nullptr);
  std::unique_lock<std::mutex> lock(g_pickMutex);
  while (g_picking)
  {
    // The picker pauses the app. Keep pumping so SDL handles the pause and
    // resume (it moves the GL context to the new surface); otherwise the
    // editor stays blank when the picker closes.
    lock.unlock();
    SDL_PumpEvents();
    lock.lock();
    g_pickDone.wait_for(lock, std::chrono::milliseconds(20), [] { return !g_picking; });
  }
  return g_picked;
}
#endif

bool android_has_file_picker()
{
#ifdef __ANDROID__
  return true;
#else
  return false;
#endif
}

// Any "open a file" dialog: returns the local copy of the picked file
// (in the documents folder), or nothing if cancelled.
std::vector<std::string> android_pick_file()
{
#ifdef __ANDROID__
  return pick_and_wait("pickFile");
#else
  return {};
#endif
}

// Preferences > Theme > Import: returns the folder of the installed theme.
std::vector<std::string> android_import_theme()
{
#ifdef __ANDROID__
  return pick_and_wait("pickTheme");
#else
  return {};
#endif
}

// View > Fullscreen: hides or shows Android's status/navigation bars and
// remembers the choice for the next launch.
void _AndroidSetFullscreen(bool fullscreen)
{
#ifdef __ANDROID__
  call_activity("setFullscreen", nullptr, &fullscreen);
#else
  (void)fullscreen;
#endif
}
