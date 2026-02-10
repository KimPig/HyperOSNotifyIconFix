package com.kimpig.hyperosnotifyiconfix;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "HyperOSNotifyIconFix";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        final String pkg = lpparam.packageName;
        if (!"com.android.systemui".equals(pkg) && !"com.miui.systemui".equals(pkg)) return;
        try {
            SmallIconHooker.hook(lpparam);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": init failed: " + t);
        }
    }
}