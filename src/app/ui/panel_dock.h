// Besprited | Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

#pragma once

#include "app/ui/button_set.h"
#include "base/signal.h"
#include "ui/box.h"

#include <string>
#include <vector>

namespace app
{

// A panel that stacks several widgets (e.g. the color bar and the timeline)
// behind a row of tabs, showing one at a time. Used by the phone layout
// (see MainWindow::applyLayout()).
class PanelDock : public ui::VBox
{
public:
  explicit PanelDock(const std::vector<std::string>& tabNames);

  // Puts "widget" in the tab at "index" (replacing what was there).
  void setPanel(int index, ui::Widget* widget);
  // Takes all panels out of the dock without deleting them.
  void releasePanels();

  [[nodiscard]] int selectedPanel() const { return m_selected; }
  void selectPanel(int index);

  base::Signal1<void, int> PanelChange;

private:
  ButtonSet m_tabs;
  ui::VBox m_content;
  std::vector<ui::Widget*> m_panels;
  int m_selected = 0;
};

} // namespace app
