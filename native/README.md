# Building the Native Launchers

The launchers can be built from the command line, with the following prerequisites:
 * XCode (Mac OS X)
 * Visual Studio 2010 with 64-bit compilers (either use Professional Edition or install Windows SDK 7.1) and .NET Framework v4 (Windows).

### Mac Launcher
```
tools/idea/native/MacLauncher$ xcodebuild
```

Optionally, you can verify the resulting binary:

```
tools/idea/native/MacLauncher$ file build/Release/Launcher.app/Contents/MacOS/Launcher
build/Release/Launcher.app/Contents/MacOS/Launcher: Mach-O universal binary with 2 architectures
build/Release/Launcher.app/Contents/MacOS/Launcher (for architecture x86_64):	Mach-O 64-bit executable x86_64
build/Release/Launcher.app/Contents/MacOS/Launcher (for architecture i386):	Mach-O executable i386
```

### Windows Launchers
Open the Visual Studio Command Prompt; the 32-bit toolchain will be selected by default:

```
Setting environment for using Microsoft Visual Studio 2010 x86 tools
tools\idea\native\WinLauncher\WinLauncher> msbuild /p:JdkPath="C:\Program Files\Java\jdk1.8.0_45" /p:Configuration=Release
```

Switch to the 64-bit compiler toolchain (`vcvarsall.bat amd64`), or open the Windows SDK 7.1 Command Prompt which selects the 64-bit
toolchain by default:

```
tools\idea\native\WinLauncher\WinLauncher> msbuild /p:JdkPath="C:\Program Files\Java\jdk1.8.0_45" /p:Configuration=Release /p:Platform=x64
```

The resulting binaries WinLauncher.exe and WinLauncher64.exe will be available under tools\idea\bin\WinLauncher.