package com.example.geckobrowser

import android.app.assist.AssistStructure
import android.content.Context
import android.os.Build
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

/**
 * Bridges horiZen's own vault to the Android Autofill framework, which is what
 * GeckoView uses to expose a page's login fields. Once the user selects horiZen
 * as the device's autofill service, saved logins appear in the system's autofill
 * bar over a login form, and a submitted login can be offered for saving.
 */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
class HorizenAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: android.os.CancellationSignal, callback: FillCallback) {
        val context = this
        cancellationSignal.setOnCancelListener { /* no-op */ }
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) { callback.onSuccess(null); return }
            val parsed = parseStructure(structure)
            val usernameId = parsed.usernameId
            val passwordId = parsed.passwordId
            if (usernameId == null && passwordId == null) { callback.onSuccess(null); return }

            val host = parsed.webDomain
            val logins = if (host.isNullOrBlank()) emptyList() else PasswordStore.forHost(context, host)
            val autofillEnabled = PasswordStore.isAutofillEnabled(context)

            if (logins.isEmpty() && !autofillEnabled) { callback.onSuccess(null); return }

            val response = FillResponse.Builder()
            if (autofillEnabled) {
                val presentation = RemoteViews(packageName, R.layout.autofill_login)
                for (login in logins) {
                    val builder = Dataset.Builder(presentation)
                    if (usernameId != null) builder.setValue(usernameId, AutofillValue.forText(login.username))
                    if (passwordId != null) builder.setValue(passwordId, AutofillValue.forText(login.password))
                    response.addDataset(builder.build())
                }
            }
            if (usernameId != null && passwordId != null && PasswordStore.isOfferToSave(context) &&
                !PasswordStore.isNeverSave(context, host.orEmpty())) {
                response.setSaveInfo(
                    SaveInfo.Builder(
                        SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                        arrayOf(usernameId, passwordId),
                    ).build()
                )
            }
            callback.onSuccess(response.build())
        } catch (_: Throwable) {
            callback.onFailure("horiZen could not fill this form")
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val context = this
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure != null) {
                val parsed = parseStructure(structure)
                val username = parsed.usernameValue
                val password = parsed.passwordValue
                val host = parsed.webDomain
                if (!host.isNullOrBlank() && !password.isNullOrBlank() &&
                    PasswordStore.isOfferToSave(context) &&
                    !PasswordStore.isNeverSave(context, host)) {
                    val login = SavedLogin(
                        id = PasswordStore.newId(),
                        host = host,
                        username = username.orEmpty(),
                        password = password,
                        updatedAt = System.currentTimeMillis(),
                    )
                    PasswordStore.upsert(context, login)
                }
            }
            callback.onSuccess()
        } catch (_: Throwable) {
            callback.onFailure("horiZen could not save this login")
        }
    }

    private data class Parsed(
        val webDomain: String?,
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        val usernameValue: String?,
        val passwordValue: String?,
    )

    private fun parseStructure(structure: AssistStructure): Parsed {
        var webDomain: String? = null
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameValue: String? = null
        var passwordValue: String? = null
        val visited = mutableListOf<AssistStructure.ViewNode>()
        val stack = ArrayDeque<AssistStructure.ViewNode>()
        for (i in 0 until structure.windowNodeCount) {
            structure.getWindowNodeAt(i)?.rootViewNode?.let { stack.add(it) }
        }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            visited.add(node)
            if (webDomain.isNullOrBlank()) {
                val d = node.webDomain
                if (!d.isNullOrBlank()) webDomain = d
            }
            val hints = node.autofillHints
            val hintStr = (hints?.joinToString(" ") ?: "") + " " + (node.hint ?: "")
            val isPassword = hints?.any { it.equals("password", ignoreCase = true) } == true ||
                hintStr.contains("password", ignoreCase = true) ||
                (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0
            val isUsername = !isPassword && (
                hints?.any {
                    it.equals("username", ignoreCase = true) ||
                        it.equals("email", ignoreCase = true) ||
                        it.equals(android.view.View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                        it.equals(android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true)
                } == true ||
                    hintStr.contains("user", ignoreCase = true) ||
                    hintStr.contains("email", ignoreCase = true) ||
                    hintStr.contains("login", ignoreCase = true)
            )
            if (isPassword && passwordId == null) {
                passwordId = node.autofillId
                passwordValue = node.autofillValue?.textValue?.toString()
                    ?: node.text?.toString()
            } else if (isUsername && usernameId == null) {
                usernameId = node.autofillId
                usernameValue = node.autofillValue?.textValue?.toString()
                    ?: node.text?.toString()
            }
            for (i in 0 until node.childCount) {
                node.getChildAt(i)?.let { stack.add(it) }
            }
        }
        return Parsed(webDomain, usernameId, passwordId, usernameValue, passwordValue)
    }
}