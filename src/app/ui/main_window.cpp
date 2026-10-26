// Aseprite    | Copyright (C) 2001-2016 David Capello
// LibreSprite | Copyright (C) 2021      LibreSprite contributors
// Besprited   | Copyright (C) 2026      Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "app/ui/main_window.h"
#include "app/ui/panel_dock.h"

#include "app/app.h"
#include "app/app_menus.h"
#include "app/commands/commands.h"
#include "app/ini_file.h"
#include "app/modules/editors.h"
#include "app/pref/preferences.h"
#include "app/ui/color_bar.h"
#include "app/ui/touch_bar.h"
#include "app/ui/context_bar.h"
#include "app/ui/devconsole_view.h"
#include "app/ui/document_view.h"
#include "app/ui/editor/editor.h"
#include "app/ui/editor/editor_view.h"
#include "app/ui/home_view.h"
#include "app/ui/main_menu_bar.h"
#include "app/ui/notifications.h"
#include "app/ui/preview_editor.h"
#include "app/ui/skin/skin_property.h"
#include "app/ui/skin/skin_theme.h"
#include "app/ui/status_bar.h"
#include "app/ui/timeline.h"
#include "app/ui/toolbar.h"
#include "app/ui/workspace.h"
#include "app/ui/workspace_tabs.h"
#include "app/ui_context.h"
#include "base/path.h"
#include "ui/message.h"
#include "ui/resize_event.h"
#include "ui/splitter.h"
#include "ui/system.h"
#include "ui/view.h"

namespace app
{

using namespace ui;

MainWindow::MainWindow()
  : m_mode(NormalMode)
  , m_homeView(nullptr)
  , m_devConsoleView(nullptr)
{
  // Load all menus by first time.
  AppMenus::instance()->reload();

  bool altTouchBar = Preferences::instance().touchBar.alternatePosition();
  auto touchbarParent = altTouchBar ? touchBarAltPlaceholder() : touchBarPlaceholder();

  m_menuBar = new MainMenuBar();
  m_notifications = new Notifications();
  m_contextBar = new ContextBar();
  m_statusBar = new StatusBar();
  m_colorBar = new ColorBar(colorBarPlaceholder()->align());
  m_toolBar = new ToolBar();
  m_tabsBar = new WorkspaceTabs(this);
  m_workspace = new Workspace();
  m_previewEditor = new PreviewEditorWindow();
  m_timeline = new Timeline();
  m_touchBar = TouchBar::create(touchbarParent->align());
  m_dock = new PanelDock({"Colors", "Layers"});
  m_dock->PanelChange.connect([](int index) { Preferences::instance().panelLayout.phoneTab(index); });

  m_workspace->setTabsBar(m_tabsBar);
  m_workspace->ActiveViewChanged.connect(&MainWindow::onActiveViewChange, this);

  // configure all widgets to expansives
  m_menuBar->setExpansive(true);
  m_contextBar->setExpansive(true);
  m_contextBar->setVisible(false);
  m_statusBar->setExpansive(true);
  m_colorBar->setExpansive(true);
  m_touchBar->setExpansive(true);
  m_toolBar->setExpansive(true);
  m_tabsBar->setExpansive(true);
  m_timeline->setExpansive(true);
  m_workspace->setExpansive(true);
  m_notifications->setVisible(false);

  // Setup the menus
  m_menuBar->setMenu(AppMenus::instance()->getRootMenu());

  // Add the widgets in the boxes
  menuBarPlaceholder()->addChild(m_menuBar);
  menuBarPlaceholder()->addChild(m_notifications);
  contextBarPlaceholder()->addChild(m_contextBar);
  colorBarPlaceholder()->addChild(m_colorBar);

  touchbarParent->addChild(m_touchBar.get());

  bool leftToolbar = Preferences::instance().general.leftToolBar();
  if (!has_config_value("general", "left_tool_bar"))
  {
    // TODO: Decide default based on DPI
#if defined(ANDROID)
    leftToolbar = true;
#endif
  }
  auto toolbarParent = leftToolbar ? toolBarAltPlaceholder() : this->toolBarPlaceholder();
  toolbarParent->addChild(m_toolBar);

  statusBarPlaceholder()->addChild(m_statusBar);
  tabsPlaceholder()->addChild(m_tabsBar);
  workspacePlaceholder()->addChild(m_workspace);
  timelinePlaceholder()->addChild(m_timeline);

  // Default splitter positions
  colorBarSplitter()->setPosition(m_colorBar->sizeHint().w);
  timelineSplitter()->setPosition(75);

  bool verticalTimeline = Preferences::instance().general.verticalTimeline();
  if (!has_config_value("general", "vertical_timeline"))
  {
    // TODO: Decide default based on DPI
#if defined(ANDROID)
    verticalTimeline = true;
#endif
  }
  timelineSplitter()->setAlign(verticalTimeline ? HORIZONTAL : VERTICAL);

#if defined(ANDROID)
  // On phones the timeline lives in the phone layout's panel dock; show it
  // (TIME hides it to make room for the canvas).
  if (!has_config_value("general", "visible_timeline"))
    Preferences::instance().general.visibleTimeline(true);
#endif

  bool touchBarVisible = Preferences::instance().touchBar.visible();
  if (!has_config_value("touch_bar", "visible"))
  {
    // TODO: Decide default based on DPI
#if defined(ANDROID)
    touchBarVisible = true;
#endif
    Preferences::instance().touchBar.visible(touchBarVisible);
  }

  // Prepare the window
  remapWindow();

  AppMenus::instance()->rebuildRecentList();
}

void MainWindow::alternateTimeline()
{
  auto old = Preferences::instance().general.verticalTimeline();
  Preferences::instance().general.verticalTimeline(!old);
  timelineSplitter()->setAlign(Preferences::instance().general.verticalTimeline() ? HORIZONTAL : VERTICAL);
  configureWorkspaceLayout();
}

void MainWindow::alternateToolbar()
{
  auto left = !Preferences::instance().general.leftToolBar();
  Preferences::instance().general.leftToolBar(left);
  auto parent = left ? toolBarAltPlaceholder() : toolBarPlaceholder();
  m_toolBar->parent()->removeChild(m_toolBar);
  parent->addChild(m_toolBar);
  remapWindow();
}

// View > Alternate Touchbar: cycles left -> right -> bottom -> left, saved
// separately for the phone and desktop layouts.
void MainWindow::alternateTouchbar()
{
  const int next = (touchBarPosition() + 1) % 3;
  if (m_phoneLayout)
  {
    Preferences::instance().touchBar.phonePosition(next);
  }
  else
  {
    Preferences::instance().touchBar.desktopPosition(next);
    Preferences::instance().touchBar.alternatePosition(next == 1);
  }
  placeTouchBar();
  configureWorkspaceLayout();
}

void MainWindow::toggleTouchbar()
{
  auto visible = !Preferences::instance().touchBar.visible();
  Preferences::instance().touchBar.visible(visible);
  configureWorkspaceLayout();
}

MainWindow::~MainWindow()
{
  if (m_devConsoleView)
  {
    if (m_devConsoleView->parent())
      m_workspace->removeView(m_devConsoleView);
    delete m_devConsoleView;
  }
  if (m_homeView)
  {
    if (m_homeView->parent())
      m_workspace->removeView(m_homeView);
    delete m_homeView;
  }
  delete m_contextBar;
  delete m_previewEditor;
  // The dock is only in the widget tree (which deletes it) in phone layout.
  if (m_dock && !m_dock->parent())
    delete m_dock;

  // Destroy the workspace first so ~Editor can dettach slots from
  // ColorBar. TODO this is a terrible hack for slot/signal stuff,
  // connections should be handle in a better/safer way.
  delete m_workspace;

  // Remove the root-menu from the menu-bar (because the rootmenu
  // module should destroy it).
  m_menuBar->setMenu(nullptr);
}

DocumentView* MainWindow::getDocView()
{
  return dynamic_cast<DocumentView*>(m_workspace->activeView());
}

HomeView* MainWindow::getHomeView()
{
  if (!m_homeView)
    m_homeView = new HomeView;
  return m_homeView;
}

void MainWindow::reloadMenus()
{
  m_menuBar->reload();

  layout();
  invalidate();
}

void MainWindow::showNotification(INotificationDelegate* del)
{
  m_notifications->addLink(del);
  m_notifications->setVisible(true);
  m_notifications->parent()->layout();
}

void MainWindow::showHomeOnOpen()
{
  if (!getHomeView()->parent())
  {
    TabView* selectedTab = m_tabsBar->getSelectedTab();

    // Show "Home" tab in the first position, and select it only if
    // there is no other view selected.
    m_workspace->addView(m_homeView, 0);
    if (selectedTab)
      m_tabsBar->selectTab(selectedTab);
    else
      m_tabsBar->selectTab(m_homeView);
  }
}

void MainWindow::showHome()
{
  if (!getHomeView()->parent())
  {
    m_workspace->addView(m_homeView, 0);
  }
  m_tabsBar->selectTab(m_homeView);
}

bool MainWindow::isHomeSelected()
{
  return (m_tabsBar->getSelectedTab() == m_homeView && m_homeView);
}

void MainWindow::showDevConsole()
{
  if (!m_devConsoleView)
    m_devConsoleView = new DevConsoleView;

  if (!m_devConsoleView->parent())
  {
    m_workspace->addView(m_devConsoleView);
    m_tabsBar->selectTab(m_devConsoleView);
  }
}

void MainWindow::setMode(Mode mode)
{
  // Check if we already are in the given mode.
  if (m_mode == mode)
    return;

  m_mode = mode;
  configureWorkspaceLayout();
}

bool MainWindow::getTimelineVisibility() const
{
  return Preferences::instance().general.visibleTimeline();
}

void MainWindow::setTimelineVisibility(bool visible)
{
  Preferences::instance().general.visibleTimeline(visible);

  configureWorkspaceLayout();
}

void MainWindow::popTimeline()
{
  Preferences& preferences = Preferences::instance();

  if (!preferences.general.autoshowTimeline())
    return;

  if (!getTimelineVisibility())
    setTimelineVisibility(true);
}

void MainWindow::showDataRecovery(crash::DataRecovery* dataRecovery)
{
  getHomeView()->showDataRecovery(dataRecovery);
}

bool MainWindow::onProcessMessage(ui::Message* msg)
{
  if (msg->type() == kOpenMessage)
    showHomeOnOpen();

  return Window::onProcessMessage(msg);
}

void MainWindow::onResize(ui::ResizeEvent& ev)
{
  const gfx::Rect& rc = ev.bounds();
  if (rc.w > 0 && rc.h > 0)
  {
    m_portrait = (rc.h > rc.w);
    updateLayout();
  }
  Window::onResize(ev);
}

// Phone layout when chosen in View > Layout, or (Auto) on a phone held
// upright. Desktop builds only use it when chosen explicitly.
bool MainWindow::wantsPhoneLayout() const
{
  switch (Preferences::instance().panelLayout.mode())
  {
  case 1:
    return true;
  case 2:
    return false;
  default:
#ifdef ANDROID
    return m_portrait;
#else
    return false;
#endif
  }
}

void MainWindow::updateLayout()
{
  const bool phone = wantsPhoneLayout();
  if (m_layoutApplied && phone == m_phoneLayout && m_portrait == m_layoutPortrait)
    return;
  applyLayout(phone, m_portrait);
}

// Desktop layout: color bar beside the canvas, timeline below or beside it.
// Phone layout: the color bar and the timeline share a tabbed panel (the
// dock), below the canvas in portrait or right of it in landscape; the
// splitter between canvas and dock can be dragged.
void MainWindow::applyLayout(bool phone, bool portrait)
{
  rememberDockSize();
  m_layoutApplied = true;
  m_phoneLayout = phone;
  m_layoutPortrait = portrait;

  // Take the movable panels out of wherever they are.
  m_dock->releasePanels();
  if (m_dock->parent())
    m_dock->parent()->removeChild(m_dock);
  if (m_colorBar->parent())
    m_colorBar->parent()->removeChild(m_colorBar);
  if (m_timeline->parent())
    m_timeline->parent()->removeChild(m_timeline);

  if (phone)
  {
    m_colorBar->setHorizontal(portrait);
    m_dock->setPanel(0, m_colorBar);
    m_dock->setPanel(1, m_timeline);
    m_dock->selectPanel(Preferences::instance().panelLayout.phoneTab());
    timelinePlaceholder()->addChild(m_dock);
    colorBarSplitter()->setPosition(0);
    timelineSplitter()->setAlign(portrait ? VERTICAL : HORIZONTAL);
    timelineSplitter()->setPosition(portrait ? Preferences::instance().panelLayout.phonePortraitSize()
                                             : Preferences::instance().panelLayout.phoneLandscapeSize());
  }
  else
  {
    m_colorBar->setHorizontal(false);
    colorBarPlaceholder()->addChild(m_colorBar);
    colorBarSplitter()->setPosition(m_colorBar->sizeHint().w);
    timelinePlaceholder()->addChild(m_timeline);
    timelineSplitter()->setAlign(Preferences::instance().general.verticalTimeline() ? HORIZONTAL : VERTICAL);
    timelineSplitter()->setPosition(75);
  }

  placeTouchBar();
  configureWorkspaceLayout();
}

// Stores the canvas/dock split of the phone layout so it comes back the
// same next time (separately for portrait and landscape).
void MainWindow::rememberDockSize()
{
  if (!m_layoutApplied || !m_phoneLayout)
    return;
  const int size = static_cast<int>(timelineSplitter()->getPosition());
  if (size <= 5 || size >= 95)
    return;
  if (m_layoutPortrait)
    Preferences::instance().panelLayout.phonePortraitSize(size);
  else
    Preferences::instance().panelLayout.phoneLandscapeSize(size);
}

// Touch bar position for the current layout: 0 left, 1 right, 2 bottom.
int MainWindow::touchBarPosition() const
{
  if (m_phoneLayout)
    return Preferences::instance().touchBar.phonePosition();
  const int pos = Preferences::instance().touchBar.desktopPosition();
  if (pos >= 0)
    return pos;
  return Preferences::instance().touchBar.alternatePosition() ? 1 : 0;
}

void MainWindow::placeTouchBar()
{
  if (m_touchBar->parent())
    m_touchBar->parent()->removeChild(m_touchBar.get());
  switch (touchBarPosition())
  {
  case 1:
    m_touchBar->setAlign(VERTICAL);
    touchBarAltPlaceholder()->addChild(m_touchBar.get());
    break;
  case 2:
    m_touchBar->setAlign(HORIZONTAL);
    touchBarBottomPlaceholder()->addChild(m_touchBar.get());
    break;
  default:
    m_touchBar->setAlign(VERTICAL);
    touchBarPlaceholder()->addChild(m_touchBar.get());
    break;
  }
}

void MainWindow::onSaveLayout(SaveLayoutEvent& ev)
{
  rememberDockSize();
  Window::onSaveLayout(ev);
}

// When the active view is changed from methods like
// Workspace::splitView(), this function is called, and we have to
// inform to the UIContext that the current view has changed.
void MainWindow::onActiveViewChange()
{
  if (DocumentView* docView = getDocView())
    UIContext::instance()->setActiveView(docView);
  else
    UIContext::instance()->setActiveView(nullptr);

  configureWorkspaceLayout();
}

bool MainWindow::isTabModified(Tabs* tabs, TabView* tabView)
{
  if (DocumentView* docView = dynamic_cast<DocumentView*>(tabView))
  {
    Document* document = docView->document();
    return document->isModified();
  }
  else
  {
    return false;
  }
}

bool MainWindow::canCloneTab(Tabs* tabs, TabView* tabView)
{
  ASSERT(tabView)

  WorkspaceView* view = dynamic_cast<WorkspaceView*>(tabView);
  return view->canCloneWorkspaceView();
}

void MainWindow::onSelectTab(Tabs* tabs, TabView* tabView)
{
  if (!tabView)
    return;

  WorkspaceView* view = dynamic_cast<WorkspaceView*>(tabView);
  if (m_workspace->activeView() != view)
    m_workspace->setActiveView(view);
}

void MainWindow::onCloseTab(Tabs* tabs, TabView* tabView)
{
  WorkspaceView* view = dynamic_cast<WorkspaceView*>(tabView);
  ASSERT(view);
  if (view)
    m_workspace->closeView(view, false);
}

void MainWindow::onCloneTab(Tabs* tabs, TabView* tabView, int pos)
{
  EditorView::SetScrollUpdateMethod(EditorView::KeepOrigin);

  WorkspaceView* view = dynamic_cast<WorkspaceView*>(tabView);
  WorkspaceView* clone = view->cloneWorkspaceView();
  ASSERT(clone);

  m_workspace->addViewToPanel(static_cast<WorkspaceTabs*>(tabs)->panel(), clone, true, pos);

  clone->onClonedFrom(view);
}

void MainWindow::onContextMenuTab(Tabs* tabs, TabView* tabView)
{
  WorkspaceView* view = dynamic_cast<WorkspaceView*>(tabView);
  ASSERT(view);
  if (view)
    view->onTabPopup(m_workspace);
}

void MainWindow::onMouseOverTab(Tabs* tabs, TabView* tabView)
{
  // Note: tabView can be NULL
  if (DocumentView* docView = dynamic_cast<DocumentView*>(tabView))
  {
    Document* document = docView->document();

    std::string name;
    if (Preferences::instance().general.showFullPath())
      name = document->filename();
    else
      name = base::get_file_name(document->filename());

    m_statusBar->setStatusText(250, "%s", name.c_str());
  }
  else
  {
    m_statusBar->clearText();
  }
}

DropViewPreviewResult MainWindow::onFloatingTab(Tabs* tabs, TabView* tabView, const gfx::Point& pos)
{
  return m_workspace->setDropViewPreview(pos, dynamic_cast<WorkspaceView*>(tabView), static_cast<WorkspaceTabs*>(tabs));
}

void MainWindow::onDockingTab(Tabs* tabs, TabView* tabView)
{
  m_workspace->removeDropViewPreview();
}

DropTabResult MainWindow::onDropTab(Tabs* tabs, TabView* tabView, const gfx::Point& pos, bool clone)
{
  m_workspace->removeDropViewPreview();

  DropViewAtResult result = m_workspace->dropViewAt(pos, dynamic_cast<WorkspaceView*>(tabView), clone);

  if (result == DropViewAtResult::MOVED_TO_OTHER_PANEL)
    return DropTabResult::REMOVE;
  else if (result == DropViewAtResult::CLONED_VIEW)
    return DropTabResult::DONT_REMOVE;
  else
    return DropTabResult::NOT_HANDLED;
}

void MainWindow::configureWorkspaceLayout()
{
  bool normal = (m_mode == NormalMode);
  bool isDoc = (getDocView() != nullptr);

  m_menuBar->setVisible(normal);
  m_tabsBar->setVisible(normal);
  colorBarPlaceholder()->setVisible(normal && isDoc && !m_phoneLayout);
  m_touchBar->setVisible(normal && isDoc && Preferences::instance().touchBar.visible());
  m_toolBar->setVisible(normal && isDoc);
  m_statusBar->setVisible(normal);
  m_contextBar->setVisible(isDoc && (m_mode == NormalMode || m_mode == ContextBarAndTimelineMode));
  timelinePlaceholder()->setVisible(isDoc && (m_mode == NormalMode || m_mode == ContextBarAndTimelineMode) &&
                                    Preferences::instance().general.visibleTimeline());

  if (m_contextBar->isVisible())
  {
    m_contextBar->updateForActiveTool();
  }

  layout();
  invalidate();
}

} // namespace app
