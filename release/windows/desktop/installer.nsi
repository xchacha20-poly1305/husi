; Husi Windows Installer — NSIS script
; Placeholders are replaced by package.sh before compilation.

Unicode true

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "LogicLib.nsh"
!include "nsDialogs.nsh"
!include "WinMessages.nsh"

; --- Metadata ---
!define PACKAGE_NAME    "__HUSI_PACKAGE_NAME__"
!define APP_NAME        "__HUSI_APP_NAME__"
!define APP_NAME_ZH_CN  "__HUSI_APP_NAME_ZH_CN__"
!define APP_VERSION     "__HUSI_APP_VERSION__"
!define APP_DESCRIPTION "__HUSI_APP_DESCRIPTION__"
!define APP_URL         "__HUSI_APP_URL__"
!define MAINTAINER      "__HUSI_MAINTAINER__"

Name "${APP_NAME} ${APP_VERSION}"
OutFile "__HUSI_OUTPUT_FILE__"
InstallDir "$LOCALAPPDATA\Programs\${APP_NAME}"
InstallDirRegKey HKCU "Software\${PACKAGE_NAME}\Installer" "InstallDir"
; Everything this installer writes lives under the user profile. Only the
; optional system service needs administrator rights, and it asks for them
; on its own, so restricted accounts can still install the app.
RequestExecutionLevel user

; --- Version info embedded in exe ---
VIProductVersion "__HUSI_VI_VERSION__"
VIAddVersionKey "ProductName" "${APP_NAME}"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "FileVersion" "__HUSI_VI_VERSION__"
VIAddVersionKey "CompanyName" "${MAINTAINER}"
VIAddVersionKey "FileDescription" "${APP_DESCRIPTION}"
VIAddVersionKey "LegalCopyright" "${MAINTAINER}"

; --- MUI settings ---
!define MUI_ABORTWARNING

Var CreateDesktopShortcut
Var CreateStartMenuShortcut
Var InstallCoreService
Var CheckboxDesktopShortcut
Var CheckboxStartMenuShortcut
Var CheckboxInstallCoreService

; --- Pages ---
!insertmacro MUI_PAGE_LICENSE "__HUSI_LICENSE_FILE__"
!insertmacro MUI_PAGE_DIRECTORY
Page custom optionsPageCreate optionsPageLeave
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "SimpChinese"

LangString OptionsPageTitle ${LANG_ENGLISH} "Options"
LangString OptionsPageTitle ${LANG_SIMPCHINESE} "选项"
LangString ShortcutAppName ${LANG_ENGLISH} "${APP_NAME}"
LangString ShortcutAppName ${LANG_SIMPCHINESE} "${APP_NAME_ZH_CN}"
LangString OptionsPageSubtitle ${LANG_ENGLISH} "Choose how ${APP_NAME} is set up."
LangString OptionsPageSubtitle ${LANG_SIMPCHINESE} "选择 ${APP_NAME} 的安装方式。"
LangString OptionsPageDescription ${LANG_ENGLISH} "${APP_NAME} installs into your own user profile. Only the system service needs administrator rights."
LangString OptionsPageDescription ${LANG_SIMPCHINESE} "${APP_NAME} 安装在当前用户的个人目录下。只有系统服务需要管理员权限。"
LangString DesktopShortcutLabel ${LANG_ENGLISH} "Create a desktop shortcut"
LangString DesktopShortcutLabel ${LANG_SIMPCHINESE} "创建桌面快捷方式"
LangString StartMenuShortcutLabel ${LANG_ENGLISH} "Create a Start Menu shortcut"
LangString StartMenuShortcutLabel ${LANG_SIMPCHINESE} "创建开始菜单快捷方式"
LangString CoreServiceLabel ${LANG_ENGLISH} "Install the system service (needs administrator; required for TUN mode)"
LangString CoreServiceLabel ${LANG_SIMPCHINESE} "安装系统服务（需要管理员权限；TUN 模式必需）"
LangString CoreServiceHint ${LANG_ENGLISH} "Without the service ${APP_NAME} still runs as a local proxy, and the service can be installed later from Settings."
LangString CoreServiceHint ${LANG_SIMPCHINESE} "不安装系统服务时，${APP_NAME} 仍可作为本地代理运行，之后也可以在设置中安装该服务。"
LangString CoreServiceInstalling ${LANG_ENGLISH} "Installing the system service…"
LangString CoreServiceInstalling ${LANG_SIMPCHINESE} "正在安装系统服务……"
LangString CoreServiceSkipped ${LANG_ENGLISH} "System service not installed. You can install it later from Settings."
LangString CoreServiceSkipped ${LANG_SIMPCHINESE} "未安装系统服务。之后可以在设置中安装。"
LangString CoreServiceUninstalling ${LANG_ENGLISH} "Removing the system service…"
LangString CoreServiceUninstalling ${LANG_SIMPCHINESE} "正在移除系统服务……"
LangString CoreServiceUninstallSkipped ${LANG_ENGLISH} "The system service was left installed because administrator rights were declined."
LangString CoreServiceUninstallSkipped ${LANG_SIMPCHINESE} "由于未获得管理员权限，系统服务仍保留在系统中。"
LangString InstallSectionName ${LANG_ENGLISH} "Install"
LangString InstallSectionName ${LANG_SIMPCHINESE} "安装"
LangString UninstallSectionName ${LANG_ENGLISH} "Uninstall"
LangString UninstallSectionName ${LANG_SIMPCHINESE} "卸载"
LangString UninstallShortcutName ${LANG_ENGLISH} "Uninstall"
LangString UninstallShortcutName ${LANG_SIMPCHINESE} "卸载"

Function .onInit
    StrCpy $CreateDesktopShortcut ${BST_CHECKED}
    StrCpy $CreateStartMenuShortcut ${BST_CHECKED}
    StrCpy $InstallCoreService ${BST_CHECKED}
FunctionEnd

; --- Install section ---
Section "$(InstallSectionName)"
    SetOutPath "$INSTDIR"
    File "/oname=${APP_NAME}.exe" "__HUSI_LAUNCHER_FILE__"
    File "/oname=husi-core.exe" "__HUSI_CORE_FILE__"
    File "/oname=husicore.dll" "__HUSI_CORE_LIB_FILE__"
    File "/oname=LICENSE" "__HUSI_LICENSE_FILE__"
    File "/oname=desktop-java-opts.conf.template" "__HUSI_JAVA_OPTS_FILE__"
    File "/oname=desktop-app-args.conf.template" "__HUSI_APP_ARGS_FILE__"

    SetOutPath "$INSTDIR\app"
    File "/oname=${PACKAGE_NAME}.jar" "__HUSI_JAR_FILE__"

    SetOutPath "$INSTDIR"

    Call installCoreService

    ; Uninstaller
    WriteUninstaller "$INSTDIR\uninstall.exe"

    ; Install dir registry (for upgrade detection)
    WriteRegStr HKCU "Software\${PACKAGE_NAME}\Installer" "InstallDir" "$INSTDIR"

    ; Add/Remove Programs entry
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "DisplayName" "${APP_NAME}"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "DisplayVersion" "${APP_VERSION}"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "Publisher" "${MAINTAINER}"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "URLInfoAbout" "${APP_URL}"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "QuietUninstallString" '"$INSTDIR\uninstall.exe" /S'
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "InstallLocation" "$INSTDIR"
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "NoModify" 1
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "NoRepair" 1

    ; Estimated size
    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    IntFmt $0 "0x%08X" $0
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}" \
        "EstimatedSize" $0

    ; URL scheme registration
__HUSI_URL_SCHEME_REGISTRY__
SectionEnd

Function optionsPageCreate
    !insertmacro MUI_HEADER_TEXT "$(OptionsPageTitle)" "$(OptionsPageSubtitle)"

    nsDialogs::Create 1018
    Pop $0
    StrCmp $0 error 0 +2
    Abort

    ${NSD_CreateLabel} 0 0 100% 24u "$(OptionsPageDescription)"
    Pop $0

    ${NSD_CreateCheckbox} 0 32u 100% 12u "$(DesktopShortcutLabel)"
    Pop $CheckboxDesktopShortcut
    ${NSD_SetState} $CheckboxDesktopShortcut $CreateDesktopShortcut

    ${NSD_CreateCheckbox} 0 50u 100% 12u "$(StartMenuShortcutLabel)"
    Pop $CheckboxStartMenuShortcut
    ${NSD_SetState} $CheckboxStartMenuShortcut $CreateStartMenuShortcut

    ${NSD_CreateCheckbox} 0 68u 100% 12u "$(CoreServiceLabel)"
    Pop $CheckboxInstallCoreService
    ${NSD_SetState} $CheckboxInstallCoreService $InstallCoreService

    ${NSD_CreateLabel} 0 86u 100% 24u "$(CoreServiceHint)"
    Pop $0

    nsDialogs::Show
FunctionEnd

Function optionsPageLeave
    ${NSD_GetState} $CheckboxDesktopShortcut $CreateDesktopShortcut
    ${NSD_GetState} $CheckboxStartMenuShortcut $CreateStartMenuShortcut
    ${NSD_GetState} $CheckboxInstallCoreService $InstallCoreService
FunctionEnd

; The service lives outside the user profile, so it is the one step that needs
; administrator rights. A declined UAC prompt only skips the service: the app
; itself is already installed, and Settings can install the service later.
Function installCoreService
    ${If} $InstallCoreService != ${BST_CHECKED}
        Return
    ${EndIf}

    DetailPrint "$(CoreServiceInstalling)"
    ${If} ${Silent}
        ; A silent install must never pop a UAC prompt: run in place and let it
        ; fail when the silent install was not started elevated.
        nsExec::ExecToLog '"$INSTDIR\husi-core.exe" service install'
        Pop $0
    ${Else}
        ClearErrors
        ExecShellWait "runas" "$INSTDIR\husi-core.exe" "service install" SW_HIDE
        ${If} ${Errors}
            StrCpy $0 "error"
        ${Else}
            StrCpy $0 0
        ${EndIf}
    ${EndIf}

    ${If} $0 != 0
        DetailPrint "$(CoreServiceSkipped)"
    ${EndIf}
FunctionEnd

Function createShortcuts
    Delete "$DESKTOP\${APP_NAME}.lnk"
    Delete "$DESKTOP\${APP_NAME_ZH_CN}.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME_ZH_CN}.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\Uninstall.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\卸载.lnk"
    RMDir "$SMPROGRAMS\${APP_NAME}"

    StrCmp $CreateDesktopShortcut ${BST_CHECKED} 0 +2
    CreateShortCut "$DESKTOP\$(ShortcutAppName).lnk" "$INSTDIR\${APP_NAME}.exe" "" "" "" "" "" "${APP_DESCRIPTION}"

    StrCmp $CreateStartMenuShortcut ${BST_CHECKED} 0 +4
    CreateDirectory "$SMPROGRAMS\${APP_NAME}"
    CreateShortCut "$SMPROGRAMS\${APP_NAME}\$(ShortcutAppName).lnk" "$INSTDIR\${APP_NAME}.exe" "" "" "" "" "" "${APP_DESCRIPTION}"
    CreateShortCut "$SMPROGRAMS\${APP_NAME}\$(UninstallShortcutName).lnk" "$INSTDIR\uninstall.exe"
FunctionEnd

Function .onInstSuccess
    Call createShortcuts
FunctionEnd

; --- Uninstall section ---
Section "un.$(UninstallSectionName)"
    Call un.uninstallCoreService

    ; Remove files
    Delete "$INSTDIR\${APP_NAME}.exe"
    Delete "$INSTDIR\husi-core.exe"
    Delete "$INSTDIR\husicore.dll"
    Delete "$INSTDIR\LICENSE"
    Delete "$INSTDIR\desktop-java-opts.conf.template"
    Delete "$INSTDIR\desktop-app-args.conf.template"
    Delete "$INSTDIR\app\${PACKAGE_NAME}.jar"
    RMDir "$INSTDIR\app"
    Delete "$INSTDIR\uninstall.exe"
    Delete "$DESKTOP\${APP_NAME}.lnk"
    Delete "$DESKTOP\${APP_NAME_ZH_CN}.lnk"
    RMDir "$INSTDIR"

    ; Start Menu
    Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME_ZH_CN}.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\Uninstall.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\$(UninstallShortcutName).lnk"
    RMDir "$SMPROGRAMS\${APP_NAME}"

    ; Registry cleanup
    DeleteRegKey HKCU "Software\${PACKAGE_NAME}\Installer"
    DeleteRegKey HKCU "Software\${PACKAGE_NAME}"
    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PACKAGE_NAME}"

    ; URL schemes
__HUSI_URL_SCHEME_UNREGISTRY__
SectionEnd

; Mirror of installCoreService. The copy under Program Files only tells us the
; service was installed at all — the uninstall is driven from $INSTDIR, because
; the installed binary cannot delete itself while it is the running process. A
; declined UAC prompt leaves the service behind rather than aborting.
Function un.uninstallCoreService
    ${IfNot} ${FileExists} "$PROGRAMFILES64\husi\husi-core.exe"
        Return
    ${EndIf}

    DetailPrint "$(CoreServiceUninstalling)"
    ${If} ${Silent}
        nsExec::ExecToLog '"$INSTDIR\husi-core.exe" service uninstall'
        Pop $0
    ${Else}
        ClearErrors
        ExecShellWait "runas" "$INSTDIR\husi-core.exe" "service uninstall" SW_HIDE
        ${If} ${Errors}
            StrCpy $0 "error"
        ${Else}
            StrCpy $0 0
        ${EndIf}
    ${EndIf}

    ${If} $0 != 0
        DetailPrint "$(CoreServiceUninstallSkipped)"
    ${EndIf}
FunctionEnd
