// Besprited | Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "app/app.h"
#include "app/commands/command.h"
#include "app/commands/params.h"
#include "app/pref/preferences.h"
#include "app/ui/main_window.h"

namespace app
{

// View > Layout > Auto/Phone/Desktop: chooses between the desktop layout
// (panels beside the canvas) and the phone layout (tabbed panel dock).
// "Auto" picks the phone layout on phones held upright.
class PanelLayoutCommand : public Command
{
public:
  PanelLayoutCommand()
    : Command{"PanelLayout", "Panel Layout", CmdUIOnlyFlag}
  {
  }

protected:
  void onLoadParams(const Params& params) override
  {
    const std::string mode = params.get("mode");
    m_mode = (mode == "phone" ? 1 : mode == "desktop" ? 2 : 0);
  }

  bool onChecked(Context* context) override { return Preferences::instance().panelLayout.mode() == m_mode; }

  void onExecute(Context* context) override
  {
    Preferences::instance().panelLayout.mode(m_mode);
    App::instance()->mainWindow()->updateLayout();
  }

private:
  int m_mode = 0;
};

std::unique_ptr<Command> CommandFactory::createPanelLayoutCommand()
{
  return std::make_unique<PanelLayoutCommand>();
}

} // namespace app
