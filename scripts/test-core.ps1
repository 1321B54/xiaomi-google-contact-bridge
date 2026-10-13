param([Parameter(Mandatory=$true)][string]$JdkRoot)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskClasses=Join-Path $taskRoot 'work\core-tests'
New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
$taskSources=@((Join-Path $taskRoot 'app\src\main\java\local\contactbridge\Model.java'),(Join-Path $taskRoot 'app\src\main\java\local\contactbridge\Planner.java'))
$taskTests=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'tests') -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $JdkRoot 'bin\javac.exe') -encoding UTF-8 -d $taskClasses @taskSources @taskTests
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
foreach($taskTest in $taskTests){$taskClass=[System.IO.Path]::GetFileNameWithoutExtension($taskTest);& (Join-Path $JdkRoot 'bin\java.exe') -cp $taskClasses $taskClass;if($LASTEXITCODE -ne 0){throw ('Test failed: '+$taskClass)}}
