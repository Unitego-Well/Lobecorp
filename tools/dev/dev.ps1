# External PowerShell entry; no IDE console or string evaluation.
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'dev.py'
$pythonCommand = Get-Command python -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -ne $pythonCommand)
{
    & $pythonCommand.Source $scriptPath @args
    exit $LASTEXITCODE
}
$pythonLauncher = Get-Command py -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -ne $pythonLauncher)
{
    & $pythonLauncher.Source -3 $scriptPath @args
    exit $LASTEXITCODE
}
[Console]::Error.WriteLine('Python 3.10+ is required. Put python or py on PATH.')
exit 2
