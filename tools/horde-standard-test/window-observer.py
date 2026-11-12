"""Read-only, PID-scoped Windows window event recorder."""
import argparse
import ctypes as c
from ctypes import wintypes as w
import datetime
import json
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pid', type=int, required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    if args.pid <= 0:
        parser.error('pid must be positive')
    with open(args.output, 'a', encoding='utf-8', buffering=1) as output:
        def emit(event, **fields):
            output.write(json.dumps(dict(utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                         perfCounterNs=time.perf_counter_ns(), pid=args.pid,
                                         event=event, **fields)) + '\n')
        handles = []
        process = None
        try:
            user = c.WinDLL('user32', use_last_error=True)
            kernel = c.WinDLL('kernel32', use_last_error=True)
            # Optional observation APIs must never prevent the lifecycle recorder.
            try:
                dwm_frame = c.WinDLL('dwmapi').DwmGetWindowAttribute
                dwm_frame.argtypes = [w.HWND, w.DWORD, c.c_void_p, w.DWORD]
                dwm_frame.restype = c.c_long
                dwm_error = None
            except (OSError, AttributeError) as error:
                dwm_frame, dwm_error = None, str(error)
            try:
                thread_dpi = user.GetThreadDpiAwarenessContext
                thread_dpi.argtypes = []
                thread_dpi.restype = c.c_void_p
                dpi_awareness = user.GetAwarenessFromDpiAwarenessContext
                dpi_awareness.argtypes = [c.c_void_p]
                dpi_awareness.restype = c.c_int
                dpi_error = None
            except (OSError, AttributeError) as error:
                thread_dpi, dpi_awareness, dpi_error = None, None, str(error)
            callback_type = c.WINFUNCTYPE(None, w.HANDLE, w.DWORD, w.HWND, w.LONG, w.LONG, w.DWORD, w.DWORD)
            class Placement(c.Structure):
                _fields_ = [('length', w.UINT), ('flags', w.UINT), ('showCmd', w.UINT),
                            ('minPosition', w.POINT), ('maxPosition', w.POINT), ('normalPosition', w.RECT)]
            kernel.OpenProcess.argtypes = [w.DWORD, w.BOOL, w.DWORD]
            kernel.OpenProcess.restype = w.HANDLE
            kernel.CloseHandle.argtypes = [w.HANDLE]
            user.SetWinEventHook.argtypes = [w.DWORD, w.DWORD, w.HMODULE, callback_type, w.DWORD, w.DWORD, w.DWORD]
            user.SetWinEventHook.restype = w.HANDLE
            user.UnhookWinEvent.argtypes = [w.HANDLE]
            user.GetWindowThreadProcessId.argtypes = [w.HWND, c.POINTER(w.DWORD)]
            user.IsWindowVisible.argtypes = [w.HWND]
            user.IsZoomed.argtypes = [w.HWND]
            user.GetWindowRect.argtypes = [w.HWND, c.POINTER(w.RECT)]
            user.GetWindowPlacement.argtypes = [w.HWND, c.POINTER(Placement)]
            user.MsgWaitForMultipleObjects.argtypes = [w.DWORD, c.POINTER(w.HANDLE), w.BOOL, w.DWORD, w.DWORD]
            user.MsgWaitForMultipleObjects.restype = w.DWORD
            user.PeekMessageW.argtypes = [c.POINTER(w.MSG), w.HWND, w.UINT, w.UINT, w.UINT]
            user.TranslateMessage.argtypes = [c.POINTER(w.MSG)]
            user.DispatchMessageW.argtypes = [c.POINTER(w.MSG)]
            user.DispatchMessageW.restype = c.c_ssize_t
            process = kernel.OpenProcess(0x100000, False, args.pid)
            if not process:
                raise c.WinError(c.get_last_error())
            def rectangle(rect):
                return [rect.left, rect.top, rect.right, rect.bottom]
            def extra_observations(hwnd):
                result = dict(physicalFrameRect=None, physicalFrameRectError=dwm_error,
                              observerThreadDpiContext=None, observerThreadDpiAwareness=None,
                              observerThreadDpiError=dpi_error)
                try:
                    if dwm_frame:
                        frame = w.RECT()
                        hr = dwm_frame(hwnd, 9, c.byref(frame), c.sizeof(frame))
                        if hr == 0:
                            result['physicalFrameRect'] = rectangle(frame)
                        else:
                            result['physicalFrameRectError'] = f'HRESULT 0x{hr & 0xffffffff:08X}'
                except Exception as error:
                    result['physicalFrameRectError'] = str(error)
                try:
                    if thread_dpi:
                        context = thread_dpi()
                        awareness = dpi_awareness(context) if context else -1
                        result['observerThreadDpiContext'] = context
                        result['observerThreadDpiAwareness'] = awareness if awareness >= 0 else None
                        if awareness < 0:
                            result['observerThreadDpiError'] = 'unknown or invalid DPI context'
                except Exception as error:
                    result['observerThreadDpiError'] = str(error)
                return result
            @callback_type
            def callback(hook, event, hwnd, obj, child, thread, timestamp):
                try:
                    pid = w.DWORD()
                    user.GetWindowThreadProcessId(hwnd, c.byref(pid))
                    if obj != 0 or child != 0 or pid.value != args.pid or not user.IsWindowVisible(hwnd):
                        return
                    rect, placement = w.RECT(), Placement()
                    placement.length = c.sizeof(placement)
                    if not user.GetWindowRect(hwnd, c.byref(rect)) or not user.GetWindowPlacement(hwnd, c.byref(placement)):
                        raise c.WinError(c.get_last_error())
                    emit({0x800B: 'LOCATIONCHANGE', 0x16: 'MINIMIZESTART', 0x17: 'MINIMIZEEND'}[event],
                         hwnd=int(hwnd), rect=rectangle(rect), maximized=bool(user.IsZoomed(hwnd)),
                         placement=dict(flags=placement.flags, showCmd=placement.showCmd,
                                        normalRect=rectangle(placement.normalPosition)), eventTimeMs=timestamp,
                         **extra_observations(hwnd))
                except Exception as error:
                    emit('gate-failed', error=str(error))
            for first, last in [(0x800B, 0x800B), (0x16, 0x17)]:
                hook = user.SetWinEventHook(first, last, None, callback, args.pid, 0, 0)
                if not hook:
                    raise c.WinError(c.get_last_error())
                handles.append(hook)
            emit('observer-ready')
            wait_handles = (w.HANDLE * 1)(process)
            message = w.MSG()
            while True:
                result = user.MsgWaitForMultipleObjects(1, wait_handles, False, 0xFFFFFFFF, 0x4FF)
                if result == 0:
                    emit('process-exited')
                    break
                if result != 1:
                    raise c.WinError(c.get_last_error())
                while user.PeekMessageW(c.byref(message), None, 0, 0, 1):
                    user.TranslateMessage(c.byref(message))
                    user.DispatchMessageW(c.byref(message))
        except Exception as error:
            emit('gate-failed', error=str(error))
            return 1
        finally:
            for hook in handles:
                user.UnhookWinEvent(hook)
            if process:
                kernel.CloseHandle(process)
            emit('observer-stopped')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
