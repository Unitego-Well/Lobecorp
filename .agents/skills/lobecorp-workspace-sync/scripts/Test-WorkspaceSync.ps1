[CmdletBinding()]
param([string]$ReportRoot)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$sync = Join-Path $PSScriptRoot 'Sync-Workspaces.ps1'
if (-not $ReportRoot)
{
    $ReportRoot = Join-Path ((& git -C $PSScriptRoot rev-parse --show-toplevel) | Out-String).Trim() 'build'
}
$fixture = Join-Path $ReportRoot ('workspace-sync-fixture-' + [guid]::NewGuid().ToString('N'))
$source = Join-Path $fixture 'source'
$target = Join-Path $fixture 'target'
$cleanTarget = Join-Path $fixture 'clean-target'
$utf8 = [Text.UTF8Encoding]::new($false)
$lf = [string][char]10
function Put
{
    param([string]$Path, [string]$Value)
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path)) | Out-Null
    [IO.File]::WriteAllText($Path, $Value, $utf8)
}
function G
{
    param([string]$Root, [string[]]$Arguments)
    $saved = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try
    {
        $output = @(& git -C $Root @Arguments 2>&1); $code = $LASTEXITCODE
    }
    finally
    {
        $ErrorActionPreference = $saved
    }
    if ($code -ne 0)
    {
        throw ($output -join [Environment]::NewLine)
    }
    return ($output -join [Environment]::NewLine).Trim()
}
function Assert-That
{
    param([bool]$Condition, [string]$Message)
    if (-not $Condition)
    {
        throw $Message
    }
}
$passed = [Collections.Generic.List[string]]::new()
try
{
    [IO.Directory]::CreateDirectory($source) | Out-Null
    G $source @('init', '-b', 'dev') | Out-Null
    G $source @('config', 'user.name', 'Workspace Sync Fixture') | Out-Null
    G $source @('config', 'user.email', 'fixture@localhost') | Out-Null
    Put (Join-Path $source '.gitignore') ('.gradle/' + $lf + 'run/' + $lf)
    $config = @'
[mcp_servers.minecraft]
cwd = "D:/base/Lobecorp/modules/minecraft-mcp/packages/minecraft-mod-mcp"
args = ["D:/base/Lobecorp/modules/minecraft-mcp/packages/minecraft-mod-mcp/dist/cli.js"]
[mcp_servers.minecraft.env]
MC_MCP_PORT = "19878"
'@
    Put (Join-Path $source '.codex/config.toml') $config
    foreach ($file in @('local.txt', 'staged.txt', 'shared.txt'))
    {
        Put (Join-Path $source $file) 'base'
    }
    G $source @('add', '.') | Out-Null
    G $source @('commit', '-m', 'fixture base') | Out-Null
    G $source @('worktree', 'add', '-b', 'fixture/task', $target) | Out-Null
    Put (Join-Path $target 'task.txt') 'task commit'
    G $target @('add', 'task.txt') | Out-Null
    G $target @('commit', '-m', 'fixture task') | Out-Null
    $taskHead = G $target @('rev-parse', 'HEAD')
    Put (Join-Path $target '.codex/config.toml') ($config.Replace('D:/base/Lobecorp',$target.Replace('\', '/')).Replace('19878', '19111'))
    Put (Join-Path $target 'staged.txt') 'staged'
    G $target @('add', 'staged.txt') | Out-Null
    Put (Join-Path $target 'staged.txt') ('staged' + $lf + 'unstaged')
    Put (Join-Path $target 'local.txt') 'task worktree'
    Put (Join-Path $target 'untracked.txt') 'keep untracked'
    Put (Join-Path $target 'run/client/saves/keep.dat') 'keep ignored save'
    $incoming = $config.Replace('D:/base/Lobecorp',$source.Replace('\', '/'))
    $incoming += $lf + '[mcp_servers.normify]' + $lf + 'cwd = "' + $source.Replace('\', '/') + '/tools/lobecorp-normify"' + $lf
    Put (Join-Path $source '.codex/config.toml') $incoming
    Put (Join-Path $source 'shared.txt') 'source change'
    G $source @('add', '.') | Out-Null
    G $source @('commit', '-m', 'fixture source') | Out-Null
    $sourceHead = G $source @('rev-parse', 'HEAD')
    $result = (& $sync -SourceRoot $source -Apply | Out-String) | ConvertFrom-Json
    Assert-That ($result.Status -eq 'Synced') ("Sync failed: " + $result.Reason)
    G $target @('merge-base', '--is-ancestor', $sourceHead, 'HEAD') | Out-Null
    G $target @('merge-base', '--is-ancestor', $taskHead, 'HEAD') | Out-Null
    Assert-That ((G $target @('show', ':staged.txt')) -eq 'staged') 'Staged contents changed.'
    Assert-That ([IO.File]::ReadAllText((Join-Path $target 'staged.txt')) -eq ('staged' + $lf + 'unstaged')) 'Unstaged contents changed.'
    Assert-That ([IO.File]::ReadAllText((Join-Path $target 'untracked.txt')) -eq 'keep untracked') 'Untracked contents changed.'
    Assert-That ([IO.File]::ReadAllText((Join-Path $target 'run/client/saves/keep.dat')) -eq 'keep ignored save') 'Ignored runtime data changed.'
    $actualConfig = [IO.File]::ReadAllText((Join-Path $target '.codex/config.toml'))
    Assert-That ($actualConfig.Contains('19111') -and $actualConfig.Contains($target.Replace('\', '/') + '/tools/lobecorp-normify')) 'Configuration isolation or new MCP registration lost.'
    $passed.Add('Divergent branch merged; staged, unstaged, untracked, ignored data and local port preserved.')
    $head = G $target @('rev-parse', 'HEAD')
    $index = G $target @('write-tree')
    $status = G $target @('status', '--porcelain')
    Put (Join-Path $source 'local.txt') 'conflicting source change'
    G $source @('add', 'local.txt') | Out-Null
    G $source @('commit', '-m', 'fixture conflict') | Out-Null
    $result = (& $sync -SourceRoot $source -Apply | Out-String) | ConvertFrom-Json
    Assert-That ($result.Status -eq 'Paused' -and $result.Reason.Contains('preflight conflict')) 'Conflict was not paused in preflight.'
    Assert-That ((G $target @('rev-parse', 'HEAD')) -eq $head) 'Conflict changed HEAD.'
    Assert-That ((G $target @('write-tree')) -eq $index) 'Conflict changed index.'
    Assert-That ((G $target @('status', '--porcelain')) -eq $status) 'Conflict changed status.'
    Assert-That ([IO.File]::ReadAllText((Join-Path $target 'local.txt')) -eq 'task worktree') 'Conflict overwrote worktree.'
    $passed.Add('Conflicting WIP paused before changing HEAD, index or worktree.')
    $result = (& $sync -SourceRoot $source -Apply -InstallHook | Out-String) | ConvertFrom-Json
    Assert-That ($result.Reason.Contains('Existing pause')) 'Pause was retried automatically.'
    Assert-That (Test-Path -LiteralPath (Join-Path $source '.git/hooks/post-commit')) 'Hook not installed.'
    Put (Join-Path $source 'hook.txt') 'hook trigger'
    G $source @('add', 'hook.txt') | Out-Null
    G $source @('commit', '-m', 'fixture hook') | Out-Null
    Assert-That (Test-Path -LiteralPath (Join-Path $source '.git/workspace-sync/hook.log')) 'Hook did not execute.'
    $hookResult = Get-Content -LiteralPath (Join-Path $source '.git/workspace-sync/hook.log') -Raw | ConvertFrom-Json
    Assert-That ($hookResult.Status -eq 'Paused') 'Hook did not return its actual synchronization result.'
    $passed.Add('Post-commit trigger executed and returned its retained pause as valid JSON.')
    G $source @('worktree', 'add', '-b', 'fixture/clean', $cleanTarget) | Out-Null
    $cleanConfig = [IO.File]::ReadAllText((Join-Path $cleanTarget '.codex/config.toml')).Replace($source.Replace('\', '/'),$cleanTarget.Replace('\', '/')).Replace('19878', '19112')
    Put (Join-Path $cleanTarget '.codex/config.toml') $cleanConfig
    G $cleanTarget @('add', '.codex/config.toml') | Out-Null
    G $cleanTarget @('commit', '-m', 'fixture isolated clean config') | Out-Null
    Put (Join-Path $source 'hook-clean.txt') 'automatic clean target'
    G $source @('add', 'hook-clean.txt') | Out-Null
    G $source @('commit', '-m', 'fixture automatic clean sync') | Out-Null
    $latestSource = G $source @('rev-parse', 'HEAD')
    G $cleanTarget @('merge-base', '--is-ancestor', $latestSource, 'HEAD') | Out-Null
    $hookLatest = Get-Content -LiteralPath (Join-Path $source '.git/workspace-sync/latest.json') -Raw | ConvertFrom-Json
    $cleanResult = @($hookLatest | Where-Object { $_.Target -eq $cleanTarget })
    Assert-That ($cleanResult.Count -eq 1 -and $cleanResult[0].Status -eq 'Synced') 'Automatic clean sync did not finish.'
    Assert-That ([IO.File]::ReadAllText((Join-Path $cleanTarget '.codex/config.toml')).Contains('19112')) 'Automatic sync changed local port.'
    $passed.Add('Actual commit hook merged a clean secondary branch and preserved its dedicated configuration.')
}
finally
{
    foreach ($target in @($target, $cleanTarget))
    {
        if (Test-Path -LiteralPath $target)
        {
            $absolute = [IO.Path]::GetFullPath($target)
            if (-not $absolute.StartsWith([IO.Path]::GetFullPath($fixture) + [IO.Path]::DirectorySeparatorChar))
            {
                throw 'Cleanup escaped fixture root.'
            }
            G $source @('worktree', 'remove', '--force', $absolute) | Out-Null
        }
    }
    $absoluteFixture = [IO.Path]::GetFullPath($fixture)
    $allowedParent = [IO.Path]::GetFullPath($ReportRoot).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $absoluteFixture.StartsWith($allowedParent, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($absoluteFixture) -notmatch '^workspace-sync-fixture-[0-9a-f]{32}$')
    {
        throw 'Fixture cleanup target was not verified.'
    }
    if (Test-Path -LiteralPath $absoluteFixture)
    {
        Remove-Item -LiteralPath $absoluteFixture -Recurse -Force
    }
    $report = [pscustomobject]@{ Passed = @($passed.ToArray()); Fixture = $fixture; Cleanup = 'Removed'; Time = (Get-Date).ToString('o') }
    Put (Join-Path $ReportRoot 'workspace-sync-validation.json') ($report | ConvertTo-Json -Depth 5)
}
$report | ConvertTo-Json -Depth 5
