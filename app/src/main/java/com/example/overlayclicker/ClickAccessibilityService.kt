package com.example.overlayclicker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ClickAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: ClickAccessibilityService? = null
            private set
    }
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun clickText(target: String) {
        val root = rootInActiveWindow ?: run { toast("No accessible active window"); return }
        val found = mutableListOf<AccessibilityNodeInfo>()
        find(root, target, found)
        if (found.size != 1) {
            toast(if (found.isEmpty()) "No exact match" else "Multiple matches; use more specific text")
            return
        }
        var n: AccessibilityNodeInfo? = found[0]
        while (n != null) {
            if (n.isClickable && n.isEnabled) {
                toast(if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "Click action sent" else "Click failed")
                return
            }
            n = n.parent
        }
        toast("Matched element has no clickable parent")
    }
    private fun find(node: AccessibilityNodeInfo, target: String, out: MutableList<AccessibilityNodeInfo>) {
        if (!node.isVisibleToUser) return
        if (node.text?.toString()?.trim() == target ||
            node.contentDescription?.toString()?.trim() == target) out.add(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { find(it, target, out) }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
