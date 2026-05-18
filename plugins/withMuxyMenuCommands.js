const { withAppDelegate, withXcodeProject } = require('@expo/config-plugins');
const { addBuildSourceFileToGroup, getProjectName } = require('@expo/config-plugins/build/ios/utils/Xcodeproj');
const fs = require('node:fs');
const path = require('node:path');

const swiftSource = `import React

@objc(MuxyMenuCommands)
class MuxyMenuCommands: RCTEventEmitter {
  private static weak var shared: MuxyMenuCommands?
  private var hasListeners = false
  private var pending: [[String: Any]] = []

  override init() {
    super.init()
    MuxyMenuCommands.shared = self
  }

  override static func requiresMainQueueSetup() -> Bool {
    true
  }

  override func supportedEvents() -> [String] {
    ["MuxyMenuCommand"]
  }

  override func startObserving() {
    hasListeners = true
    pending.forEach { sendEvent(withName: "MuxyMenuCommand", body: $0) }
    pending.removeAll()
  }

  override func stopObserving() {
    hasListeners = false
  }

  @objc(addListener:)
  override func addListener(_ eventName: String!) {
    super.addListener(eventName)
  }

  @objc(removeListeners:)
  override func removeListeners(_ count: Double) {
    super.removeListeners(count)
  }

  static func emit(_ body: [String: Any]) {
    DispatchQueue.main.async {
      guard let shared else { return }
      if shared.hasListeners {
        shared.sendEvent(withName: "MuxyMenuCommand", body: body)
      } else {
        shared.pending.append(body)
      }
    }
  }
}
`;

const menuBlock = `
  public override func buildMenu(with builder: UIMenuBuilder) {
    super.buildMenu(with: builder)

    let newTab = UIKeyCommand(
      title: "New Tab",
      action: #selector(newMuxyTab),
      input: "t",
      modifierFlags: .command
    )

    builder.insertChild(
      UIMenu(title: "", options: .displayInline, children: [newTab]),
      atStartOfMenu: .file
    )

    var tabCommands: [UICommand] = []
    for tabNumber in 1...9 {
      tabCommands.append(UIKeyCommand(
        title: "Tab " + String(tabNumber),
        action: #selector(selectMuxyTab(_:)),
        input: String(tabNumber),
        modifierFlags: .command,
        propertyList: tabNumber
      ))
    }

    builder.insertChild(
      UIMenu(title: "", options: .displayInline, children: tabCommands),
      atEndOfMenu: .window
    )
  }

  @objc func newMuxyTab() {
    MuxyMenuCommands.emit(["type": "newTab"])
  }

  @objc func selectMuxyTab(_ sender: UICommand) {
    guard let index = sender.propertyList as? Int else { return }
    MuxyMenuCommands.emit(["type": "selectTab", "index": index - 1])
  }
`;

function upsertMenuBlock(contents) {
  if (contents.includes('func newMuxyTab()')) return contents;
  const classMatch = contents.match(/class\s+AppDelegate\s*:\s*[^{]*\{/);
  if (!classMatch) return null;
  const classStart = classMatch.index + classMatch[0].length;
  let depth = 1;
  for (let i = classStart; i < contents.length; i += 1) {
    const ch = contents[i];
    if (ch === '{') depth += 1;
    else if (ch === '}') {
      depth -= 1;
      if (depth === 0) {
        return contents.slice(0, i) + menuBlock + contents.slice(i);
      }
    }
  }
  return null;
}

module.exports = function withMuxyMenuCommands(config) {
  config = withAppDelegate(config, (mod) => {
    const next = upsertMenuBlock(mod.modResults.contents);
    if (next) {
      mod.modResults.contents = next;
    } else {
      console.warn('[withMuxyMenuCommands] AppDelegate insertion point not found; skipping menu commands');
    }
    return mod;
  });

  return withXcodeProject(config, (mod) => {
    const projectName = getProjectName(mod.modRequest.projectRoot);
    const filePath = path.join(mod.modRequest.platformProjectRoot, projectName, 'MuxyMenuCommands.swift');
    fs.writeFileSync(filePath, swiftSource);

    const projectFile = path.join(projectName, 'MuxyMenuCommands.swift');
    if (!mod.modResults.hasFile(projectFile)) {
      mod.modResults = addBuildSourceFileToGroup({
        filepath: projectFile,
        groupName: projectName,
        project: mod.modResults,
        verbose: true,
      });
    }

    return mod;
  });
};
