package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class StackListTest {

    // Trimmed real output from a K706 head unit (Android 10).
    private val output = """
        Stack id=136 bounds=[0,0][1920,1200] displayId=0 userId=0
         configuration={1.0 sw600dp w960dp h535dp 320dpi}
          taskId=577: org.opencarproject.launcher/org.opencarproject.launcher.presentation.LauncherActivity bounds=[0,0][1920,1200] userId=0 visible=true
        
        Stack id=135 bounds=[0,0][904,323] displayId=3 userId=0
         configuration={1.0 sw161dp w452dp h161dp 320dpi}
          taskId=576: com.waze/com.waze.FreeMapAppActivity bounds=[0,0][904,323] userId=0 visible=true topActivity=ComponentInfo{com.waze/com.waze.MainActivity}
    """.trimIndent()

    @Test
    fun findsStackAndDisplayOfPackage() {
        assertEquals(listOf(StackRef(135, 3)), StackList.findAll(output, "com.waze"))
        assertEquals(listOf(StackRef(136, 0)), StackList.findAll(output, "org.opencarproject.launcher"))
    }

    @Test
    fun ignoresMissingAndPrefixPackages() {
        assertEquals(emptyList<StackRef>(), StackList.findAll(output, "com.google.android.apps.maps"))
        assertEquals(emptyList<StackRef>(), StackList.findAll(output, "com.wa"))
    }
}
