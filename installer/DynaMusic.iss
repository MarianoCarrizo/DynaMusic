
#ifndef MyAppVersion
  #error Pass the version: /DMyAppVersion=x.y.z
#endif
#ifndef AppImageDir
  #error Pass the jpackage app-image directory: /DAppImageDir=...
#endif
#ifndef FfmpegDir
  #error Pass the ffmpeg bundle directory: /DFfmpegDir=...
#endif
#ifndef OutputDir
  #define OutputDir "Output"
#endif

#define MyAppName "DynaMusic"
#define MyAppExe "DynaMusic.exe"

[Setup]
AppId={{79F75685-7F3D-42C7-AE06-D18F84A1AC47}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher=marianocarrizo
AppPublisherURL=https://github.com/MarianoCarrizo/DynaMusic
AppSupportURL=https://github.com/MarianoCarrizo/DynaMusic/issues
VersionInfoVersion={#MyAppVersion}

DefaultDirName={autopf}\{#MyAppName}
UsePreviousAppDir=no
DisableProgramGroupPage=yes
PrivilegesRequired=lowest

ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

CloseApplications=yes
RestartApplications=no

SetupIconFile=..\icon.ico
UninstallDisplayIcon={app}\{#MyAppExe}
OutputDir={#OutputDir}
OutputBaseFilename=DynaMusic-Setup-{#MyAppVersion}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
Type: filesandordirs; Name: "{app}\ffmpeg"
Type: filesandordirs; Name: "{autopf}\DynamicMusic"
Type: files; Name: "{autoprograms}\DynamicMusic.lnk"
Type: files; Name: "{autodesktop}\DynamicMusic.lnk"

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#FfmpegDir}\*"; DestDir: "{app}\ffmpeg"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExe}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent
Filename: "{app}\{#MyAppExe}"; Flags: nowait runasoriginaluser; Check: WizardSilent
