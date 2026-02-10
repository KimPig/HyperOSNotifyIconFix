package com.kimpig.hyperosnotifyiconfix;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.drawable.Icon;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.ArrayList;
import java.util.List;

public final class SmallIconHooker {

    private static final String FAKE_ICON_TAG = "aosp_icon_clone_tag";

    static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        hookStatusBarIcons(lpparam);
        hookPanelIcons(lpparam);
        hookNotifImageUtil(lpparam);
    }

    private static void hookNotifImageUtil(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> notifImageUtilClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.utils.NotifImageUtil",
                    lpparam.classLoader
            );

            Class<?> expandedNotificationClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.ExpandedNotification",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    notifImageUtilClass,
                    "applyAppIconAllowCustom",
                    Context.class,
                    expandedNotificationClass,
                    ImageView.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Context context = (Context) param.args[0];
                                Object expandedNotification = param.args[1];
                                ImageView imageView = (ImageView) param.args[2];

                                if (imageView == null || expandedNotification == null) return;

                                // ExpandedNotification 자체가 StatusBarNotification을 상속하므로 직접 캐스팅
                                StatusBarNotification sbn = (StatusBarNotification) expandedNotification;

                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

                                applyAospStyleToView(imageView, sbn, true);
                            } catch (Throwable ignored) {}
                        }
                    }
            );
        } catch (Throwable ignored) {}
    }

    private static void hookPanelIcons(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            final Class<?> expandableRowClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.classLoader);

            hookInjectors(lpparam);

            final Class<?> miuiWrapperClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationViewWrapper", lpparam.classLoader);
            if (miuiWrapperClass != null) {
                XposedHelpers.findAndHookConstructor(miuiWrapperClass, Context.class, View.class, expandableRowClass, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) { cloneAndApplyAospStyle(param.thisObject); }
                });
                XposedHelpers.findAndHookMethod(miuiWrapperClass, "onContentUpdated", expandableRowClass, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        updateClonedIcon(param.thisObject);
                    }
                });
            }

            final Class<?> childrenContainerClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.stack.MiuiNotificationChildrenContainer", lpparam.classLoader);
            if (childrenContainerClass != null) {
                XposedHelpers.findAndHookMethod(childrenContainerClass, "updateAppIcon", boolean.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            ImageView summaryIcon = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mAppIcon");
                            StatusBarNotification summarySbn = getSbnFromChildrenContainer(param.thisObject);
                            applyAospStyleToView(summaryIcon, summarySbn, true);

                            @SuppressWarnings("unchecked")
                            List<Object> children = (List<Object>) XposedHelpers.getObjectField(param.thisObject, "mChildren");
                            if (children != null) {
                                for (Object childRow : children) {
                                    StatusBarNotification childSbn = getSbnFromEntry(childRow);
                                    ImageView childIcon = findChildHeaderIcon(childRow);
                                    applyAospStyleToView(childIcon, childSbn, true);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }

            final Class<?> headerWrapperClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper", lpparam.classLoader);
            if (headerWrapperClass != null) {
                XposedHelpers.findAndHookMethod(headerWrapperClass, "onContentUpdated", expandableRowClass, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (miuiWrapperClass != null && miuiWrapperClass.isInstance(param.thisObject)) {
                            cloneAndApplyAospStyle(param.thisObject);
                        } else {
                            try {
                                ImageView iconView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mIcon");
                                StatusBarNotification sbn = getSbnFromWrapper(param.thisObject);

                                if (iconView != null && sbn != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    Icon smallIcon = sbn.getNotification().getSmallIcon();
                                    if (smallIcon != null) {
                                        try {
                                            int tagId = iconView.getContext().getResources().getIdentifier("image_icon_tag", "id", "com.android.systemui");
                                            if (tagId != 0) {
                                                iconView.setTag(tagId, smallIcon);
                                            }
                                        } catch (Throwable ignored) {}
                                        iconView.setImageIcon(smallIcon);
                                    }
                                }

                                applyAospStyleToView(iconView, sbn, false);
                            } catch (Throwable ignored) {}
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void hookInjectors(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> injector = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapperInjector", lpparam.classLoader);
            if (injector != null) {
                Class<?> expandedNotificationClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.ExpandedNotification", lpparam.classLoader);
                XposedHelpers.findAndHookMethod(injector, "setAppIcon", Context.class, ImageView.class, expandedNotificationClass, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam p) { try { ImageView iv = (ImageView) p.args[1]; Object expanded = p.args[2]; StatusBarNotification sbn = (StatusBarNotification) XposedHelpers.callMethod(expanded, "getSbn"); applyAospStyleToView(iv, sbn, false); } catch (Throwable ignored) {} } });
                XposedHelpers.findAndHookMethod(injector, "setAppIcon", ImageView.class, expandedNotificationClass, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam p) { try { ImageView iv = (ImageView) p.args[0]; Object expanded = p.args[1]; StatusBarNotification sbn = (StatusBarNotification) XposedHelpers.callMethod(expanded, "getSbn"); applyAospStyleToView(iv, sbn, false); } catch (Throwable ignored) {} } });
            }
        } catch (Throwable ignored) {}
    }

    private static void cloneAndApplyAospStyle(Object miuiWrapper) { try { ImageView o = (ImageView) XposedHelpers.getObjectField(miuiWrapper, "mAppIcon"); if (o == null) return; ViewGroup p = (ViewGroup) o.getParent(); if (p == null) return; StatusBarNotification s = getSbnFromWrapper(miuiWrapper); o.setAlpha(0f); o.setVisibility(View.INVISIBLE); ImageView f = p.findViewWithTag(FAKE_ICON_TAG); if (f == null) { f = new ImageView(o.getContext()); f.setTag(FAKE_ICON_TAG); f.setLayoutParams(o.getLayoutParams()); p.addView(f); } applyAospStyleToView(f, s, true); } catch (Throwable ignored) {} }

    private static void updateClonedIcon(Object miuiWrapper) {
        try {
            ImageView original = (ImageView) XposedHelpers.getObjectField(miuiWrapper, "mAppIcon");
            if (original == null) return;
            ViewGroup parent = (ViewGroup) original.getParent();
            if (parent == null) return;

            ImageView fakeIcon = parent.findViewWithTag(FAKE_ICON_TAG);
            if (fakeIcon == null) {
                cloneAndApplyAospStyle(miuiWrapper);
                return;
            }

            StatusBarNotification sbn = getSbnFromWrapper(miuiWrapper);
            applyAospStyleToView(fakeIcon, sbn, true);
        } catch (Throwable ignored) {}
    }
    
    private static void applyAospStyleToView(ImageView iconView, StatusBarNotification sbn, boolean isMiuiPanel) {
        if (iconView == null || sbn == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            if (isConversationBadge(iconView)) return;
            Context context = iconView.getContext();
            Icon smallIcon = sbn.getNotification().getSmallIcon();

            int accent = sbn.getNotification().color;
            if (accent == 0) {
                int colorResId = Resources.getSystem().getIdentifier("system_accent1_600", "color", "android");
                if (colorResId != 0) accent = context.getColor(colorResId);
            }

            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isSystemDark = (uiMode == Configuration.UI_MODE_NIGHT_YES);

            int backgroundColor = isSystemDark ? brightenLikeM3(accent) : accent;

            if (smallIcon != null) {
                ShapeDrawable background = new ShapeDrawable(new OvalShape());
                background.getPaint().setColor(backgroundColor);
                iconView.setBackground(background);
                iconView.setImageDrawable(smallIcon.loadDrawable(context));

                int tintColor = isSystemDark ? 0xFF212121 : Color.WHITE;
                iconView.setImageTintList(ColorStateList.valueOf(tintColor));

                int padding = dpToPx(context, isMiuiPanel ? 7 : 4);
                iconView.setPadding(padding, padding, padding, padding);
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconView.setVisibility(View.VISIBLE);
            }
        } catch (Throwable ignored) {}
    }

    private static void hookStatusBarIcons(XC_LoadPackage.LoadPackageParam lpparam) {
        final Class<?> sbivClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.StatusBarIconView", lpparam.classLoader);
        if (sbivClass == null) return;
        try { XposedHelpers.findAndHookMethod(sbivClass, "set", "com.android.internal.statusbar.StatusBarIcon", new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam param) { StatusBarNotification sbn = getSbn(param.thisObject); if (sbn == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return; Icon smallIcon = sbn.getNotification().getSmallIcon(); if (smallIcon != null) { XposedHelpers.setObjectField(param.args[0], "icon", smallIcon); } } }); } catch (Throwable ignored) {}
        try {
            XposedHelpers.findAndHookMethod(sbivClass, "updateIconColor", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object exParams = XposedHelpers.getObjectField(param.thisObject, "mExParams");
                        boolean original = XposedHelpers.getBooleanField(exParams, "expandNotification");
                        param.setObjectExtra("origExpandNotif", original);
                        if (original) {
                            StatusBarNotification sbn = getSbn(param.thisObject);
                            if (sbn != null && isGrayscale((ImageView) param.thisObject, sbn)) {
                                XposedHelpers.setBooleanField(exParams, "expandNotification", false);
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object exParams = XposedHelpers.getObjectField(param.thisObject, "mExParams");
                        Boolean original = (Boolean) param.getObjectExtra("origExpandNotif");
                        if (original != null) {
                            XposedHelpers.setBooleanField(exParams, "expandNotification", original);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    private static int brightenLikeM3(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, hsv[1] - 0.3f);
        hsv[2] = Math.min(1f, hsv[2] + 0.3f);
        return Color.HSVToColor(hsv);
    }

    private static int dpToPx(Context c, int dp) { return (int) (dp * c.getResources().getDisplayMetrics().density); }
    private static StatusBarNotification getSbn(Object view) { try { Object sbn = XposedHelpers.getObjectField(view, "mNotification"); return (sbn instanceof StatusBarNotification) ? (StatusBarNotification) sbn : null; } catch (Throwable ignored) { return null; } }
    private static StatusBarNotification getSbnFromWrapper(Object wrapper) { try { Object r = XposedHelpers.getObjectField(wrapper, "mRow"); return getSbnFromEntry(r); } catch (Throwable ignored) { return null; } }
    private static StatusBarNotification getSbnFromChildrenContainer(Object container) { try { Object c = XposedHelpers.getObjectField(container, "mContainingNotification"); return getSbnFromEntry(c); } catch (Throwable ignored) { return null; } }
    private static StatusBarNotification getSbnFromEntry(Object rowOrEntry) { try { Object entry = rowOrEntry; if (XposedHelpers.findFieldIfExists(rowOrEntry.getClass(), "mEntry") != null) { entry = XposedHelpers.getObjectField(rowOrEntry, "mEntry"); } return (StatusBarNotification) XposedHelpers.getObjectField(entry, "mSbn"); } catch (Throwable ignored) { return null; } }
    private static ImageView findChildHeaderIcon(Object childRow) { try { Object wrapper = XposedHelpers.getObjectField(childRow, "mPrivateLayout"); if (wrapper != null) { View contracted = (View) XposedHelpers.callMethod(wrapper, "getContractedChild"); ImageView v = tryFindAppIconInView(contracted); if (v != null) return v; View expanded = (View) XposedHelpers.callMethod(wrapper, "getExpandedChild"); v = tryFindAppIconInView(expanded); if (v != null) return v; } Object f = XposedHelpers.getObjectField(childRow, "mAppIcon"); if (f instanceof ImageView) return (ImageView) f; } catch (Throwable ignored) {} return null; }
    private static ImageView tryFindAppIconInView(View root) { if (root == null) return null; try { int[] candidates = new int[] { root.getResources().getIdentifier("app_icon", "id", "com.android.systemui"), root.getResources().getIdentifier("notification_header_icon", "id", "com.android.systemui"), root.getResources().getIdentifier("icon", "id", "android") }; for (int id : candidates) { if (id != 0) { View v = root.findViewById(id); if (v instanceof ImageView) return (ImageView) v; } } } catch (Throwable ignored) {} return null; }
    private static boolean isGrayscale(ImageView i, StatusBarNotification s) { if (s == null || i.getDrawable() == null) return true; try { Class<?> c = XposedHelpers.findClass("com.android.internal.util.ContrastColorUtil", null); Object inst = XposedHelpers.callStaticMethod(c, "getInstance", i.getContext()); return (boolean) XposedHelpers.callMethod(inst, "isGrayscaleIcon", i.getDrawable()); } catch (Throwable ignored) { return true; } }
	private static boolean isConversationBadge(ImageView iconView) {
        try {
            View parent = iconView;
            for (int i = 0; i < 5; i++) {
                if (parent.getParent() instanceof View) {
                    parent = (View) parent.getParent();
                    if (parent.getClass().getName().contains("ConversationLayout")) {
                        int size = Math.max(iconView.getWidth(), iconView.getHeight());
                        if (size > 0 && size < dpToPx(iconView.getContext(), 32)) {
                            return true;
                        }
                        if (size == 0) {
                            ViewGroup.LayoutParams lp = iconView.getLayoutParams();
                            if (lp != null && lp.width > 0 && lp.width < dpToPx(iconView.getContext(), 32)) {
                                return true;
                            }
                        }
                        return false;
                    }
                } else {
                    break;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}