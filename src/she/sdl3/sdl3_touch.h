// SHE library
// Copyright (C) 2026 Veritaware
//
// This file is released under the terms of the MIT license.
// Read LICENSE.txt for more information.

#pragma once

// Turns raw touchscreen fingers into the mouse-style events the UI expects,
// plus two-finger gestures. Used on Android, where SDL's own touch-to-mouse
// emulation is switched off because it can't tell a one-finger stroke from
// the start of a pinch.
//
//  - One finger: left button press/drag/release. The press is held back
//    for a moment (or until the finger moves) so that a second finger
//    arriving right after doesn't leave a stray dot on the canvas.
//  - Two fingers: pan (sent as precise wheel events, i.e. scrolling) and
//    pinch-to-zoom (sent as TouchMagnify events).
//  - Two-finger tap: Undo (Ctrl+Z). Three-finger tap: Redo (Ctrl+Y).

#include "she/event.h"

#include <SDL3/SDL.h>

#include <chrono>
#include <deque>
#include <vector>

namespace she
{

class SDL3Display;

class SDL3TouchGestures
{
public:
  // Handles SDL_EVENT_FINGER_DOWN/MOTION/UP/CANCELED. Generated events are
  // appended to "out". Returns false for any other kind of event.
  bool processEvent(const SDL_Event& sdlEvent, SDL3Display* display, std::deque<Event>& out);

  // Must be called regularly (on every event poll): turns a finger that was
  // held still for a moment into a button press.
  void update(SDL3Display* display, std::deque<Event>& out);

private:
  using Clock = std::chrono::steady_clock;

  enum class State
  {
    Idle,    // No fingers down
    Pending, // One finger down, waiting to know if it's a stroke or a gesture
    Pressed, // One finger down, left button press sent
    Gesture, // Two or more fingers down
  };

  struct Finger
  {
    SDL_FingerID id;
    float x, y; // window coordinates
  };

  Finger* findFinger(SDL_FingerID id);
  void removeFinger(SDL_FingerID id);
  void press(SDL3Display* display, std::deque<Event>& out);
  void startGesture(SDL3Display* display, std::deque<Event>& out);
  void gestureMotion(SDL3Display* display, std::deque<Event>& out);
  void endGesture(std::deque<Event>& out);
  float slop(SDL3Display* display) const;

  Event mouseEvent(Event::Type type, SDL3Display* display, float x, float y) const;

  State m_state = State::Idle;
  std::vector<Finger> m_fingers;

  // One-finger press
  float m_downX = 0, m_downY = 0;
  Clock::time_point m_downTime;
  float m_lastTapX = 0, m_lastTapY = 0;
  Clock::time_point m_lastTapTime;

  // Gesture
  Clock::time_point m_gestureStart;
  size_t m_maxFingers = 0;
  size_t m_gestureFingerCount = 0;
  bool m_gestureMoved = false;
  float m_lastCenterX = 0, m_lastCenterY = 0;
  float m_startCenterX = 0, m_startCenterY = 0;
  float m_lastDistance = 0, m_startDistance = 0;
  float m_scrollRemX = 0, m_scrollRemY = 0;
  double m_pendingMagnify = 0;
};

} // namespace she
