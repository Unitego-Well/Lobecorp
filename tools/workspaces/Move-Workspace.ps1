[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Destination,
    [string]$Repository = 'E:/xm/mc/mod/Unitego-Well/Lobecorp',
    [string]$ResumeRecord,
    [switch]$Apply
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$Repository = [IO.Path]::GetFullPath($Repository).TrimEnd('\', '/')
$Source = [IO.Path]::GetFullPath($Source).TrimEnd('\', '/')
$Destination = [IO.Path]::GetFullPath($Destination).TrimEnd('\', '/')
$container = Join-Path $Repository '.workspace'
if ([IO.Path]::GetDirectoryName($Destination) -ne $container -or
        [IO.Path]::GetFileName($Destination) -notmatch '^lobecorp_[a-z0-9_]+$')
{
    throw 'Destination must be a named worktree directly inside the foundation .workspace directory.'
}
if (-not (Test-Path -LiteralPath (Join-Path $Source '.git') -PathType Leaf))
{
    throw 'Source must be an existing linked Git worktree.'
}
if ((Get-Item -LiteralPath $Source -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)
{
    throw 'Source is already a link; refusing to migrate it again.'
}
if ((Test-Path -LiteralPath $Destination) -and -not $ResumeRecord)
{
    throw 'Destination already exists.'
}
if (Test-Path -LiteralPath $container)
{
    if ((Get-Item -LiteralPath $container -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)
    {
        throw 'The destination container must not be a link.'
    }
}
function Git
{
    param([string]$Root, [string[]]$Arguments)
    $ErrorActionPreference = 'Continue'
    $result = @(& git.exe -C $Root @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0)
    {
        throw ($result -join [Environment]::NewLine)
    }
    return ($result -join ([string][char]10))
}
$common = Git $Repository @('rev-parse', '--path-format=absolute', '--git-common-dir')
$sourceCommon = Git $Source @('rev-parse', '--path-format=absolute', '--git-common-dir')
if ([IO.Path]::GetFullPath($common) -ne [IO.Path]::GetFullPath($sourceCommon))
{
    throw 'Source belongs to another Git repository.'
}
$links = @(Get-ChildItem -LiteralPath $Source -Recurse -Force -Attributes ReparsePoint)
if ($links.Count)
{
    throw 'Nested links require explicit migration review.'
}
$plan = [ordered]@{
    Source = $Source
    Destination = $Destination
    Head = Git $Source @('rev-parse', 'HEAD')
    IndexTree = Git $Source @('write-tree')
    Status = Git $Source @('status', '--porcelain=v1', '--untracked-files=all')
}
if (-not $Apply)
{
    $plan | ConvertTo-Json -Depth 5
    return
}
$stamp = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$records = Join-Path $common ('workspace-relocations/' + $stamp)
if ($ResumeRecord)
{
    $records = [IO.Path]::GetFullPath($ResumeRecord)
    $recordRoot = [IO.Path]::GetFullPath((Join-Path $common 'workspace-relocations')).TrimEnd('\') + '\'
    if (-not $records.StartsWith($recordRoot, [StringComparison]::OrdinalIgnoreCase))
    {
        throw 'Resume record is outside this repository migration directory.'
    }
    $saved = [IO.File]::ReadAllText((Join-Path $records 'state.json')) | ConvertFrom-Json
    if ($saved.Phase -notin @('verify', 'relocate') -or $saved.Source -ne $Source -or $saved.Destination -ne $Destination -or
            $saved.Head -ne $plan.Head -or $saved.IndexTree -ne $plan.IndexTree -or $saved.Status -ne $plan.Status)
    {
        throw 'Resume record does not match the unchanged source worktree.'
    }
}
[IO.Directory]::CreateDirectory($records) | Out-Null
[IO.Directory]::CreateDirectory($container) | Out-Null
$backup = Join-Path ([IO.Path]::GetDirectoryName($Source)) ([IO.Path]::GetFileName($Destination) + '_backup_' + $stamp)
if ($ResumeRecord)
{
    $backup = $saved.Backup
}
if ([IO.Path]::GetDirectoryName($backup) -ne [IO.Path]::GetDirectoryName($Source) -or
        (Test-Path -LiteralPath $backup))
{
    throw 'Unexpected backup path.'
}
$plan.Backup = $backup
$plan.Phase = 'snapshot'
$utf8 = New-Object Text.UTF8Encoding($false)
function Save-State
{
    [IO.File]::WriteAllText((Join-Path $records 'state.json'), ($plan | ConvertTo-Json -Depth 6), $utf8)
}
Save-State
if ($ResumeRecord)
{
    $manifest = [IO.File]::ReadAllText((Join-Path $records 'files.json'), $utf8) | ConvertFrom-Json
    $plan.Files = $manifest.Count
}
else
{
    Write-Output ('Snapshot: ' + $Source)
    $manifest = @(Get-ChildItem -LiteralPath $Source -Recurse -Force -File | ForEach-Object {
        [pscustomobject]@{
            Path = $_.FullName.Substring($Source.Length + 1)
            Bytes = $_.Length
            Hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        }
    })
    [IO.File]::WriteAllText((Join-Path $records 'files.json'), ($manifest | ConvertTo-Json -Depth 4), $utf8)
    $plan.Files = $manifest.Count
    $plan.Phase = 'copy'
    Save-State
    Write-Output ('Copying ' + $manifest.Count + ' files')
    & robocopy $Source $Destination /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /MT:8 /XJ /NP /NFL /NDL (('/LOG:') + (Join-Path $records 'copy.log')) | Out-Null
    if ($LASTEXITCODE -ge 8)
    {
        throw 'Copy failed; source retained, inspect the copy log and state record.'
    }
}
$plan.Phase = 'verify'
Save-State
Write-Output 'Verifying copied bytes'
$copied = @(Get-ChildItem -LiteralPath $Destination -Recurse -Force -File)
if ($copied.Count -ne $manifest.Count)
{
    throw 'Copied file count differs; source retained.'
}
foreach ($file in $manifest)
{
    $target = Join-Path $Destination $file.Path
    if (-not (Test-Path -LiteralPath $target -PathType Leaf) -or
            (Get-Item -LiteralPath $target -Force).Length -ne $file.Bytes -or
            (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.Hash -or
            (Get-FileHash -LiteralPath (Join-Path $Source $file.Path) -Algorithm SHA256).Hash -ne $file.Hash)
    {
        throw ('Source changed or copied bytes differ: ' + $file.Path)
    }
}
if ((Git $Source @('rev-parse', 'HEAD')) -ne $plan.Head -or
        (Git $Source @('write-tree')) -ne $plan.IndexTree -or
        (Git $Source @('status', '--porcelain=v1', '--untracked-files=all')) -ne $plan.Status)
{
    throw 'Git state changed during copy; source retained.'
}
$plan.Phase = 'relocate'
Save-State
Rename-Item -LiteralPath $Source -NewName ([IO.Path]::GetFileName($backup))
New-Item -ItemType Junction -Path $Source -Target $Destination | Out-Null
Git $Repository @('worktree', 'repair', $Destination) | Out-Null
Git $Repository @('worktree', 'lock', '--reason', 'Relocated worktree with legacy chat junction; do not auto-clean', $Destination) | Out-Null
if ((Git $Destination @('rev-parse', 'HEAD')) -ne $plan.Head -or
        (Git $Destination @('write-tree')) -ne $plan.IndexTree -or
        (Git $Destination @('status', '--porcelain=v1', '--untracked-files=all')) -ne $plan.Status)
{
    throw 'Relocated Git state differs; full original directory remains at Backup.'
}
$plan.Phase = 'complete'
Save-State
[pscustomobject]@{ Source = $Source; Destination = $Destination; Backup = $backup; Records = $records; Files = $manifest.Count; Head = $plan.Head } | ConvertTo-Json -Compress
