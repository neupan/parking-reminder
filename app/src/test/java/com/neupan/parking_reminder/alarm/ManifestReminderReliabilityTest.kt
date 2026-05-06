package com.neupan.parking_reminder.alarm

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class ManifestReminderReliabilityTest {
    private val manifest = readManifest()

    @Test
    fun `manifest requests wake lock for alarm receiver work`() {
        assertTrue(
            "Alarm delivery must hold a bounded wake lock after BroadcastReceiver.onReceive returns.",
            manifest.usesPermission("android.permission.WAKE_LOCK"),
        )
    }

    @Test
    fun `manifest resyncs alarms when exact alarm permission is granted`() {
        assertTrue(
            "Exact alarm permission grant must resync any saved active parking reminder.",
            manifest.receiverHandlesAction(
                receiverName = ".alarm.ExactAlarmPermissionChangedReceiver",
                actionName = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            ),
        )
    }

    private fun Document.usesPermission(permissionName: String): Boolean {
        val permissions = getElementsByTagName("uses-permission")
        return (0 until permissions.length)
            .map { permissions.item(it) as Element }
            .any { it.androidName == permissionName }
    }

    private fun Document.receiverHandlesAction(
        receiverName: String,
        actionName: String,
    ): Boolean {
        val receivers = getElementsByTagName("receiver")
        return (0 until receivers.length)
            .map { receivers.item(it) as Element }
            .filter { it.androidName == receiverName }
            .flatMap { receiver ->
                val actions = receiver.getElementsByTagName("action")
                (0 until actions.length).map { actions.item(it) as Element }
            }
            .any { it.androidName == actionName }
    }

    private val Element.androidName: String
        get() = getAttributeNS(ANDROID_NS, "name")

    private fun readManifest(): Document {
        val file = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
