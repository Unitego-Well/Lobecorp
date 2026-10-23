[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Root, [switch]$Stop)
$ErrorActionPreference = 'Stop'
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class WorkspaceCwd {
 [DllImport("ntdll.dll")] static extern int NtQueryInformationProcess(IntPtr h, int c, IntPtr b, int n, out int r);
 [DllImport("kernel32.dll")] static extern bool ReadProcessMemory(IntPtr h, IntPtr p, byte[] b, int n, out IntPtr r);
 [DllImport("kernel32.dll")] static extern IntPtr OpenProcess(int a, bool i, int p);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
 static byte[] Read(IntPtr h, long p, int n) { var b=new byte[n]; IntPtr r; if(!ReadProcessMemory(h,new IntPtr(p),b,n,out r))throw new Exception();return b; }
 public static string Get(int pid) { IntPtr h=OpenProcess(0x410,false,pid); if(h==IntPtr.Zero)return null; IntPtr b=Marshal.AllocHGlobal(48); try {int r;if(NtQueryInformationProcess(h,0,b,48,out r)!=0)return null;long peb=Marshal.ReadInt64(b,8);long par=BitConverter.ToInt64(Read(h,peb+0x20,8),0);var u=Read(h,par+0x38,16);int len=BitConverter.ToUInt16(u,0);long p=BitConverter.ToInt64(u,8);return System.Text.Encoding.Unicode.GetString(Read(h,p,len));}catch{return null;}finally{Marshal.FreeHGlobal(b);CloseHandle(h);} }
}
'@
$prefix = [IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'
$items = @(Get-CimInstance Win32_Process | ForEach-Object {
    $dir = [WorkspaceCwd]::Get($_.ProcessId)
    if ($dir -and $dir.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase))
    {
        $safe = ($_.Name -eq 'node_repl.exe') -or
                ($_.Name -eq 'node.exe' -and $_.CommandLine -match 'cua-repl.mjs|minecraft-mod-mcp[/\\]dist[/\\]cli.js') -or
                ($_.Name -eq 'python.exe' -and $_.CommandLine -match 'mcp-idle-reaper[/\\]idle_proxy.py')
        [pscustomobject]@{ Id = $_.ProcessId; Name = $_.Name; Start = $_.CreationDate; Directory = $dir; SafeTool = $safe }
    }
})
$items | ConvertTo-Json -Depth 3
if ($Stop)
{
    foreach ($item in $items)
    {
        if (-not $item.SafeTool)
        {
            continue
        }
        $live = Get-Process -Id $item.Id -ErrorAction SilentlyContinue
        if ($live -and [Math]::Abs(($live.StartTime - $item.Start).TotalMilliseconds) -lt 10 -and
                [WorkspaceCwd]::Get($item.Id).StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase))
        {
            if (-not $live.CloseMainWindow())
            {
                Stop-Process -Id $item.Id -Force
            }
            Write-Output ('Stopped confirmed workspace tool PID ' + $item.Id)
        }
    }
}
