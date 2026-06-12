; Kosmix — Inno Setup script.
;
; Expects the jpackage app-image in {#WORK_DIR}\data (produced by the
; Gradle task `prepareInnoSetup`, which copies build/jpackage/Kosmix
; there — Kosmix.exe at the data root, runtime + app beside it).

#define MyAppName "Kosmix"
#define MyAppVersion "v1.0.0"
#define MyAppExeName "Kosmix.exe"
#define WORK_DIR = "F:\DEV\skiafx\kosmix\innosetup"



[Setup]
; NOTE: The value of AppId uniquely identifies this application. Do not use the same AppId value in installers for other applications.
; (To generate a new GUID, click Tools | Generate GUID inside the IDE.)
AppId={{7E4B2A91-3C58-4F6D-9A12-D80E5B6C44F2}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
LicenseFile={#WORK_DIR}\License.txt
AppPublisher=Kosmix
; Run in non administrative install mode (install for current user only).
PrivilegesRequired=lowest
OutputDir={#WORK_DIR}\Release
OutputBaseFilename=Kosmix Installer {#MyAppVersion}
SetupIconFile={#WORK_DIR}\icon.ico
Compression=lzma
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#WORK_DIR}\data\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; NOTE: Don't use "Flags: ignoreversion" on any shared system files

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
