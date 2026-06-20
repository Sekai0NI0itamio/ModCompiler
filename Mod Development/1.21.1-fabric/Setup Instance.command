#!/bin/bash
# Setup script to create the 1.21.1 Fabric instance in Prism Launcher
# Run this once to set up the instance

PRISM_INSTANCES="/Users/stevennovak/Library/Application Support/PrismLauncher/instances"
INSTANCE_DIR="$PRISM_INSTANCES/1.21.1 Fabric"

if [ -d "$INSTANCE_DIR" ]; then
    echo "Instance '1.21.1 Fabric' already exists at:"
    echo "  $INSTANCE_DIR"
    echo ""
    echo "Skipping creation. If you want to recreate it, delete the instance first."
    exit 0
fi

echo "Creating 1.21.1 Fabric instance in Prism Launcher..."
echo ""

mkdir -p "$INSTANCE_DIR/minecraft/mods"

cat > "$INSTANCE_DIR/instance.cfg" << 'EOF'
[General]
AutoCloseConsole=false
AutomaticJava=true
CloseAfterLaunch=false
ConfigVersion=1.3
CustomGLFWPath=
CustomOpenALPath=
EnableFeralGamemode=false
EnableMangoHud=false
Env={}
ExportAuthor=
ExportName=
ExportOptionalFiles=true
ExportSummary=
ExportVersion=1.0.0
GlobalDataPacksEnabled=false
GlobalDataPacksPath=
IgnoreJavaCompatibility=false
InstanceAccountId=
InstanceType=OneSix
JavaArchitecture=64
JavaPath=
JavaRealArchitecture=
JavaSignature=
JavaVendor=
JavaVersion=
JoinServerOnLaunch=false
JoinServerOnLaunchAddress=
JoinWorldOnLaunch=
JvmArgs=
LaunchMaximized=false
LogPrePostOutput=true
ManagedPack=false
ManagedPackID=
ManagedPackName=
ManagedPackType=
ManagedPackURL=
ManagedPackVersionID=
ManagedPackVersionName=
MaxMemAlloc=4096
MinMemAlloc=512
MinecraftWinHeight=480
MinecraftWinWidth=854
ModDownloadLoaders=[]
OnlineFixes=false
OverrideCommands=false
OverrideConsole=false
OverrideEnv=false
OverrideGameTime=false
OverrideJavaArgs=false
OverrideJavaLocation=false
OverrideLegacySettings=false
OverrideMemory=false
OverrideMiscellaneous=false
OverrideModDownloadLoaders=false
OverrideNativeWorkarounds=false
OverridePerformance=false
OverrideWindow=false
PermGen=128
PostExitCommand=
PreLaunchCommand=
Profiler=
QuitAfterGameStop=false
RecordGameTime=true
ShowConsole=false
ShowConsoleOnError=true
ShowGameTime=true
UseAccountForInstance=false
UseDiscreteGpu=false
UseNativeGLFW=false
UseNativeOpenAL=false
UseZink=false
WrapperCommand=
iconKey=default
lastLaunchTime=0
lastTimePlayed=0
linkedInstances=[]
name=1.21.1 Fabric
notes=Mod development instance for 1.21.1 Fabric mods
shortcuts=
totalTimePlayed=0

[UI]
mods_Page\Columns=
mods_Page\ColumnsOverride=false
resourcepacks_Page\Columns=
resourcepacks_Page\ColumnsOverride=false
shaderpacks_Page\Columns=
shaderpacks_Page\ColumnsOverride=false
texturepacks_Page\Columns=
texturepacks_Page\ColumnsOverride=false
EOF

cat > "$INSTANCE_DIR/mmc-pack.json" << 'EOF'
{
    "components": [
        {
            "cachedName": "LWJGL 3",
            "cachedVersion": "3.3.3",
            "cachedVolatile": true,
            "dependencyOnly": true,
            "uid": "org.lwjgl3",
            "version": "3.3.3"
        },
        {
            "cachedName": "Minecraft",
            "cachedRequires": [
                {
                    "suggests": "3.3.3",
                    "uid": "org.lwjgl3"
                }
            ],
            "cachedVersion": "1.21.1",
            "important": true,
            "uid": "net.minecraft",
            "version": "1.21.1"
        },
        {
            "cachedName": "Fabric Loader",
            "cachedRequires": [
                {
                    "equals": "1.21.1",
                    "uid": "net.minecraft"
                }
            ],
            "cachedVersion": "0.18.2",
            "uid": "net.fabricmc.fabric-loader",
            "version": "0.18.2"
        }
    ],
    "formatVersion": 1
}
EOF

echo "Instance created successfully!"
echo ""
echo "Location: $INSTANCE_DIR"
echo ""
echo "IMPORTANT: When you first launch this instance in Prism Launcher:"
echo "  1. It will download Minecraft 1.21.1 and Fabric Loader"
echo "  2. Make sure you have Java 21 installed (required for 1.21.1)"
echo "  3. You may need to set the Java path in instance settings"
echo ""
echo "Opening Prism Launcher..."
open -a "Prism Launcher"
