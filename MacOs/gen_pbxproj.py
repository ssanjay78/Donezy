#!/usr/bin/env python3
"""Generate Donezy.xcodeproj/project.pbxproj for the two-target (app + widget) iOS port.

Run once: python3 gen_pbxproj.py  — it writes Donezy.xcodeproj/project.pbxproj.
IDs are deterministic (derived from a counter) so re-running is stable.
"""
import os

ROOT = os.path.dirname(os.path.abspath(__file__))

# ── File lists ──────────────────────────────────────────────────────────────
SHARED = [
    "Shared/AppGroup.swift", "Shared/Models.swift", "Shared/Time.swift",
    "Shared/AppDatabase.swift", "Shared/HobbyRepository.swift",
    "Shared/CategoryDefinitions.swift", "Shared/StreakMath.swift",
    "Shared/Achievements.swift", "Shared/BackupCodec.swift",
    "Shared/RecurrenceMath.swift", "Shared/Theme.swift",
    "Shared/ReminderStatus.swift", "Shared/Preferences.swift",
]
APP = [
    "Donezy/DonezyApp.swift", "Donezy/SharedComponents.swift",
    "Donezy/HobbyViewModel.swift", "Donezy/NotificationManager.swift",
    "Donezy/NotificationDelegate.swift", "Donezy/WidgetBridge.swift",
    "Donezy/SnackbarHost.swift", "Donezy/CreateTrackerSheet.swift",
    "Donezy/HomeScreen.swift", "Donezy/DetailScreen.swift",
    "Donezy/SettingsScreen.swift", "Donezy/ArchiveScreen.swift",
    "Donezy/AboutScreen.swift", "Donezy/NotificationSoundPlayer.swift",
    "Donezy/OnboardingScreen.swift",
]
WIDGET = ["DonezyWidget/NextDueWidget.swift"]

# Files compiled into the app target and into the widget target.
APP_SOURCES = SHARED + APP
WIDGET_SOURCES = SHARED + WIDGET

APP_RESOURCES = ["Donezy/Assets.xcassets"]
WIDGET_RESOURCES = ["DonezyWidget/Assets.xcassets"]

# ── Stable ID generator ───────────────────────────────────────────────────────
_counter = [0]
def oid():
    _counter[0] += 1
    return "DEAD%020X" % _counter[0]

# Assign a file reference id to every distinct path.
all_paths = sorted(set(APP_SOURCES + WIDGET_SOURCES + APP_RESOURCES + WIDGET_RESOURCES
                       + ["Donezy/Info.plist", "DonezyWidget/Info.plist",
                          "Donezy/Donezy.entitlements", "DonezyWidget/DonezyWidget.entitlements"]))
fileref = {p: oid() for p in all_paths}

# Product references
app_product = oid()
widget_product = oid()
fileref["Donezy.app"] = app_product
fileref["DonezyWidget.appex"] = widget_product

def filetype(path):
    if path.endswith(".swift"): return "sourcecode.swift"
    if path.endswith(".xcassets"): return "folder.assetcatalog"
    if path.endswith(".plist"): return "text.plist.xml"
    if path.endswith(".entitlements"): return "text.plist.entitlements"
    return "text"

# ── Build file entries (path, target) → build-file id ──────────────────────────
buildfiles = {}  # (path, target) -> id
def bf(path, target):
    key = (path, target)
    if key not in buildfiles:
        buildfiles[key] = oid()
    return buildfiles[key]

for p in APP_SOURCES: bf(p, "app")
for p in APP_RESOURCES: bf(p, "app")
for p in WIDGET_SOURCES: bf(p, "widget")
for p in WIDGET_RESOURCES: bf(p, "widget")
# Embed appex into app
embed_bf = oid()

# ── Container proxy + target dependency (app → widget) ──────────────────────────
proj_id = oid()
app_target = oid()
widget_target = oid()
proxy_id = oid()
dep_id = oid()

# Build phases
app_sources_phase = oid(); app_resources_phase = oid(); app_frameworks_phase = oid(); app_embed_phase = oid()
widget_sources_phase = oid(); widget_resources_phase = oid(); widget_frameworks_phase = oid()

# Groups
main_group = oid(); products_group = oid()
shared_group = oid(); app_group = oid(); widget_group = oid()

# Config lists
proj_cfg_list = oid(); app_cfg_list = oid(); widget_cfg_list = oid()
proj_debug = oid(); proj_release = oid()
app_debug = oid(); app_release = oid()
widget_debug = oid(); widget_release = oid()

DEV_TEAM = ""  # fill in your Apple Developer team ID, or set in Xcode.
APP_BUNDLE_ID = "com.swarnkary.donezy"
WIDGET_BUNDLE_ID = "com.swarnkary.donezy.DonezyWidget"

def basename(p): return p.rsplit("/", 1)[-1]

lines = []
def w(s=""): lines.append(s)

w("// !$*UTF8*$!")
w("{")
w("\tarchiveVersion = 1;")
w("\tclasses = {")
w("\t};")
w("\tobjectVersion = 56;")
w("\tobjects = {")

# PBXBuildFile
w("\n/* Begin PBXBuildFile section */")
for (path, target), bid in buildfiles.items():
    w('\t\t%s /* %s in build */ = {isa = PBXBuildFile; fileRef = %s /* %s */; };'
      % (bid, basename(path), fileref[path], basename(path)))
w('\t\t%s /* DonezyWidget.appex in Embed Foundation Extensions */ = {isa = PBXBuildFile; fileRef = %s /* DonezyWidget.appex */; settings = {ATTRIBUTES = (RemoveHeadersOnCopy, ); }; };'
  % (embed_bf, widget_product))
w("/* End PBXBuildFile section */")

# PBXContainerItemProxy
w("\n/* Begin PBXContainerItemProxy section */")
w('\t\t%s /* PBXContainerItemProxy */ = {' % proxy_id)
w('\t\t\tisa = PBXContainerItemProxy;')
w('\t\t\tcontainerPortal = %s /* Project object */;' % proj_id)
w('\t\t\tproxyType = 1;')
w('\t\t\tremoteGlobalIDString = %s;' % widget_target)
w('\t\t\tremoteInfo = DonezyWidget;')
w('\t\t};')
w("/* End PBXContainerItemProxy section */")

# PBXCopyFilesBuildPhase (embed extension)
w("\n/* Begin PBXCopyFilesBuildPhase section */")
w('\t\t%s /* Embed Foundation Extensions */ = {' % app_embed_phase)
w('\t\t\tisa = PBXCopyFilesBuildPhase;')
w('\t\t\tbuildActionMask = 2147483647;')
w('\t\t\tdstPath = "";')
w('\t\t\tdstSubfolderSpec = 13;')
w('\t\t\tfiles = (')
w('\t\t\t\t%s /* DonezyWidget.appex in Embed Foundation Extensions */,' % embed_bf)
w('\t\t\t);')
w('\t\t\tname = "Embed Foundation Extensions";')
w('\t\t\trunOnlyForDeploymentPostprocessing = 0;')
w('\t\t};')
w("/* End PBXCopyFilesBuildPhase section */")

# PBXFileReference
w("\n/* Begin PBXFileReference section */")
for p in all_paths:
    w('\t\t%s /* %s */ = {isa = PBXFileReference; lastKnownFileType = %s; name = "%s"; path = "%s"; sourceTree = "<group>"; };'
      % (fileref[p], basename(p), filetype(p), basename(p), p))
w('\t\t%s /* Donezy.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = Donezy.app; sourceTree = BUILT_PRODUCTS_DIR; };' % app_product)
w('\t\t%s /* DonezyWidget.appex */ = {isa = PBXFileReference; explicitFileType = "wrapper.app-extension"; includeInIndex = 0; path = DonezyWidget.appex; sourceTree = BUILT_PRODUCTS_DIR; };' % widget_product)
w("/* End PBXFileReference section */")

# PBXFrameworksBuildPhase
w("\n/* Begin PBXFrameworksBuildPhase section */")
for ph in (app_frameworks_phase, widget_frameworks_phase):
    w('\t\t%s /* Frameworks */ = {' % ph)
    w('\t\t\tisa = PBXFrameworksBuildPhase;')
    w('\t\t\tbuildActionMask = 2147483647;')
    w('\t\t\tfiles = (')
    w('\t\t\t);')
    w('\t\t\trunOnlyForDeploymentPostprocessing = 0;')
    w('\t\t};')
w("/* End PBXFrameworksBuildPhase section */")

# PBXGroup
w("\n/* Begin PBXGroup section */")
# main
w('\t\t%s = {' % main_group)
w('\t\t\tisa = PBXGroup;')
w('\t\t\tchildren = (')
w('\t\t\t\t%s /* Shared */,' % shared_group)
w('\t\t\t\t%s /* Donezy */,' % app_group)
w('\t\t\t\t%s /* DonezyWidget */,' % widget_group)
w('\t\t\t\t%s /* Products */,' % products_group)
w('\t\t\t);')
w('\t\t\tsourceTree = "<group>";')
w('\t\t};')
# products
w('\t\t%s /* Products */ = {' % products_group)
w('\t\t\tisa = PBXGroup;')
w('\t\t\tchildren = (')
w('\t\t\t\t%s /* Donezy.app */,' % app_product)
w('\t\t\t\t%s /* DonezyWidget.appex */,' % widget_product)
w('\t\t\t);')
w('\t\t\tname = Products;')
w('\t\t\tsourceTree = "<group>";')
w('\t\t};')
def group(gid, name, paths):
    w('\t\t%s /* %s */ = {' % (gid, name))
    w('\t\t\tisa = PBXGroup;')
    w('\t\t\tchildren = (')
    for p in paths:
        w('\t\t\t\t%s /* %s */,' % (fileref[p], basename(p)))
    w('\t\t\t);')
    w('\t\t\tname = %s;' % name)
    w('\t\t\tsourceTree = "<group>";')
    w('\t\t};')
group(shared_group, "Shared", SHARED)
group(app_group, "Donezy", APP + APP_RESOURCES + ["Donezy/Info.plist", "Donezy/Donezy.entitlements"])
group(widget_group, "DonezyWidget", WIDGET + WIDGET_RESOURCES + ["DonezyWidget/Info.plist", "DonezyWidget/DonezyWidget.entitlements"])
w("/* End PBXGroup section */")

# PBXNativeTarget
w("\n/* Begin PBXNativeTarget section */")
# app
w('\t\t%s /* Donezy */ = {' % app_target)
w('\t\t\tisa = PBXNativeTarget;')
w('\t\t\tbuildConfigurationList = %s /* Build configuration list for PBXNativeTarget "Donezy" */;' % app_cfg_list)
w('\t\t\tbuildPhases = (')
w('\t\t\t\t%s /* Sources */,' % app_sources_phase)
w('\t\t\t\t%s /* Frameworks */,' % app_frameworks_phase)
w('\t\t\t\t%s /* Resources */,' % app_resources_phase)
w('\t\t\t\t%s /* Embed Foundation Extensions */,' % app_embed_phase)
w('\t\t\t);')
w('\t\t\tbuildRules = (')
w('\t\t\t);')
w('\t\t\tdependencies = (')
w('\t\t\t\t%s /* PBXTargetDependency */,' % dep_id)
w('\t\t\t);')
w('\t\t\tname = Donezy;')
w('\t\t\tproductName = Donezy;')
w('\t\t\tproductReference = %s /* Donezy.app */;' % app_product)
w('\t\t\tproductType = "com.apple.product-type.application";')
w('\t\t};')
# widget
w('\t\t%s /* DonezyWidget */ = {' % widget_target)
w('\t\t\tisa = PBXNativeTarget;')
w('\t\t\tbuildConfigurationList = %s /* Build configuration list for PBXNativeTarget "DonezyWidget" */;' % widget_cfg_list)
w('\t\t\tbuildPhases = (')
w('\t\t\t\t%s /* Sources */,' % widget_sources_phase)
w('\t\t\t\t%s /* Frameworks */,' % widget_frameworks_phase)
w('\t\t\t\t%s /* Resources */,' % widget_resources_phase)
w('\t\t\t);')
w('\t\t\tbuildRules = (')
w('\t\t\t);')
w('\t\t\tdependencies = (')
w('\t\t\t);')
w('\t\t\tname = DonezyWidget;')
w('\t\t\tproductName = DonezyWidget;')
w('\t\t\tproductReference = %s /* DonezyWidget.appex */;' % widget_product)
w('\t\t\tproductType = "com.apple.product-type.app-extension";')
w('\t\t};')
w("/* End PBXNativeTarget section */")

# PBXProject
w("\n/* Begin PBXProject section */")
w('\t\t%s /* Project object */ = {' % proj_id)
w('\t\t\tisa = PBXProject;')
w('\t\t\tattributes = {')
w('\t\t\t\tBuildIndependentTargetsInParallel = 1;')
w('\t\t\t\tLastSwiftUpdateCheck = 1600;')
w('\t\t\t\tLastUpgradeCheck = 1600;')
w('\t\t\t\tTargetAttributes = {')
w('\t\t\t\t\t%s = { CreatedOnToolsVersion = 16.0; };' % app_target)
w('\t\t\t\t\t%s = { CreatedOnToolsVersion = 16.0; };' % widget_target)
w('\t\t\t\t};')
w('\t\t\t};')
w('\t\t\tbuildConfigurationList = %s /* Build configuration list for PBXProject "Donezy" */;' % proj_cfg_list)
w('\t\t\tcompatibilityVersion = "Xcode 14.0";')
w('\t\t\tdevelopmentRegion = en;')
w('\t\t\thasScannedForEncodings = 0;')
w('\t\t\tknownRegions = (en, Base, );')
w('\t\t\tmainGroup = %s;' % main_group)
w('\t\t\tproductRefGroup = %s /* Products */;' % products_group)
w('\t\t\tprojectDirPath = "";')
w('\t\t\tprojectRoot = "";')
w('\t\t\ttargets = (')
w('\t\t\t\t%s /* Donezy */,' % app_target)
w('\t\t\t\t%s /* DonezyWidget */,' % widget_target)
w('\t\t\t);')
w('\t\t};')
w("/* End PBXProject section */")

# PBXResourcesBuildPhase
w("\n/* Begin PBXResourcesBuildPhase section */")
def resphase(pid, paths, target):
    w('\t\t%s /* Resources */ = {' % pid)
    w('\t\t\tisa = PBXResourcesBuildPhase;')
    w('\t\t\tbuildActionMask = 2147483647;')
    w('\t\t\tfiles = (')
    for p in paths:
        w('\t\t\t\t%s /* %s in Resources */,' % (bf(p, target), basename(p)))
    w('\t\t\t);')
    w('\t\t\trunOnlyForDeploymentPostprocessing = 0;')
    w('\t\t};')
resphase(app_resources_phase, APP_RESOURCES, "app")
resphase(widget_resources_phase, WIDGET_RESOURCES, "widget")
w("/* End PBXResourcesBuildPhase section */")

# PBXSourcesBuildPhase
w("\n/* Begin PBXSourcesBuildPhase section */")
def srcphase(pid, paths, target):
    w('\t\t%s /* Sources */ = {' % pid)
    w('\t\t\tisa = PBXSourcesBuildPhase;')
    w('\t\t\tbuildActionMask = 2147483647;')
    w('\t\t\tfiles = (')
    for p in paths:
        w('\t\t\t\t%s /* %s in Sources */,' % (bf(p, target), basename(p)))
    w('\t\t\t);')
    w('\t\t\trunOnlyForDeploymentPostprocessing = 0;')
    w('\t\t};')
srcphase(app_sources_phase, APP_SOURCES, "app")
srcphase(widget_sources_phase, WIDGET_SOURCES, "widget")
w("/* End PBXSourcesBuildPhase section */")

# PBXTargetDependency
w("\n/* Begin PBXTargetDependency section */")
w('\t\t%s /* PBXTargetDependency */ = {' % dep_id)
w('\t\t\tisa = PBXTargetDependency;')
w('\t\t\ttarget = %s /* DonezyWidget */;' % widget_target)
w('\t\t\ttargetProxy = %s /* PBXContainerItemProxy */;' % proxy_id)
w('\t\t};')
w("/* End PBXTargetDependency section */")

# XCBuildConfiguration
w("\n/* Begin XCBuildConfiguration section */")
def proj_common(debug):
    s = [
        'ALWAYS_SEARCH_USER_PATHS = NO;',
        'CLANG_ANALYZER_NONNULL = YES;',
        'CLANG_ENABLE_MODULES = YES;',
        'CLANG_ENABLE_OBJC_ARC = YES;',
        'ENABLE_STRICT_OBJC_MSGSEND = YES;',
        'GCC_C_LANGUAGE_STANDARD = gnu17;',
        'IPHONEOS_DEPLOYMENT_TARGET = 17.0;',
        'MTL_ENABLE_DEBUG_INFO = %s;' % ("INCLUDE_SOURCE" if debug else "NO"),
        'SDKROOT = iphoneos;',
        'SWIFT_VERSION = 5.0;',
    ]
    if debug:
        s += ['DEBUG_INFORMATION_FORMAT = dwarf;',
              'ENABLE_TESTABILITY = YES;',
              'GCC_OPTIMIZATION_LEVEL = 0;',
              'GCC_PREPROCESSOR_DEFINITIONS = ("DEBUG=1", "$(inherited)", );',
              'ONLY_ACTIVE_ARCH = YES;',
              'SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG $(inherited)";',
              'SWIFT_OPTIMIZATION_LEVEL = "-Onone";']
    else:
        s += ['DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";',
              'ENABLE_NS_ASSERTIONS = NO;',
              'SWIFT_COMPILATION_MODE = wholemodule;',
              'VALIDATE_PRODUCT = YES;']
    return s

def emit_cfg(cid, name, settings):
    w('\t\t%s /* %s */ = {' % (cid, name))
    w('\t\t\tisa = XCBuildConfiguration;')
    w('\t\t\tbuildSettings = {')
    for line in settings:
        w('\t\t\t\t%s' % line)
    w('\t\t\t};')
    w('\t\t\tname = %s;' % name)
    w('\t\t};')

emit_cfg(proj_debug, "Debug", proj_common(True))
emit_cfg(proj_release, "Release", proj_common(False))

def app_settings():
    return [
        'ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;',
        'ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;',
        'CODE_SIGN_ENTITLEMENTS = Donezy/Donezy.entitlements;',
        'CODE_SIGN_STYLE = Automatic;',
        'CURRENT_PROJECT_VERSION = 1;',
        ('DEVELOPMENT_TEAM = "%s";' % DEV_TEAM),
        'ENABLE_PREVIEWS = YES;',
        'GENERATE_INFOPLIST_FILE = NO;',
        'INFOPLIST_FILE = Donezy/Info.plist;',
        'INFOPLIST_KEY_UILaunchScreen_Generation = YES;',
        'LD_RUNPATH_SEARCH_PATHS = ("$(inherited)", "@executable_path/Frameworks", );',
        'MARKETING_VERSION = 1.0.0;',
        ('PRODUCT_BUNDLE_IDENTIFIER = %s;' % APP_BUNDLE_ID),
        'PRODUCT_NAME = "$(TARGET_NAME)";',
        'SWIFT_EMIT_LOC_STRINGS = YES;',
        'TARGETED_DEVICE_FAMILY = "1,2";',
    ]

def widget_settings():
    return [
        'ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;',
        'ASSETCATALOG_COMPILER_WIDGET_BACKGROUND_COLOR_NAME = WidgetBackground;',
        'CODE_SIGN_ENTITLEMENTS = DonezyWidget/DonezyWidget.entitlements;',
        'CODE_SIGN_STYLE = Automatic;',
        'CURRENT_PROJECT_VERSION = 1;',
        ('DEVELOPMENT_TEAM = "%s";' % DEV_TEAM),
        'ENABLE_PREVIEWS = YES;',
        'GENERATE_INFOPLIST_FILE = NO;',
        'INFOPLIST_FILE = DonezyWidget/Info.plist;',
        'INFOPLIST_KEY_CFBundleDisplayName = Donezy;',
        'INFOPLIST_KEY_NSHumanReadableCopyright = "";',
        'LD_RUNPATH_SEARCH_PATHS = ("$(inherited)", "@executable_path/Frameworks", "@executable_path/../../Frameworks", );',
        'MARKETING_VERSION = 1.0.0;',
        ('PRODUCT_BUNDLE_IDENTIFIER = %s;' % WIDGET_BUNDLE_ID),
        'PRODUCT_NAME = "$(TARGET_NAME)";',
        'SKIP_INSTALL = YES;',
        'SWIFT_EMIT_LOC_STRINGS = YES;',
        'TARGETED_DEVICE_FAMILY = "1,2";',
    ]

emit_cfg(app_debug, "Debug", app_settings())
emit_cfg(app_release, "Release", app_settings())
emit_cfg(widget_debug, "Debug", widget_settings())
emit_cfg(widget_release, "Release", widget_settings())
w("/* End XCBuildConfiguration section */")

# XCConfigurationList
w("\n/* Begin XCConfigurationList section */")
def cfg_list(cid, name, debug, release):
    w('\t\t%s /* Build configuration list for %s */ = {' % (cid, name))
    w('\t\t\tisa = XCConfigurationList;')
    w('\t\t\tbuildConfigurations = (')
    w('\t\t\t\t%s /* Debug */,' % debug)
    w('\t\t\t\t%s /* Release */,' % release)
    w('\t\t\t);')
    w('\t\t\tdefaultConfigurationIsVisible = 0;')
    w('\t\t\tdefaultConfigurationName = Release;')
    w('\t\t};')
cfg_list(proj_cfg_list, 'PBXProject "Donezy"', proj_debug, proj_release)
cfg_list(app_cfg_list, 'PBXNativeTarget "Donezy"', app_debug, app_release)
cfg_list(widget_cfg_list, 'PBXNativeTarget "DonezyWidget"', widget_debug, widget_release)
w("/* End XCConfigurationList section */")

w("\t};")
w("\trootObject = %s /* Project object */;" % proj_id)
w("}")

out_dir = os.path.join(ROOT, "Donezy.xcodeproj")
os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, "project.pbxproj"), "w") as f:
    f.write("\n".join(lines) + "\n")
print("Wrote", os.path.join(out_dir, "project.pbxproj"))
