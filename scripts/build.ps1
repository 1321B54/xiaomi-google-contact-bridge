param(
    [Parameter(Mandatory=$true)][string]$SdkRoot,
    [Parameter(Mandatory=$true)][string]$JdkRoot,
    [ValidateSet('Debug','Release')][string]$BuildType='Debug',
    [string]$KeystorePath,
    [string]$KeyAlias,
    [string]$BuildRoot,
    [string]$BuildToolsVersion='37.0.0',
    [string]$Platform='android-37.0'
)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskRelease=$BuildType -eq 'Release'
# Release signing inputs are validated before any file or build output is created.
# The password values never enter a command line: apksigner reads them through the
# 'env:NAME' form, so only the environment variable names are ever interpolated.
if($taskRelease) {
    if([string]::IsNullOrWhiteSpace($KeystorePath)){throw 'Release build requires -KeystorePath pointing to an external release keystore'}
    if([string]::IsNullOrWhiteSpace($KeyAlias)){throw 'Release build requires -KeyAlias'}
    $taskKeystore=[System.IO.Path]::GetFullPath($KeystorePath)
    if(-not(Test-Path -LiteralPath $taskKeystore -PathType Leaf)){throw ('Release keystore not found: '+$taskKeystore)}
    if([string]::IsNullOrEmpty($env:CONTACT_BRIDGE_STORE_PASSWORD)){throw 'Release build requires the CONTACT_BRIDGE_STORE_PASSWORD environment variable'}
    $taskStorePassRef='env:CONTACT_BRIDGE_STORE_PASSWORD'
    $taskKeyPassRef=if([string]::IsNullOrEmpty($env:CONTACT_BRIDGE_KEY_PASSWORD)){'env:CONTACT_BRIDGE_STORE_PASSWORD'}else{'env:CONTACT_BRIDGE_KEY_PASSWORD'}
}
$taskBuild=if($BuildRoot){[System.IO.Path]::GetFullPath($BuildRoot)}else{Join-Path $taskRoot 'app\build\manual'}
$taskTools=Join-Path $SdkRoot ('build-tools\'+$BuildToolsVersion)
$taskAndroidJar=Join-Path $SdkRoot ('platforms\'+$Platform+'\android.jar')
$taskJavaBin=Join-Path $JdkRoot 'bin'
foreach($taskRequired in @($taskAndroidJar,(Join-Path $taskTools 'aapt2.exe'),(Join-Path $taskJavaBin 'javac.exe'))){if(-not(Test-Path -LiteralPath $taskRequired)){throw ('Missing build dependency: '+$taskRequired)}}
New-Item -ItemType Directory -Path $taskBuild -Force | Out-Null
$taskClasses=Join-Path $taskBuild ('classes-'+[Guid]::NewGuid().ToString('N'))
$taskDex=Join-Path $taskBuild ('dex-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskClasses,$taskDex -Force | Out-Null
function Invoke-BuildTool([string]$Exe,[string[]]$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw ('Build tool failed: '+$Exe+' (exit '+$LASTEXITCODE+')')}}
$taskOldJavaHome=$env:JAVA_HOME
$env:JAVA_HOME=$JdkRoot
try {
    [xml]$taskManifest=Get-Content -LiteralPath (Join-Path $taskRoot 'app\src\main\AndroidManifest.xml') -Raw
    $taskManifest.DocumentElement.SetAttribute('package','local.contactbridge')
    $taskApplication=$taskManifest.DocumentElement.SelectSingleNode('application')
    if($taskRelease) {
        # Packaged release manifest is explicitly non-debuggable and not a test-only build.
        $taskAndroidNamespace='http://schemas.android.com/apk/res/android'
        $taskApplication.SetAttribute('debuggable',$taskAndroidNamespace,'false')
        $taskApplication.SetAttribute('testOnly',$taskAndroidNamespace,'false')
    }
    $taskManifestPath=Join-Path $taskBuild 'AndroidManifest.xml'
    $taskManifest.Save($taskManifestPath)
    $taskResources=Join-Path $taskBuild 'resources.zip'
    $taskUnsigned=Join-Path $taskBuild 'unsigned.apk'
    $taskStagedRes=Join-Path $taskBuild ('res-'+[Guid]::NewGuid().ToString('N'))
    Copy-Item -LiteralPath (Join-Path $taskRoot 'app\src\main\res') -Destination $taskStagedRes -Recurse
    Invoke-BuildTool (Join-Path $taskTools 'aapt2.exe') @('compile','--dir',$taskStagedRes,'-o',$taskResources)
    $taskLinkArgs=@('link')
    if(-not $taskRelease){$taskLinkArgs+='--debug-mode'}
    # The compiled resource archive must be passed to aapt2 link, otherwise references such as @drawable/ic_bridge cannot be resolved.
    $taskLinkArgs+=@('-o',$taskUnsigned,'-I',$taskAndroidJar,'--manifest',$taskManifestPath,'--min-sdk-version','30','--target-sdk-version','37','--version-code','4','--version-name','0.1.3',$taskResources)
    Invoke-BuildTool (Join-Path $taskTools 'aapt2.exe') $taskLinkArgs
    $taskSources=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
    Invoke-BuildTool (Join-Path $taskJavaBin 'javac.exe') (@('--release','17','-encoding','UTF-8','-classpath',$taskAndroidJar,'-d',$taskClasses)+$taskSources)
    $taskClassJar=Join-Path $taskBuild 'classes.jar'
    Invoke-BuildTool (Join-Path $taskJavaBin 'jar.exe') @('cf',$taskClassJar,'-C',$taskClasses,'.')
    Invoke-BuildTool (Join-Path $taskTools 'd8.bat') @('--lib',$taskAndroidJar,'--min-api','30','--output',$taskDex,$taskClassJar)
    # APKs are ZIP archives. Add all DEX shards without changing the packaged resources.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskZip=[System.IO.Compression.ZipFile]::Open($taskUnsigned,[System.IO.Compression.ZipArchiveMode]::Update)
    try{foreach($taskDexFile in Get-ChildItem -LiteralPath $taskDex -Filter '*.dex'){[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip,$taskDexFile.FullName,$taskDexFile.Name)|Out-Null}}finally{$taskZip.Dispose()}
    $taskAligned=Join-Path $taskBuild 'aligned.apk'
    Invoke-BuildTool (Join-Path $taskTools 'zipalign.exe') @('-f','4',$taskUnsigned,$taskAligned)
    if($taskRelease) {
        $taskOutputDir=Join-Path $taskRoot 'outputs\apk\release'
        New-Item -ItemType Directory -Path $taskOutputDir -Force | Out-Null
        $taskOutput=Join-Path $taskOutputDir 'contact-bridge-0.1.3-release.apk'
        Invoke-BuildTool (Join-Path $taskTools 'apksigner.bat') @('sign','--ks',$taskKeystore,'--ks-key-alias',$KeyAlias,'--ks-pass',$taskStorePassRef,'--key-pass',$taskKeyPassRef,'--out',$taskOutput,$taskAligned)
    } else {
        # Debug stays local-only: a random throwaway RSA key with the conventional dev password.
        $taskSigning=Join-Path $taskRoot 'work\signing'
        New-Item -ItemType Directory -Path $taskSigning -Force | Out-Null
        $taskKeystore=Join-Path $taskSigning 'debug.keystore'
        if(-not(Test-Path -LiteralPath $taskKeystore)) {
            Invoke-BuildTool (Join-Path $taskJavaBin 'keytool.exe') @('-genkeypair','-keystore',$taskKeystore,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=ContactBridge Local Debug','-keyalg','RSA','-keysize','2048','-validity','10000')
        }
        $taskOutputDir=Join-Path $taskRoot 'app\build\outputs\apk\debug'
        New-Item -ItemType Directory -Path $taskOutputDir -Force | Out-Null
        $taskOutput=Join-Path $taskOutputDir 'contact-bridge-0.1.3-debug.apk'
        Invoke-BuildTool (Join-Path $taskTools 'apksigner.bat') @('sign','--ks',$taskKeystore,'--ks-pass','pass:android','--key-pass','pass:android','--out',$taskOutput,$taskAligned)
    }
    Invoke-BuildTool (Join-Path $taskTools 'apksigner.bat') @('verify','--verbose',$taskOutput)
    if($taskRelease) {
        $taskBadging=& (Join-Path $taskTools 'aapt2.exe') dump badging $taskOutput
        if($LASTEXITCODE -ne 0){throw 'aapt2 dump badging failed'}
        if(($taskBadging -join "`n") -match 'application-debuggable'){throw 'Release APK is marked debuggable'}
    }
    Write-Output ('APK: '+$taskOutput)
} finally {$env:JAVA_HOME=$taskOldJavaHome}
