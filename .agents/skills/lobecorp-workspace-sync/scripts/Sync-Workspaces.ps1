[CmdletBinding()]
param(
    [string]$SourceRoot,
    [string]$SourceRef = 'dev',
    [string[]]$TargetRoots = @(),
    [switch]$Apply,
    [switch]$Resume,
    [switch]$InstallHook,
    [switch]$FromHook
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ConfigPath = '.codex/config.toml'
$RuntimePattern = '^(\.idea/|\.run/|\.gradle/|run/|build/|.*?/node_modules/)'
$Utf8 = [Text.UTF8Encoding]::new($false)
$GitExecutable = (Get-Command git -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
[Console]::OutputEncoding = $Utf8
foreach ($name in @('GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR', 'GIT_PREFIX',
'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES', 'GIT_IMPLICIT_WORK_TREE', 'GIT_SHALLOW_FILE', 'GIT_GRAFT_FILE'))
{
    if ($FromHook)
    {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    elseif ([Environment]::GetEnvironmentVariable($name, 'Process'))
    {
        throw "Explicit Git environment must be cleared before synchronization: $name"
    }
}
$Lf = [string][char]10
$CrLf = [string][char]13 + [char]10
$env:GIT_TERMINAL_PROMPT = '0'
$env:GIT_MERGE_AUTOEDIT = 'no'

$script:ProcessLog = $null
function Quote-GitArgument
{
    param([string]$Value)
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}
function Git
{
    param([string]$Root, [string[]]$Arguments, [switch]$AllowFailure)
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $GitExecutable
    $start.Arguments = ((@('-c', 'core.quotePath=false', '-C', $Root) + $Arguments) | ForEach-Object { Quote-GitArgument $_ }) -join ' '
    $start.WorkingDirectory = $Root
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = $Utf8
    $start.StandardErrorEncoding = $Utf8
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try
    {
        $process.Start() | Out-Null
        $identity = @{ Pid = $process.Id; Started = $process.StartTime.ToString('o'); Executable = $GitExecutable; Workspace = $Root }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $code = $process.ExitCode
        $text = $stdout.Result.TrimEnd([char]13, [char]10)
        $errorText = $stderr.Result.TrimEnd([char]13, [char]10)
        if ($script:ProcessLog)
        {
            $identity.Completed = (Get-Date).ToString('o')
            $identity.ExitCode = $code
            [IO.File]::AppendAllText($script:ProcessLog, ($identity | ConvertTo-Json -Compress) + $Lf, $Utf8)
        }
        if ($code -ne 0 -and -not $AllowFailure)
        {
            throw ($errorText + $Lf + $text)
        }
        $lines = @()
        if ($text)
        {
            $lines = @($text -split '\r?\n')
        }
        return [pscustomobject]@{ Code = $code; Lines = $lines; Text = $text; Errors = $errorText }
    }
    finally
    {
        $process.Dispose()
    }
}
function Save-Json
{
    param([string]$Path, $Value)
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path)) | Out-Null
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 12), $Utf8)
}
function Hash-Text
{
    param([string]$Value)
    $sha = [Security.Cryptography.SHA256]::Create()
    try
    {
        return ([BitConverter]::ToString($sha.ComputeHash($Utf8.GetBytes($Value)))).Replace('-', '').ToLowerInvariant()
    }
    finally
    {
        $sha.Dispose()
    }
}
function Worktree-Stamp
{
    param([string]$Root)
    $parts = [Collections.Generic.List[string]]::new()
    $parts.Add((Git $Root @('rev-parse', 'HEAD')).Text)
    $parts.Add((Git $Root @('write-tree')).Text)
    $parts.Add((Git $Root @('status', '--porcelain=v1', '--untracked-files=all')).Text)
    foreach ($path in (Git $Root @('ls-files', '--modified', '--others', '--exclude-standard')).Lines | Sort-Object -Unique)
    {
        $full = Join-Path $Root $path
        if (Test-Path -LiteralPath $full -PathType Leaf)
        {
            $parts.Add($path + ':' + (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash)
        }
    }
    return Hash-Text ($parts -join $Lf)
}
function Mask-ConfigTree
{
    param([string]$Root, [string]$Tree, [string]$ConfigRef, [string]$Directory)
    $index = Join-Path $Directory ('index-' + [guid]::NewGuid().ToString('N'))
    $oldIndex = $env:GIT_INDEX_FILE
    try
    {
        $env:GIT_INDEX_FILE = $index
        Git $Root @('read-tree', $Tree) | Out-Null
        if ((Git $Root @('ls-tree', $ConfigRef, '--', $ConfigPath)).Text)
        {
            Git $Root @('restore', "--source=$ConfigRef", '--staged', '--', $ConfigPath) | Out-Null
        }
        else
        {
            Git $Root @('update-index', '--force-remove', '--', $ConfigPath) | Out-Null
        }
        return (Git $Root @('write-tree')).Text
    }
    finally
    {
        $env:GIT_INDEX_FILE = $oldIndex
        if (Test-Path -LiteralPath $index)
        {
            [IO.File]::Delete($index)
        }
    }
}
function Save-WorkingFiles
{
    param([string]$Root, [string]$Directory)
    $files = [Collections.Generic.List[object]]::new()
    foreach ($path in (Git $Root @('diff', 'HEAD', '--name-only', '--diff-filter=ACMRT')).Lines)
    {
        if ($path -eq $ConfigPath)
        {
            continue
        }
        $full = Join-Path $Root $path
        if (-not (Test-Path -LiteralPath $full -PathType Leaf))
        {
            continue
        }
        if ((Get-Item -LiteralPath $full).Attributes -band [IO.FileAttributes]::ReparsePoint)
        {
            throw 'Changed link requires manual review.'
        }
        $backup = Join-Path $Directory ('working-' + $files.Count + '.bin')
        [IO.File]::WriteAllBytes($backup,[IO.File]::ReadAllBytes($full))
        $files.Add([pscustomobject]@{ Path = $path; Backup = $backup })
    }
    Save-Json (Join-Path $Directory 'working-files.json') @($files.ToArray())
    return $files.ToArray()
}
function Restore-WorkingFormat
{
    param([string]$Root, [object[]]$Files)
    $decoder = [Text.UTF8Encoding]::new($false, $true)
    foreach ($file in $Files)
    {
        $full = Join-Path $Root $file.Path
        if (-not (Test-Path -LiteralPath $full -PathType Leaf))
        {
            continue
        }
        $before = (Git $Root @('hash-object', "--path=$( $file.Path )", '--', $file.Backup)).Text
        $after = (Git $Root @('hash-object', "--path=$( $file.Path )", '--', $full)).Text
        if ($before -eq $after)
        {
            [IO.File]::WriteAllBytes($full,[IO.File]::ReadAllBytes($file.Backup))
            continue
        }
        try
        {
            $old = $decoder.GetString([IO.File]::ReadAllBytes($file.Backup))
            $current = $decoder.GetString([IO.File]::ReadAllBytes($full))
        }
        catch
        {
            continue
        }
        if ($old.Contains([string][char]0) -or $current.Contains([string][char]0))
        {
            continue
        }
        $hasCrLf = $old.Contains($CrLf)
        $hasBareLf = [regex]::IsMatch($old, '(?<!\r)\n')
        if ($hasCrLf -and -not $hasBareLf)
        {
            [IO.File]::WriteAllBytes($full,$Utf8.GetBytes($current.Replace($CrLf, $Lf).Replace($Lf, $CrLf)))
        }
        elseif ($hasBareLf -and -not $hasCrLf)
        {
            [IO.File]::WriteAllBytes($full,$Utf8.GetBytes($current.Replace($CrLf, $Lf)))
        }
        elseif ($hasCrLf -and $hasBareLf)
        {
            throw "Merged mixed-EOL file requires format review: $( $file.Path )"
        }
    }
}

function Normalize-Config
{
    param([string]$Text)
    $text = $Text.Replace($CrLf, $Lf)
    $roots = @([regex]::Matches($text, '"([^"]+)/modules/minecraft-mcp/packages/minecraft-mod-mcp') |
            ForEach-Object { $_.Groups[1].Value }) + @($SourceRoot.Replace('\', '/'))
    foreach ($root in $roots | Sort-Object -Unique)
    {
        if ($root)
        {
            $text = $text.Replace($root, '__WORKSPACE_ROOT__')
        }
    }
    return [regex]::Replace($text, '(?m)^(MC_MCP_PORT\s*=\s*)"\d+"', '$1"__MCP_PORT__"')
}
function Merge-LocalConfig
{
    param([string]$Root, [string]$Base, [string]$Source, [string]$Directory)
    $path = Join-Path $Root $ConfigPath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        throw 'Missing isolated MCP configuration.'
    }
    $local = [IO.File]::ReadAllText($path)
    $ports = @([regex]::Matches($local, '(?m)^MC_MCP_PORT\s*=\s*"(\d+)"'))
    if ($ports.Count -ne 1)
    {
        throw 'A single explicit local MC_MCP_PORT is required.'
    }
    $port = [int]$ports[0].Groups[1].Value
    if ($port -lt 1 -or $port -gt 65535)
    {
        throw 'Invalid local MCP port.'
    }
    if ((Git $Root @('diff', '--cached', '--name-only', '--', $ConfigPath)).Text)
    {
        throw 'Staged MCP configuration needs manual review.'
    }
    $baseText = (Git $Root @('show', "$( $Base ):$ConfigPath")).Text
    $sourceText = (Git $Root @('show', "$( $Source ):$ConfigPath")).Text
    foreach ($item in @(@('local', $local), @('base', $baseText), @('source', $sourceText)))
    {
        [IO.File]::WriteAllText((Join-Path $Directory ($item[0] + '.toml')), (Normalize-Config $item[1]), $Utf8)
    }
    $merged = Git $Root @('merge-file', '-p', (Join-Path $Directory 'local.toml'),
    (Join-Path $Directory 'base.toml'), (Join-Path $Directory 'source.toml')) -AllowFailure
    if ($merged.Code -ne 0)
    {
        throw 'MCP configuration conflict; original local file unchanged.'
    }
    $text = $merged.Text.Replace('__WORKSPACE_ROOT__',$Root.Replace('\', '/')).Replace('__MCP_PORT__', "$port")
    $ending = $Lf
    if ( $local.Contains($CrLf))
    {
        $text = $text.Replace($Lf, $CrLf); $ending = $CrLf
    }
    return [pscustomobject]@{ Text = $text + $ending; Port = $port }
}
if (-not $SourceRoot)
{
    $SourceRoot = (Git $PSScriptRoot @('rev-parse', '--show-toplevel')).Text
}
$SourceRoot = [IO.Path]::GetFullPath($SourceRoot).TrimEnd('\', '/')
if ($FromHook)
{
    $caller = (Git ((Get-Location).Path) @('rev-parse', '--show-toplevel') -AllowFailure).Text
    if (-not $caller -or [IO.Path]::GetFullPath($caller).TrimEnd('\', '/') -ne $SourceRoot)
    {
        return
    }
}
if ((Git $SourceRoot @('symbolic-ref', '--short', 'HEAD')).Text -ne $SourceRef)
{
    throw 'Foundation checkout must remain on its source branch.'
}
$source = (Git $SourceRoot @('rev-parse', '--verify', "$SourceRef^{commit}")).Text
$common = (Git $SourceRoot @('rev-parse', '--path-format=absolute', '--git-common-dir')).Text
$dataRoot = Join-Path $common 'workspace-sync'
$registered = [Collections.Generic.List[string]]::new()
foreach ($line in (Git $SourceRoot @('worktree', 'list', '--porcelain')).Lines)
{
    if ( $line.StartsWith('worktree '))
    {
        $registered.Add([IO.Path]::GetFullPath($line.Substring(9)).TrimEnd('\', '/'))
    }
}
if ($TargetRoots.Count -eq 0)
{
    $TargetRoots = @($registered | Where-Object { $_ -ne $SourceRoot })
}
foreach ($root in $TargetRoots)
{
    $absolute = [IO.Path]::GetFullPath($root).TrimEnd('\', '/')
    if ($absolute -eq $SourceRoot -or -not $registered.Contains($absolute))
    {
        throw "Not a registered secondary worktree: $root"
    }
}
if (-not $Apply)
{
    [pscustomobject]@{ Mode = 'Plan'; Source = $source; SourceRoot = $SourceRoot; Targets = $TargetRoots; InstallHook = [bool]$InstallHook } | ConvertTo-Json -Depth 5
    return
}
[IO.Directory]::CreateDirectory($dataRoot) | Out-Null
$script:ProcessLog = Join-Path $dataRoot 'processes.jsonl'
try
{
    $lock = [IO.File]::Open((Join-Path $dataRoot 'sync.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
}
catch
{
    if (-not $FromHook)
    {
        Write-Warning 'Synchronization already owns the repository lock; skipped.'
    }; return
}
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$results = [Collections.Generic.List[object]]::new()
try
{
    foreach ($target in $TargetRoots)
    {
        $target = [IO.Path]::GetFullPath($target).TrimEnd('\', '/')
        $key = (Hash-Text $target.ToLowerInvariant()).Substring(0, 16)
        $statePath = Join-Path $dataRoot "state/$key.json"
        if ((Test-Path -LiteralPath $statePath) -and -not $Resume)
        {
            $old = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
            if ($old.Status -ne 'Synced')
            {
                $results.Add([pscustomobject]@{ Target = $target; Status = 'Paused'; Reason = 'Existing pause; inspect state and use -Resume after resolution.' })
                continue
            }
        }
        $directory = Join-Path $dataRoot "$runId/$key"
        [IO.Directory]::CreateDirectory($directory) | Out-Null
        $record = [ordered]@{ Target = $target; Source = $source; Status = 'Paused'; Before = ''; BackupRef = ''; StashRef = ''; Reason = ''; Detail = @(); Time = (Get-Date).ToString('o') }
        $config = $null
        $savedTracked = $false
        $mergeCompleted = $false
        try
        {
            if ((Git $target @('rev-parse', '--path-format=absolute', '--git-common-dir')).Text -ne $common)
            {
                throw 'Different Git repository.'
            }
            $record.Before = (Git $target @('rev-parse', 'HEAD')).Text
            Git $target @('symbolic-ref', '--quiet', 'HEAD') | Out-Null
            if ((Git $target @('ls-files', '-u')).Text)
            {
                throw 'Existing unresolved index entries.'
            }
            foreach ($marker in @('MERGE_HEAD', 'CHERRY_PICK_HEAD', 'REVERT_HEAD', 'rebase-merge', 'rebase-apply', 'index.lock'))
            {
                $markerPath = (Git $target @('rev-parse', '--git-path', $marker)).Text
                if (-not [IO.Path]::IsPathRooted($markerPath))
                {
                    $markerPath = Join-Path $target $markerPath
                }
                if (Test-Path -LiteralPath $markerPath)
                {
                    throw "Existing Git operation: $marker"
                }
            }
            if (Test-Path -LiteralPath (Join-Path $target '.gradle/workspace-sync.busy'))
            {
                throw 'Workspace owner marked checkout busy.'
            }
            if ((Git $target @('merge-base', '--is-ancestor', $source, 'HEAD') -AllowFailure).Code -eq 0)
            {
                $record.Status = 'Synced'; $record.Reason = 'Already contains source commit.'
            }
            else
            {
                $stamp = Worktree-Stamp $target
                $base = (Git $target @('merge-base', 'HEAD', $source)).Text
                $incoming = (Git $target @('diff', '--name-only', $base, $source)).Lines
                if (@($incoming | Where-Object { $_ -match $RuntimePattern }).Count)
                {
                    throw 'Incoming protected runtime paths require manual review.'
                }
                $workingFiles = @(Save-WorkingFiles $target $directory)
                $config = Merge-LocalConfig $target $base $source $directory
                [IO.File]::WriteAllBytes((Join-Path $directory 'local-config.backup'),[IO.File]::ReadAllBytes((Join-Path $target $ConfigPath)))
                $record.BackupRef = "refs/backups/workspace-sync/$runId/$key/head"
                Git $target @('update-ref', $record.BackupRef, $record.Before) | Out-Null
                $wip = (Git $target @('stash', 'create', "workspace-sync-preflight-$runId")).Text
                if ($wip)
                {
                    Git $target @('update-ref', "refs/backups/workspace-sync/$runId/$key/wip", $wip) | Out-Null
                    $tree = Mask-ConfigTree $target $wip $source $directory
                    $preview = (Git $target @('commit-tree', $tree, '-p', $record.Before, '-m', "workspace-sync preflight $runId")).Text
                }
                else
                {
                    $preview = $record.Before
                }
                $check = Git $target @('merge-tree', '--write-tree', $preview, $source) -AllowFailure
                if ($check.Code -ne 0)
                {
                    $record.Detail = @($check.Lines | Where-Object { $_ -match 'CONFLICT|Auto-merging' })
                    throw 'Three-way preflight conflict; original worktree/index unchanged.'
                }
                $check = Git $target @('merge-tree', '--write-tree', $record.Before, $source) -AllowFailure
                if ($check.Code -ne 0)
                {
                    $record.Detail = $check.Lines; throw 'Committed branch conflict; original worktree unchanged.'
                }
                $sourceFiles = (Git $target @('ls-tree', '-r', '--name-only', $source)).Lines
                foreach ($file in (Git $target @('ls-files', '--others', '--exclude-standard')).Lines)
                {
                    if ($sourceFiles -contains $file -or @($sourceFiles | Where-Object { $_.StartsWith($file + '/') -or $file.StartsWith($_ + '/') }).Count)
                    {
                        throw "Untracked incoming collision: $file"
                    }
                }
                if ((Worktree-Stamp $target) -ne $stamp)
                {
                    throw 'Workspace changed during preflight; merge skipped.'
                }
                if ($FromHook -and $wip -and -not (Test-Path -LiteralPath (Join-Path $target '.gradle/workspace-sync.ready')))
                {
                    throw 'Dirty workspace requires owner readiness marker for unattended synchronization.'
                }
                if ($wip)
                {
                    Git $target @('stash', 'push', '-m', "workspace-sync-$runId") | Out-Null
                    $savedTracked = $true
                    $stash = (Git $target @('rev-parse', 'refs/stash')).Text
                    $record.StashRef = "refs/backups/workspace-sync/$runId/$key/stash"
                    Git $target @('update-ref', $record.StashRef, $stash) | Out-Null
                    if ((Git $target @('rev-parse', "$stash^{tree}")).Text -ne (Git $target @('rev-parse', "$wip^{tree}")).Text)
                    {
                        Git $target @('stash', 'apply', '--index', $stash) | Out-Null
                        throw 'Workspace changed before stash; restored and skipped.'
                    }
                    $restoreTree = Mask-ConfigTree $target $stash $record.Before $directory
                    $indexTree = Mask-ConfigTree $target "$stash^2" $record.Before $directory
                    $indexCommit = (Git $target @('commit-tree', $indexTree, '-p', $record.Before, '-m', "workspace-sync index $runId")).Text
                    $restore = (Git $target @('commit-tree', $restoreTree, '-p', $record.Before, '-p', $indexCommit, '-m', "workspace-sync restore $runId")).Text
                    Git $target @('update-ref', "refs/backups/workspace-sync/$runId/$key/restore", $restore) | Out-Null
                }
                Save-Json (Join-Path $directory 'before-merge.json') $record
                $merge = Git $target @('merge', '--no-edit', '--no-ff', $source) -AllowFailure
                if ($merge.Code -ne 0)
                {
                    $record.Detail = $merge.Lines; throw 'Merge stopped; local changes remain in backup stash.'
                }
                $mergeCompleted = $true
                [IO.File]::WriteAllText((Join-Path $target $ConfigPath), $config.Text, $Utf8)
                if ($wip)
                {
                    $restored = Git $target @('stash', 'apply', '--index', $restore) -AllowFailure
                    if ($restored.Code -ne 0)
                    {
                        $record.Detail = $restored.Lines; throw 'Local restoration conflict; backup stash retained.'
                    }
                }
                Restore-WorkingFormat $target $workingFiles
                $record.Status = 'Synced'; $record.Reason = 'Source merged; local index/worktree restored; local MCP port retained.'
            }
        }
        catch
        {
            $record.Reason = $_.Exception.Message
        }
        finally
        {
            if ($savedTracked -and -not $mergeCompleted)
            {
                [IO.File]::WriteAllBytes((Join-Path $target $ConfigPath),[IO.File]::ReadAllBytes((Join-Path $directory 'local-config.backup')))
            }
        }
        Save-Json $statePath $record
        Save-Json (Join-Path $directory 'result.json') $record
        $results.Add([pscustomobject]$record)
    }
    if ($InstallHook)
    {
        if ((Git $SourceRoot @('config', '--get', 'core.hooksPath') -AllowFailure).Text)
        {
            throw 'Custom core.hooksPath exists; integrate manually without overwriting it.'
        }
        $hook = Join-Path $common 'hooks/post-commit'
        $marker = '# lobecorp-workspace-sync'
        if (Test-Path -LiteralPath $hook)
        {
            if (-not [IO.File]::ReadAllText($hook).Contains($marker))
            {
                throw 'Existing post-commit hook retained; manual integration required.'
            }
        }
        else
        {
            $scriptPath = $PSCommandPath.Replace('\', '/')
            if ($scriptPath.Contains("'") -or $SourceRoot.Contains("'"))
            {
                throw 'Hook paths containing apostrophes require manual installation.'
            }
            $body = @("#!/bin/sh", $marker,
            "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File '$scriptPath' -SourceRoot '$($SourceRoot.Replace('\', '/') )' -Apply -FromHook >> '$($dataRoot.Replace('\', '/') )/hook.log' 2>&1",
            'exit 0') -join $Lf
            [IO.File]::WriteAllText($hook, $body + $Lf, $Utf8)
        }
    }
    Save-Json (Join-Path $dataRoot 'latest.json') @($results)
    $results | ConvertTo-Json -Depth 8
}
finally
{
    $lock.Dispose()
}
