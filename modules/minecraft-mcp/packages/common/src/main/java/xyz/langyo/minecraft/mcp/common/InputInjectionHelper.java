package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Constructor;

public final class InputInjectionHelper {

    private InputInjectionHelper() {}

    public static void sendKey(long handle, int key, int action) {
        if (ReflectionCache.LWJGL3) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object kbHandler = getKeyboardHandler(mc);
                if (kbHandler != null) {
                    for (java.lang.reflect.Method m : kbHandler.getClass().getDeclaredMethods()) {
                        Class<?>[] pts = m.getParameterTypes();
                        if (m.getName().equals("keyPress") && pts.length == 3 && pts[0] == long.class
                                && pts[1] == int.class && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                            Constructor<?> constructor = pts[2].getDeclaredConstructor(int.class, int.class, int.class);
                            constructor.setAccessible(true);
                            m.setAccessible(true);
                            m.invoke(kbHandler, handle, action, constructor.newInstance(key, 0, 0));
                            return;
                        }
                        if (pts.length == 5 && pts[0] == long.class && pts[1] == int.class
                                && pts[2] == int.class && pts[3] == int.class && pts[4] == int.class
                                && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                            m.setAccessible(true);
                            m.invoke(kbHandler, handle, key, 0, action, 0);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendKey GLFW: " + e.getMessage());
            }
        }
        throw new IllegalStateException("Internal keyboard injection is unavailable; use player_control or gui_control.");
    }

    private static Object getKeyboardHandler(Object mc) {
        try { return mc.getClass().getField("keyboardHandler").get(mc); } catch (Exception ignored) {}
        try { return mc.getClass().getDeclaredField("keyboardHandler").get(mc); } catch (Exception ignored) {}
        java.lang.reflect.Field discovered = ReflectionCache.getDiscoveredField("keyboardHandler");
        if (discovered != null) { try { discovered.setAccessible(true); return discovered.get(mc); } catch (Exception ignored) {} }
        for (java.lang.reflect.Field f : ReflectionCache.getAllFields(mc.getClass())) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                try { f.setAccessible(true); Object v = f.get(mc); if (v != null && ReflectionCache.isKeyboardHandlerType(v.getClass())) return v; } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static Object getMouseHandlerField(Object mc) {
        return ReflectionCache.getMouseHandler(mc);
    }

    public static void sendMouseButton(long handle, int button, int action) {
        if (ReflectionCache.LWJGL3 && handle != 0) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = ReflectionCache.getMouseHandler(mc);
                if (mouseHandler != null) {
                    java.lang.reflect.Method target = ReflectionCache.findMouseButtonMethod(mouseHandler.getClass());
                    if (target != null) {
                        target.setAccessible(true);
                        Class<?>[] pt = target.getParameterTypes();
                        if (pt.length == 3 && pt[1] != int.class) {
                            Object mouseInput = createMouseInput(pt[1], button, 0);
                            if (mouseInput != null) {
                                target.invoke(mouseHandler, handle, mouseInput, action);
                                return;
                            }
                        } else {
                            target.invoke(mouseHandler, handle, button, action, 0);
                            return;
                        }
                    }
                }
                throw new IllegalStateException("No internal mouse button callback is available.");
            } catch (Exception e) {
                ReflectionHelper.dbg("sendMouseButton GLFW: " + e.getMessage());
            }
        }
        throw new IllegalStateException("Internal mouse injection is unavailable; use gui_control or player_control.");
    }

    public static Object createMouseInputPublic(Class<?> mouseInputClass, int button, int modifiers) {
        return createMouseInput(mouseInputClass, button, modifiers);
    }

    private static Object createMouseInput(Class<?> mouseInputClass, int button, int modifiers) {
        try {
            for (java.lang.reflect.Constructor<?> c : mouseInputClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0] == int.class && pt[1] == int.class) {
                    return c.newInstance(button, modifiers);
                }
                if (pt.length == 1 && pt[0] == int.class) {
                    return c.newInstance(button);
                }
            }
            for (java.lang.reflect.Method m : mouseInputClass.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (m.getReturnType() == mouseInputClass && pt.length == 2 && pt[0] == int.class && pt[1] == int.class) {
                        return m.invoke(null, button, modifiers);
                    }
                    if (m.getReturnType() == mouseInputClass && pt.length == 1 && pt[0] == int.class) {
                        return m.invoke(null, button);
                    }
                }
            }
            Object[] enums = mouseInputClass.getEnumConstants();
            if (enums != null && enums.length > 0) {
                for (Object e : enums) {
                    String name = ((Enum<?>) e).name();
                    if (button == 0 && name.contains("LEFT")) return e;
                    if (button == 1 && name.contains("RIGHT")) return e;
                    if (button == 2 && name.contains("MIDDLE")) return e;
                }
                return enums[0];
            }
        } catch (Exception e) {
            ReflectionHelper.dbg("createMouseInput failed: " + e.getMessage());
        }
        return null;
    }

    public static void sendScroll(long handle, double scrollY) {
        if (ReflectionCache.LWJGL3) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = ReflectionCache.getMouseHandler(mc);
                if (mouseHandler != null) {
                    for (java.lang.reflect.Method m : mouseHandler.getClass().getDeclaredMethods()) {
                        String name = m.getName();
                        if ((name.contains("scroll") || name.startsWith("lambda$setup$"))
                            && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                            Class<?>[] pt = m.getParameterTypes();
                            if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class) {
                                m.setAccessible(true);
                                m.invoke(mouseHandler, handle, 0.0, scrollY);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendScroll GLFW: " + e.getMessage());
            }
        }
        throw new IllegalStateException("Internal mouse scrolling is unavailable; use gui_control.");
    }

    public static void sendMouseMoveInternal(long handle, double x, double y) {
        if (ReflectionCache.LWJGL3) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = ReflectionCache.getMouseHandler(mc);
                if (mouseHandler != null) {
                    for (java.lang.reflect.Method m : mouseHandler.getClass().getDeclaredMethods()) {
                        String name = m.getName();
                        if ((name.equals("onMove") || name.contains("cursor") || name.contains("pos") || name.startsWith("lambda$setup$"))
                            && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                            Class<?>[] pt = m.getParameterTypes();
                            if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class) {
                                m.setAccessible(true);
                                m.invoke(mouseHandler, handle, x, y);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendMouseMove GLFW: " + e.getMessage());
            }
        }
        throw new IllegalStateException("Internal mouse movement is unavailable; use gui_control.");
    }

    public static void sendMouseDrag(long handle, int x1, int y1, int x2, int y2, int button, int steps) {
        setCursorPos(handle, x1, y1);
        try { Thread.sleep(20); } catch (Exception ignored) {}
        sendMouseButton(handle, button, 1);
        try { Thread.sleep(20); } catch (Exception ignored) {}
        if (steps < 1) steps = 1;
        for (int i = 1; i <= steps; i++) {
            int cx = x1 + (x2 - x1) * i / steps;
            int cy = y1 + (y2 - y1) * i / steps;
            setCursorPos(handle, cx, cy);
            try { Thread.sleep(10); } catch (Exception ignored) {}
        }
        sendMouseButton(handle, button, 0);
    }

    public static void setCursorPos(long handle, double x, double y) {
        ReflectionCache.initClassCache();
        sendMouseMoveInternal(handle, x, y);
    }


}
