package com.example

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

object SmtpHelper {

    private const val PREFS_NAME = "huolileme_settings"
    private const val KEY_TARGET_EMAIL = "target_email"
    private const val KEY_SMTP_HOST = "smtp_host"
    private const val KEY_SMTP_PORT = "smtp_port"
    private const val KEY_SMTP_USER = "smtp_user"
    private const val KEY_SMTP_PASS = "smtp_pass"
    private const val KEY_SMTP_SENDER = "smtp_sender"
    private const val KEY_USE_REAL_SMTP = "use_real_smtp"

    // Retrieve the target email
    fun getSavedEmail(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TARGET_EMAIL, "") ?: ""
    }

    // Save the target email
    fun saveEmail(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TARGET_EMAIL, email.trim()).apply()
    }

    // Check if the email config setup has been completed (has non-empty target email)
    fun isEmailConfigured(context: Context): Boolean {
        return getSavedEmail(context).isNotEmpty()
    }

    // SMTP details getters and setters
    fun getSmtpHost(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SMTP_HOST, "smtp.qq.com") ?: "smtp.qq.com"
    }

    fun saveSmtpHost(context: Context, host: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SMTP_HOST, host.trim()).apply()
    }

    fun getSmtpPort(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SMTP_PORT, 465)
    }

    fun saveSmtpPort(context: Context, port: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SMTP_PORT, port).apply()
    }

    fun getSmtpUser(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SMTP_USER, "") ?: ""
    }

    fun saveSmtpUser(context: Context, user: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SMTP_USER, user.trim()).apply()
    }

    fun getSmtpPass(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SMTP_PASS, "") ?: ""
    }

    fun saveSmtpPass(context: Context, pass: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SMTP_PASS, pass.trim()).apply()
    }

    fun getSmtpSender(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SMTP_SENDER, "") ?: ""
    }

    fun saveSmtpSender(context: Context, sender: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SMTP_SENDER, sender.trim()).apply()
    }

    fun getUseRealSmtp(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_REAL_SMTP, false)
    }

    fun saveUseRealSmtp(context: Context, useReal: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_REAL_SMTP, useReal).apply()
    }

    /**
     * Sends an email via standard SSL SMTP.
     * Returns a Result containing detailed response/logs for troubleshooting.
     */
    suspend fun sendEmail(
        context: Context,
        subject: String,
        htmlBody: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetEmail = getSavedEmail(context)
        if (targetEmail.isEmpty()) {
            return@withContext Result.failure(Exception("Target email is not configured"))
        }

        val useRealSmtp = getUseRealSmtp(context)
        val host = getSmtpHost(context)
        val port = getSmtpPort(context)
        val username = getSmtpUser(context)
        val password = getSmtpPass(context)
        var sender = getSmtpSender(context)
        if (sender.isEmpty()) {
            sender = username
        }

        // Check if real SMTP sending is disabled/unconfigured, fall back to safe simulation log!
        if (!useRealSmtp || host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            val logMessage = """
                [SMTP 模拟器信道激活]
                目标邮箱: $targetEmail
                发信服务器 (模拟): $host:$port
                邮件主题: $subject
                
                --- 邮件正文 (HTML) ---
                $htmlBody
                -----------------------
                
                提示: 由于尚未在【守护参数设置】中启用“真实 SMTP 发信”或未填写发信凭据，系统已启用沙盒模拟模式安全归档此邮件任务。
            """.trimIndent()
            Log.d("SmtpHelper", logMessage)
            return@withContext Result.success("MOCK_SUCCESS: 模拟邮件已生成 (收件箱: $targetEmail)。如果您想获得真实邮件推送，请在‘设置’中配置并开启真实发信通道。")
        }

        val logs = StringBuilder()
        var socket: Socket? = null
        try {
            logs.append("正在建立 SSL 连接到 $host:$port...\n")
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket(host, port)
            socket.soTimeout = 10000 // 10s timeout
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = socket.getOutputStream()

            fun readResponse(): String {
                val line = reader.readLine() ?: ""
                logs.append("S: $line\n")
                Log.d("SmtpHelper", "Rx: $line")
                return line
            }

            fun sendCommand(cmd: String) {
                Log.d("SmtpHelper", "Tx: ${cmd.trim()}")
                logs.append("C: ${cmd.trim()}\n")
                writer.write(cmd.toByteArray(Charsets.UTF_8))
                writer.flush()
            }

            var response = readResponse() // Greeting
            if (!response.startsWith("220")) {
                throw Exception("Unexpected greeting: $response")
            }

            sendCommand("EHLO localhost\r\n")
            response = readResponse()
            while (response.startsWith("250-")) {
                response = readResponse()
            }

            sendCommand("AUTH LOGIN\r\n")
            response = readResponse()
            if (!response.startsWith("334")) {
                throw Exception("AUTH LOGIN rejected: $response")
            }

            val userB64 = Base64.encodeToString(username.toByteArray(), Base64.NO_WRAP)
            sendCommand("$userB64\r\n")
            response = readResponse()
            if (!response.startsWith("334")) {
                throw Exception("Username rejected: $response")
            }

            val passB64 = Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP)
            sendCommand("$passB64\r\n")
            response = readResponse()
            if (!response.startsWith("235")) {
                throw Exception("Password rejected: $response")
            }

            sendCommand("MAIL FROM:<$sender>\r\n")
            response = readResponse()
            if (!response.startsWith("250")) {
                throw Exception("MAIL FROM rejected: $response")
            }

            sendCommand("RCPT TO:<$targetEmail>\r\n")
            response = readResponse()
            if (!response.startsWith("250")) {
                throw Exception("RCPT TO rejected: $response")
            }

            sendCommand("DATA\r\n")
            response = readResponse()
            if (!response.startsWith("354")) {
                throw Exception("DATA rejected: $response")
            }

            val subjectB64 = Base64.encodeToString(subject.toByteArray(), Base64.NO_WRAP)
            val emailMessage = StringBuilder().apply {
                append("From: $sender\r\n")
                append("To: $targetEmail\r\n")
                append("Subject: =?UTF-8?B?$subjectB64?=\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("\r\n")
                append(htmlBody)
                append("\r\n.\r\n")
            }.toString()

            writer.write(emailMessage.toByteArray(Charsets.UTF_8))
            writer.flush()
            logs.append("C: [HTML 邮件数据段已发送]\n")
            
            response = readResponse()
            if (!response.startsWith("250")) {
                throw Exception("Message data rejected: $response")
            }

            sendCommand("QUIT\r\n")
            readResponse()

            logs.append("封包发送完毕，邮件推送成功！")
            Result.success("REAL_SUCCESS: " + logs.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            logs.append("发送失败: ${e.message}\n")
            Result.failure(Exception(logs.toString(), e))
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
