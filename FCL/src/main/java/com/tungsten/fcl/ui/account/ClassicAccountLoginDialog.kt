package com.tungsten.fcl.ui.account

import android.content.Context
import android.graphics.Point
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import com.tungsten.fcl.R
import com.tungsten.fcl.databinding.DialogReloginClassicBinding
import com.tungsten.fcl.setting.Accounts
import com.tungsten.fclcore.auth.AuthInfo
import com.tungsten.fclcore.auth.ClassicAccount
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.task.Task
import com.tungsten.fclcore.util.Logging.LOG
import com.tungsten.fclcore.util.StringUtils
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog
import com.tungsten.fcllibrary.component.dialog.FCLDialog
import com.tungsten.fcllibrary.component.view.FCLEditText
import com.tungsten.fcllibrary.component.view.FCLTextView
import com.tungsten.fcllibrary.util.ConvertUtils
import java.util.logging.Level

/**
 * 外置账户（authlib-injector / Yggdrasil）凭据过期时的重新登录对话框（v2 风格）：
 * 标题下展示账号名与密码输入，确定后经 [ClassicAccount.logInWithPassword] 刷新凭据。
 */
class ClassicAccountLoginDialog(
    context: Context,
    private val oldAccount: ClassicAccount,
    private val success: (AuthInfo) -> Unit,
    private val failed: () -> Unit,
) : FCLDialog(context) {

    private val binding = DialogReloginClassicBinding.inflate(layoutInflater)

    private lateinit var passwordInput: FCLEditText
    private lateinit var progressView: View
    private lateinit var progressText: FCLTextView

    init {
        val point = Point()
        window?.windowManager?.defaultDisplay?.getSize(point)
        val params = window?.attributes
        params?.width = ConvertUtils.dip2px(context, 500f)
        val ratio = point.x.toFloat() / point.y.toFloat()
        if (ratio >= 1.5f) {
            params?.height = WindowManager.LayoutParams.MATCH_PARENT
        } else {
            params?.height = point.y * 1 / 2
        }
        window?.attributes = params

        setContentView(binding.root)
        setCancelable(false)
        binding.title.setText(R.string.account_login_refresh)

        buildContent()
        binding.ok.setOnClickListener { login() }
        binding.cancel.setOnClickListener {
            failed()
            dismiss()
        }
    }

    private fun buildContent() {
        val density = context.resources.displayMetrics.density

        // 提示：凭据失效需重新输入密码
        binding.contentContainer.addView(FCLTextView(context).apply {
            setText(R.string.account_login_refresh_classic_hint)
            textSize = 12f
        })

        // 账号名（只读）
        binding.contentContainer.addView(labelView(R.string.account_create_username, density))
        binding.contentContainer.addView(FCLTextView(context).apply {
            text = oldAccount.username
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * density).toInt() }
        })

        // 密码
        binding.contentContainer.addView(labelView(R.string.account_create_password, density))
        passwordInput = FCLEditText(context).apply {
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * density).toInt() }
        }
        binding.contentContainer.addView(passwordInput)

        // 登录进度行（复用 view_login_progress）
        progressView = layoutInflater.inflate(R.layout.view_login_progress, binding.contentContainer, false)
        progressText = progressView.findViewById<FCLTextView>(R.id.progress_text)
        progressView.visibility = View.GONE
        binding.contentContainer.addView(progressView)
    }

    private fun labelView(resId: Int, density: Float): FCLTextView = FCLTextView(context).apply {
        setText(resId)
        textSize = 15f
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (12 * density).toInt() }
    }

    private fun login() {
        val password = passwordInput.text?.toString().orEmpty()
        if (StringUtils.isBlank(password)) {
            Toast.makeText(context, R.string.account_create_alert, Toast.LENGTH_SHORT).show()
            return
        }
        binding.ok.isEnabled = false
        binding.cancel.isEnabled = false
        progressView.visibility = View.VISIBLE
        progressText.setText(R.string.launch_state_logging_in)
        Task.supplyAsync { oldAccount.logInWithPassword(password) }
            .whenComplete(Schedulers.androidUIThread()) { authInfo, exception ->
                progressView.visibility = View.GONE
                binding.ok.isEnabled = true
                binding.cancel.isEnabled = true
                if (exception == null) {
                    success(authInfo)
                    dismiss()
                } else {
                    LOG.log(Level.INFO, "Failed to login when credentials expired: $oldAccount", exception)
                    FCLAlertDialog.Builder(context)
                        .setAlertLevel(FCLAlertDialog.AlertLevel.ALERT)
                        .setMessage(Accounts.localizeErrorMessage(context, exception))
                        .setCancelable(false)
                        .setNegativeButton(context.getString(R.string.dialog_positive), null)
                        .create()
                        .show()
                }
            }.start()
    }
}
