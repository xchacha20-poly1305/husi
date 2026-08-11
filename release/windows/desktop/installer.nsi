; Husi Windows Installer — NSIS script
; Placeholders are replaced by package.sh before compilation.

Unicode true

!include "MUI2.nsh"
!include "FileFunc.nsh"
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
RequestExecutionLevel admin

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
Var CheckboxDesktopShortcut
Var CheckboxStartMenuShortcut

; --- Pages ---
!insertmacro MUI_PAGE_LICENSE "__HUSI_LICENSE_FILE__"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
Page custom shortcutsPageCreate shortcutsPageLeave
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "SimpChinese"

LangString ShortcutPageTitle ${LANG_ENGLISH} "Shortcuts"
LangString ShortcutPageTitle ${LANG_SIMPCHINESE} "快捷方式"
LangString ShortcutAppName ${LANG_ENGLISH} "${APP_NAME}"
LangString ShortcutAppName ${LANG_SIMPCHINESE} "${APP_NAME_ZH_CN}"
LangString ShortcutPageSubtitle ${LANG_ENGLISH} "Choose which shortcuts to create."
LangString ShortcutPageSubtitle ${LANG_SIMPCHINESE} "选择要创建的快捷方式。"
LangString ShortcutPageDescription ${LANG_ENGLISH} "Select the shortcuts to create for ${APP_NAME}."
LangString ShortcutPageDescription ${LANG_SIMPCHINESE} "选择要为 ${APP_NAME} 创建的快捷方式。"
LangString DesktopShortcutLabel ${LANG_ENGLISH} "Create a desktop shortcut"
LangString DesktopShortcutLabel ${LANG_SIMPCHINESE} "创建桌面快捷方式"
LangString StartMenuShortcutLabel ${LANG_ENGLISH} "Create a Start Menu shortcut"
LangString StartMenuShortcutLabel ${LANG_SIMPCHINESE} "创建开始菜单快捷方式"
LangString InstallSectionName ${LANG_ENGLISH} "Install"
LangString InstallSectionName ${LANG_SIMPCHINESE} "安装"
LangString UninstallSectionName ${LANG_ENGLISH} "Uninstall"
LangString UninstallSectionName ${LANG_SIMPCHINESE} "卸载"
LangString UninstallShortcutName ${LANG_ENGLISH} "Uninstall"
LangString UninstallShortcutName ${LANG_SIMPCHINESE} "卸载"

Function .onInit
    StrCpy $CreateDesktopShortcut ${BST_CHECKED}
    StrCpy $CreateStartMenuShortcut ${BST_CHECKED}
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

    ; Install the daemon service
    nsExec::ExecToLog '"$INSTDIR\husi-core.exe" service install'

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

Function shortcutsPageCreate
    !insertmacro MUI_HEADER_TEXT "$(ShortcutPageTitle)" "$(ShortcutPageSubtitle)"

    nsDialogs::Create 1018
    Pop $0
    StrCmp $0 error 0 +2
    Abort

    ${NSD_CreateLabel} 0 0 100% 24u "$(ShortcutPageDescription)"
    Pop $0

    ${NSD_CreateCheckbox} 0 32u 100% 12u "$(DesktopShortcutLabel)"
    Pop $CheckboxDesktopShortcut
    ${NSD_SetState} $CheckboxDesktopShortcut $CreateDesktopShortcut

    ${NSD_CreateCheckbox} 0 50u 100% 12u "$(StartMenuShortcutLabel)"
    Pop $CheckboxStartMenuShortcut
    ${NSD_SetState} $CheckboxStartMenuShortcut $CreateStartMenuShortcut

    nsDialogs::Show
FunctionEnd

Function shortcutsPageLeave
    ${NSD_GetState} $CheckboxDesktopShortcut $CreateDesktopShortcut
    ${NSD_GetState} $CheckboxStartMenuShortcut $CreateStartMenuShortcut
    Call createShortcuts
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
    IfSilent silent done
silent:
    Call createShortcuts
done:
FunctionEnd

; --- Uninstall section ---
Section "un.$(UninstallSectionName)"
    ; Uninstall the daemon service
    nsExec::ExecToLog '"$INSTDIR\husi-core.exe" service uninstall'

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
