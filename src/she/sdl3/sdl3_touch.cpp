// SHE library
// Copyright (C) 2026 Veritaware
//
// This file is released under the terms of the MIT license.
// Read LICENSE.txt for more information.

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "she/sdl3/sdl3_touch.h"

#include "she/keys.h"
#include "she/sdl3/sdl3_display.h"

#include <algorithm>
#include <cmath>

namespace she
{

namespace
{

using namespace std::chrono_literals;

// How long a single finger may rest before it counts as a press even if it
// hasn't moved. A second finger landing within this time starts a gesture
// instead, without drawing anything.
constexpr auto kPressDelay = 100ms;

// A gesture shorter than this that barely moved counts as a tap.
constexpr auto kTapTime = 350ms;

// Two taps closer in time than this make a double click.
constexpr auto kDoubleTapTime = 300ms;

// Distance in density-independent pixels a finger may move and still count
// as resting (or a gesture as a tap).
constexpr float kSlopDp = 10.0f;

// Pinch zoom is sent in steps of at least this relative change.
constexpr double kMagnifyStep = 0.02;

float distance(float x1, float y1, float x2, float y2)
{
  return std::hypot(x2 - x1, y2 - y1);
}

void windowSize(SDL3Display* display, int& w, int& h)
{
  w = h = 1;
  if (display && display->window())
    SDL_GetWindowSize(display->window(), &w, &h);
}

} // namespace

bool SDL3TouchGestures::processEvent(const SDL_Event& sdlEvent, SDL3Display* display, std::deque<Event>& out)
{
  if (sdlEvent.type != SDL_EVENT_FINGER_DOWN && sdlEvent.type != SDL_EVENT_FINGER_MOTION &&
      sdlEvent.type != SDL_EVENT_FINGER_UP && sdlEvent.type != SDL_EVENT_FINGER_CANCELED)
    return false;

  if (!display)
    return true;

  // Finger positions are normalized (0..1) to the window.
  int winW, winH;
  windowSize(display, winW, winH);
  const float x = sdlEvent.tfinger.x * static_cast<float>(winW);
  const float y = sdlEvent.tfinger.y * static_cast<float>(winH);
  const SDL_FingerID id = sdlEvent.tfinger.fingerID;

  switch (sdlEvent.type)
  {
  case SDL_EVENT_FINGER_DOWN:
  {
    if (Finger* finger = findFinger(id))
    {
      finger->x = x;
      finger->y = y;
    }
    else
    {
      m_fingers.push_back({id, x, y});
    }

    switch (m_state)
    {
    case State::Idle:
      m_state = State::Pending;
      m_downX = x;
      m_downY = y;
      m_downTime = Clock::now();
      // Let the UI know where the finger is (hover feedback, brush
      // preview) before anything is pressed.
      out.push_back(mouseEvent(Event::MouseMove, display, x, y));
      break;

    case State::Pending:
      startGesture(display, out);
      break;

    case State::Pressed:
    {
      // The second finger came late: finish what the first one was doing
      // and switch to the gesture.
      const Finger& first = m_fingers.front();
      out.push_back(mouseEvent(Event::MouseUp, display, first.x, first.y));
      startGesture(display, out);
      // Too late for this to be a tap; don't undo the stroke.
      m_gestureMoved = true;
      break;
    }

    case State::Gesture:
      m_maxFingers = std::max(m_maxFingers, m_fingers.size());
      gestureMotion(display, out);
      break;
    }
    return true;
  }

  case SDL_EVENT_FINGER_MOTION:
  {
    Finger* finger = findFinger(id);
    if (!finger)
      return true;
    finger->x = x;
    finger->y = y;

    switch (m_state)
    {
    case State::Pending:
      if (distance(m_downX, m_downY, x, y) > slop(display))
      {
        press(display, out);
        out.push_back(mouseEvent(Event::MouseMove, display, x, y));
      }
      break;

    case State::Pressed:
      out.push_back(mouseEvent(Event::MouseMove, display, x, y));
      break;

    case State::Gesture:
      gestureMotion(display, out);
      break;

    case State::Idle:
      break;
    }
    return true;
  }

  case SDL_EVENT_FINGER_UP:
  case SDL_EVENT_FINGER_CANCELED:
  {
    const bool canceled = (sdlEvent.type == SDL_EVENT_FINGER_CANCELED);
    const bool known = (findFinger(id) != nullptr);
    removeFinger(id);
    if (!known)
      return true;

    switch (m_state)
    {
    case State::Pending:
      if (!canceled)
      {
        // A quick tap: press and release where the finger landed.
        press(display, out);
        out.push_back(mouseEvent(Event::MouseUp, display, m_downX, m_downY));
        m_lastTapX = m_downX;
        m_lastTapY = m_downY;
        m_lastTapTime = Clock::now();
      }
      m_state = State::Idle;
      break;

    case State::Pressed:
      out.push_back(mouseEvent(Event::MouseUp, display, x, y));
      m_lastTapX = x;
      m_lastTapY = y;
      m_lastTapTime = Clock::now();
      m_state = State::Idle;
      break;

    case State::Gesture:
      if (m_fingers.empty())
      {
        if (canceled)
          m_gestureMoved = true;
        endGesture(out);
        m_state = State::Idle;
      }
      else
      {
        // Keep going with the remaining fingers, measuring from where they
        // are now so nothing jumps.
        gestureMotion(display, out);
      }
      break;

    case State::Idle:
      break;
    }

    if (m_fingers.empty())
      m_state = State::Idle;
    return true;
  }
  }
  return false;
}

void SDL3TouchGestures::update(SDL3Display* display, std::deque<Event>& out)
{
  if (m_state == State::Pending && display && Clock::now() - m_downTime >= kPressDelay)
    press(display, out);
}

SDL3TouchGestures::Finger* SDL3TouchGestures::findFinger(SDL_FingerID id)
{
  for (Finger& finger : m_fingers)
    if (finger.id == id)
      return &finger;
  return nullptr;
}

void SDL3TouchGestures::removeFinger(SDL_FingerID id)
{
  std::erase_if(m_fingers, [id](const Finger& finger) { return finger.id == id; });
}

void SDL3TouchGestures::press(SDL3Display* display, std::deque<Event>& out)
{
  const bool doubleTap = (Clock::now() - m_lastTapTime < kDoubleTapTime) &&
                         distance(m_lastTapX, m_lastTapY, m_downX, m_downY) < 2.0f * slop(display);
  out.push_back(mouseEvent(doubleTap ? Event::MouseDoubleClick : Event::MouseDown, display, m_downX, m_downY));
  m_state = State::Pressed;
}

void SDL3TouchGestures::startGesture(SDL3Display* display, std::deque<Event>& out)
{
  m_state = State::Gesture;
  m_gestureStart = Clock::now();
  m_maxFingers = m_fingers.size();
  m_gestureFingerCount = m_fingers.size();
  m_gestureMoved = false;
  m_scrollRemX = m_scrollRemY = 0;
  m_pendingMagnify = 1.0;

  const Finger& a = m_fingers[0];
  const Finger& b = m_fingers.size() > 1 ? m_fingers[1] : m_fingers[0];
  m_startCenterX = m_lastCenterX = (a.x + b.x) / 2;
  m_startCenterY = m_lastCenterY = (a.y + b.y) / 2;
  m_startDistance = m_lastDistance = distance(a.x, a.y, b.x, b.y);

  // Point the UI at the widget between the fingers: scroll and zoom events
  // go to the widget under the mouse.
  out.push_back(mouseEvent(Event::MouseMove, display, m_lastCenterX, m_lastCenterY));
}

void SDL3TouchGestures::gestureMotion(SDL3Display* display, std::deque<Event>& out)
{
  if (m_fingers.empty())
    return;

  const Finger& a = m_fingers[0];
  const Finger& b = m_fingers.size() > 1 ? m_fingers[1] : m_fingers[0];
  const float cx = (a.x + b.x) / 2;
  const float cy = (a.y + b.y) / 2;
  const float dist = distance(a.x, a.y, b.x, b.y);

  // When a finger is added or lifted, the center and distance jump; just
  // take the new values as the reference. With a single finger left,
  // nothing happens until it's lifted too.
  const bool countChanged = (m_gestureFingerCount != m_fingers.size());
  m_gestureFingerCount = m_fingers.size();
  if (countChanged || m_fingers.size() < 2)
  {
    m_lastCenterX = cx;
    m_lastCenterY = cy;
    m_lastDistance = dist;
    return;
  }

  if (distance(m_startCenterX, m_startCenterY, cx, cy) > slop(display) ||
      std::abs(dist - m_startDistance) > slop(display))
    m_gestureMoved = true;

  const gfx::Point center = display->windowToDisplay(cx, cy);
  const float scale = static_cast<float>(std::max(1, display->scale()));

  // Pinch: zoom around the center of the fingers.
  if (m_lastDistance > 1.0f && dist > 1.0f)
  {
    m_pendingMagnify *= static_cast<double>(dist / m_lastDistance);
    if (std::abs(m_pendingMagnify - 1.0) >= kMagnifyStep)
    {
      Event ev;
      ev.setType(Event::TouchMagnify);
      ev.setPosition(center);
      ev.setModifiers(kKeyNoneModifier);
      ev.setMagnification(m_pendingMagnify - 1.0);
      out.push_back(ev);
      m_pendingMagnify = 1.0;
    }
  }

  // Pan: scroll by however far the center moved (in UI pixels). Scrolling
  // moves the view the opposite way of the content, hence the minus.
  m_scrollRemX -= (cx - m_lastCenterX) / scale;
  m_scrollRemY -= (cy - m_lastCenterY) / scale;
  const int dx = static_cast<int>(m_scrollRemX);
  const int dy = static_cast<int>(m_scrollRemY);
  if (dx != 0 || dy != 0)
  {
    m_scrollRemX -= static_cast<float>(dx);
    m_scrollRemY -= static_cast<float>(dy);
    Event ev;
    ev.setType(Event::MouseWheel);
    ev.setPosition(center);
    ev.setModifiers(kKeyNoneModifier);
    ev.setPointerType(PointerType::Multitouch);
    ev.setWheelDelta({dx, dy});
    ev.setPreciseWheel(true);
    out.push_back(ev);
  }

  m_lastCenterX = cx;
  m_lastCenterY = cy;
  m_lastDistance = dist;
}

void SDL3TouchGestures::endGesture(std::deque<Event>& out)
{
  if (m_gestureMoved || Clock::now() - m_gestureStart > kTapTime)
    return;

  // Two-finger tap: Undo. Three-finger tap: Redo.
  KeyScancode key;
  if (m_maxFingers == 2)
    key = kKeyZ;
  else if (m_maxFingers == 3)
    key = kKeyY;
  else
    return;

  Event ev;
  ev.setModifiers(kKeyCtrlModifier);
  ev.setScancode(key);
  ev.setUnicodeChar(0);
  ev.setType(Event::KeyDown);
  out.push_back(ev);
  ev.setType(Event::KeyUp);
  out.push_back(ev);
}

float SDL3TouchGestures::slop(SDL3Display* display) const
{
  float density = 1.0f;
  if (display && display->window())
  {
    const float s = SDL_GetWindowDisplayScale(display->window());
    if (s > 0.0f)
      density = s;
  }
  return kSlopDp * density;
}

Event SDL3TouchGestures::mouseEvent(Event::Type type, SDL3Display* display, float x, float y) const
{
  Event ev;
  ev.setType(type);
  ev.setPosition(display->windowToDisplay(x, y));
  ev.setModifiers(kKeyNoneModifier);
  ev.setPointerType(PointerType::Multitouch);
  if (type != Event::MouseMove)
    ev.setButton(Event::LeftButton);
  ev.setPressure(type == Event::MouseUp ? 0.0f : 1.0f);
  return ev;
}

} // namespace she
