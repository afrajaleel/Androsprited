// Besprited | Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "app/ui/panel_dock.h"

#include <algorithm>

namespace app
{

PanelDock::PanelDock(const std::vector<std::string>& tabNames)
  : m_tabs(static_cast<int>(tabNames.size()))
{
  noBorderNoChildSpacing();
  m_content.noBorderNoChildSpacing();
  m_content.setExpansive(true);

  for (const auto& name : tabNames)
    m_tabs.addItem(name);
  m_panels.resize(tabNames.size(), nullptr);

  m_tabs.ItemChange.connect(
      [this](ButtonSet::Item*)
      {
        const int index = m_tabs.selectedItem();
        if (index >= 0)
        {
          selectPanel(index);
          PanelChange(index);
        }
      });

  addChild(&m_tabs);
  addChild(&m_content);
  setExpansive(true);
  m_tabs.setSelectedItem(0, false);
}

void PanelDock::setPanel(int index, ui::Widget* widget)
{
  if (index < 0 || index >= static_cast<int>(m_panels.size()))
    return;
  if (m_panels[index] && m_panels[index]->parent() == &m_content)
    m_content.removeChild(m_panels[index]);
  m_panels[index] = widget;
  if (widget)
  {
    m_content.addChild(widget);
    widget->setExpansive(true);
  }
  selectPanel(m_selected);
}

void PanelDock::releasePanels()
{
  for (auto*& widget : m_panels)
  {
    if (widget && widget->parent() == &m_content)
    {
      m_content.removeChild(widget);
      widget->setVisible(true);
    }
    widget = nullptr;
  }
}

void PanelDock::selectPanel(int index)
{
  index = std::clamp(index, 0, static_cast<int>(m_panels.size()) - 1);
  m_selected = index;
  if (m_tabs.selectedItem() != index)
    m_tabs.setSelectedItem(index, false);
  for (int i = 0; i < static_cast<int>(m_panels.size()); ++i)
    if (m_panels[i])
      m_panels[i]->setVisible(i == index);
  layout();
  invalidate();
}

} // namespace app
