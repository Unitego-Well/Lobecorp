param([Parameter(Mandatory=$true)][int]$GamePid,[Parameter(Mandatory=$true)][int]$ObserverPid,[Parameter(Mandatory=$true)][string]$LogPath)
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class PhotonPerfWindow {
 [DllImport("user32.dll")] public static extern bool IsWindow(IntPtr h);
 [DllImport("user32.dll")] public static extern bool IsZoomed(IntPtr h);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h,out uint pid);
}
'@
function Write-Evidence($value) {
 [IO.File]::AppendAllText($LogPath, (($value | ConvertTo-Json -Compress) + [Environment]::NewLine))
}
$game = [Diagnostics.Process]::GetProcessById($GamePid)
$observer = [Diagnostics.Process]::GetProcessById($ObserverPid)
$limit = [DateTime]::UtcNow.AddMinutes(8)
$handle = [IntPtr]::Zero
while (!$game.HasExited -and [DateTime]::UtcNow -lt $limit) {
 $game.Refresh()
 $handle = $game.MainWindowHandle
 if ($handle -ne [IntPtr]::Zero) { break }
 Start-Sleep -Milliseconds 250
}
if ($game.HasExited -or $handle -eq [IntPtr]::Zero) { throw 'game_window_unavailable' }
[uint32]$windowOwner = 0
[void][PhotonPerfWindow]::GetWindowThreadProcessId($handle,[ref]$windowOwner)
if ($windowOwner -ne $GamePid -or $game.ProcessName -notin @('java','javaw') -or $game.MainWindowTitle -notmatch 'Minecraft') { throw 'window_identity_mismatch' }
Write-Evidence @{event='owned_window';mode='observe_only';utc=[DateTime]::UtcNow.ToString('o');pid=$GamePid;handle=$handle.ToInt64();title=$game.MainWindowTitle}
$previous = ''
try {
 while (!$game.HasExited -and !$observer.HasExited -and [DateTime]::UtcNow -lt $limit -and [PhotonPerfWindow]::IsWindow($handle)) {
  $maximized = [PhotonPerfWindow]::IsZoomed($handle)
  $focused = [PhotonPerfWindow]::GetForegroundWindow() -eq $handle
  $state = "$maximized/$focused"
  if ($state -ne $previous) {
   Write-Evidence @{event='state';utc=[DateTime]::UtcNow.ToString('o');pid=$GamePid;maximized=$maximized;focused=$focused}
   $previous = $state
  }
  # 只记录窗口状态，不前置、聚焦、最大化或操作实体鼠标。
  Start-Sleep -Milliseconds 500
 }
} finally {
 Write-Evidence @{event='released';utc=[DateTime]::UtcNow.ToString('o');pid=$GamePid;gameExited=$game.HasExited;observerExited=$observer.HasExited}
 $game.Dispose()
 $observer.Dispose()
}
