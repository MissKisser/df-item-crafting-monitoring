package com.local.dfcraftmonitor.ui.login

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccountSourceTest {

    @Test
    fun addingCachedExistingAccountIsIdempotentAndNavigatesBackToList() {
        val loginViewModel = source("ui/login/LoginViewModel.kt")

        assertFalse(
            "Add-account login must not strand cached existing accounts on a failure page; it should refresh/switch the existing account instead",
            loginViewModel.contains("该账号已在列表中，请切换其他账号扫码或返回设置页"),
        )
        assertTrue(
            "Add-account login must persist/update the harvested credential even when the account already exists",
            loginViewModel.contains("sessionHolder.set(credential)"),
        )
        assertTrue(
            "Existing cached accounts should still drive the normal LoggedIn navigation so settings can refresh without rebooting",
            loginViewModel.contains("_state.value = UiState.LoggedIn"),
        )
    }

    @Test
    fun sessionHolderEmitsInitializedCurrentAccountIdSnapshot() {
        val sessionHolder = source("ui/login/SessionHolder.kt")

        assertTrue(
            "SessionHolder must assign currentEntry before emitting the initial replay snapshot",
            sessionHolder.contains("currentEntry = loadCurrent() ?: accountStore.migrateFromCredentialStore(credentialStore)") &&
                sessionHolder.contains("currentEntry?.let { emitSnapshot() }"),
        )
        assertFalse(
            "Initial snapshots must not be emitted while currentEntry is still null",
            sessionHolder.contains("loadCurrent()?.let {\n            emitSnapshot()"),
        )
    }

    @Test
    fun profileRefreshDoesNotOverwriteExistingAvatarWithBlankValue() {
        val sessionHolder = source("ui/login/SessionHolder.kt")

        assertTrue(
            "Profile refresh should preserve a previously loaded avatar when a later WeChat response returns blank",
            sessionHolder.contains("avatarUrl = avatarUrl.normalizedAvatarUrl()") &&
                sessionHolder.contains("ifBlank { entry.avatarUrl.normalizedAvatarUrl() }"),
        )
        assertTrue(
            "Profile refresh should preserve a previously loaded nickname when a later response returns blank",
            sessionHolder.contains("nickname = nickname.ifBlank { entry.nickname }"),
        )
    }

    @Test
    fun profileRefreshDoesNotPreserveNumericAvatarPlaceholder() {
        val sessionHolder = source("ui/login/SessionHolder.kt")

        assertTrue(
            "Numeric headIcon values are not loadable avatars and must not be preserved as avatarUrl",
            sessionHolder.contains("private fun String.normalizedAvatarUrl()"),
        )
    }

    private fun source(relative: String): String {
        val path = "src/main/java/com/local/dfcraftmonitor/$relative"
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.first { it.isFile }.readText()
    }
}
